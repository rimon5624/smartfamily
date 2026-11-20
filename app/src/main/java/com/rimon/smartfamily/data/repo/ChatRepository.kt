package com.rimon.smartfamily.data.repo

import com.rimon.smartfamily.data.db.AppDatabase
import com.rimon.smartfamily.data.db.ChatEntity
import com.rimon.smartfamily.data.db.ChatKeyEntity
import com.rimon.smartfamily.data.db.ChatMessageEntity
import com.rimon.smartfamily.data.db.ChatReactionEntity
import com.rimon.smartfamily.data.db.ChatReadEntity
import com.rimon.smartfamily.data.db.UserKeyEntity
import com.rimon.smartfamily.security.ChatCrypto
import com.rimon.smartfamily.sync.SupabaseApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** What is inside an encrypted message. */
@Serializable
data class MessagePayload(
    val text: String = "",
    val mediaKey: String = "",
    val mediaMime: String = "",
    val mediaName: String = "",
    val durationSec: Int = 0,
    val transcript: String = "",
    val deleted: Boolean = false,
    // 2.0: in-app item cards, locations, forwarded messages
    val cardType: String = "",
    val cardId: String = "",
    val cardTitle: String = "",
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val forwarded: Boolean = false,
)

/** A message after decryption; [payload] is null when this phone has no key for it yet. */
data class ShownMessage(val entity: ChatMessageEntity, val payload: MessagePayload?)

class ChatRepository(
    private val db: AppDatabase,
    private val crypto: ChatCrypto,
    private val api: SupabaseApi,
    private val onLocalChange: () -> Unit,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val keyCache = ConcurrentHashMap<String, ByteArray>() // chatId|epoch -> key
    private fun now() = System.currentTimeMillis()
    private fun uuid() = UUID.randomUUID().toString()
    private fun ctx(chatId: String, epoch: Int) = "smartfamily-chat|$chatId|$epoch"
    private fun aad(m: ChatMessageEntity) = "${m.chatId}|${m.epoch}|${m.senderId}|${m.id}"

    fun observeChats(userId: String) = db.chats().observeMine(userId)
    fun observeChat(id: String) = db.chats().observeOne(id)
    fun observeMessages(chatId: String) = db.chatMessages().observeChat(chatId)
    fun observeLast(chatId: String) = db.chatMessages().observeLast(chatId)
    fun observeReactions(chatId: String) = db.chatReactions().observeChat(chatId)
    fun observeReads(chatId: String) = db.chatReads().observeChat(chatId)
    fun observeMyReads(familyId: String, userId: String) = db.chatReads().observeMine(familyId, userId)
    fun observeKeys() = db.userKeys().observeAll()
    fun observePeople() = db.people().observeAllActive()
    val myDeviceId get() = crypto.deviceId
    fun myFingerprint() = crypto.fingerprint(crypto.publicKeyset())

    /** Publishes this device's public key in the family, once. */
    suspend fun ensureMyKey(familyId: String, userId: String) {
        val id = "${crypto.deviceId}|$familyId"
        val pub = crypto.publicKeyset()
        val existing = db.userKeys().get(id)
        if (existing == null || existing.publicKeyset != pub || existing.userId != userId) {
            db.userKeys().upsert(
                UserKeyEntity(id = id, familyId = familyId, userId = userId, deviceId = crypto.deviceId, publicKeyset = pub, fingerprint = crypto.fingerprint(pub))
            )
            onLocalChange()
        }
    }

    /**
     * The whole-family group chat, created on first use (CHT-1). A phone that has not pulled the chats yet
     * ([chatsPulled] false) never creates one, so a newly joined member does not start a second family chat.
     */
    suspend fun ensureFamilyChat(familyId: String, userId: String, memberIds: List<String>, title: String, chatsPulled: Boolean = true): ChatEntity? {
        db.chats().familyChat(familyId)?.let { return it }
        if (!chatsPulled) return null
        return createChat(familyId, userId, "FAMILY", title, memberIds)
    }

    /**
     * 2.1.2, run after every sync. Fixes what a member who joins later used to miss:
     * 1. my device key is published in every family I am an active member of (it is refused before approval);
     * 2. the family chat and the albums always contain every active member: joiners are added, and
     *    leavers are removed with a new key (only an owner/admin removes, so two phones never rotate at once);
     * 3. any chat key I hold is sealed to every member device that lacks it — no need to open the chat first.
     */
    suspend fun maintain(me: String) {
        if (me.isBlank()) return
        val allMembers = db.members().all().filter { it.deletedAt == null && it.status == "ACTIVE" }
        val myFamilies = allMembers.filter { it.userId == me }
        myFamilies.forEach { mine ->
            runCatching {
                ensureMyKey(mine.familyId, me)
                // a key sent before my membership was approved was refused by the server: send it again
                val k = db.userKeys().get("${crypto.deviceId}|${mine.familyId}")
                if (k != null && k.updatedAt < mine.updatedAt) { db.userKeys().upsert(k.copy(updatedAt = now(), dirty = true)); onLocalChange() }
            }
        }
        var changed = false
        for (chat in db.chats().allActive()) {
            val mine = myFamilies.firstOrNull { it.familyId == chat.familyId }
            val current = chat.members.split(',').filter { it.isNotBlank() }
            var c = chat
            if (mine != null && (chat.kind == "FAMILY" || chat.kind == "ALBUM")) {
                val everyone = allMembers.filter { it.familyId == chat.familyId }.map { it.userId }.distinct()
                val joiners = everyone - current.toSet()
                val leavers = current - everyone.toSet()
                val canRemove = mine.role == "OWNER" || mine.role == "ADMIN"
                if ((joiners.isNotEmpty() || (leavers.isNotEmpty() && canRemove)) && keyFor(chat.id, chat.epoch) != null) {
                    val target = if (canRemove) everyone else (current + joiners).distinct()
                    updateMembers(chat, target)
                    c = db.chats().get(chat.id) ?: chat
                    changed = true
                }
            }
            if (me !in c.members.split(',')) continue
            val key = keyFor(c.id, c.epoch) ?: continue
            val before = db.chatKeys().forEpoch(c.id, c.epoch).size
            sealFor(c, c.epoch, key, c.members.split(',').filter { it.isNotBlank() })
            if (db.chatKeys().forEpoch(c.id, c.epoch).size != before) changed = true
        }
        if (changed) onLocalChange()
    }

    suspend fun createChat(familyId: String, userId: String, kind: String, title: String, memberIds: List<String>): ChatEntity {
        val members = (memberIds + userId).distinct()
        val chat = ChatEntity(id = uuid(), familyId = familyId, kind = kind, title = title, members = members.joinToString(","), epoch = 1, createdBy = userId, lastMessageAt = now())
        db.chats().upsert(chat)
        val key = crypto.newChatKey()
        keyCache["${chat.id}|1"] = key
        sealFor(chat, 1, key, members)
        onLocalChange()
        return chat
    }

    /** Seals a chat key to every known device of [members] that does not have it yet. */
    private suspend fun sealFor(chat: ChatEntity, epoch: Int, key: ByteArray, members: List<String>) {
        val have = db.chatKeys().forEpoch(chat.id, epoch).map { it.deviceId }.toSet()
        db.userKeys().forUsersAny(members).distinctBy { it.deviceId }.filter { it.deviceId !in have }.forEach { k ->
            db.chatKeys().upsert(
                ChatKeyEntity(
                    id = "${chat.id}|${k.deviceId}|$epoch", familyId = chat.familyId, chatId = chat.id, userId = k.userId,
                    deviceId = k.deviceId, epoch = epoch, wrapped = crypto.seal(key, k.publicKeyset, ctx(chat.id, epoch)),
                )
            )
        }
    }

    /** Key for [epoch] on this device, or null when no member has sealed it to us yet. */
    private suspend fun keyFor(chatId: String, epoch: Int): ByteArray? {
        keyCache["$chatId|$epoch"]?.let { return it }
        val sealed = db.chatKeys().forDevice(chatId, crypto.deviceId).firstOrNull { it.epoch == epoch } ?: return null
        return crypto.open(sealed.wrapped, ctx(chatId, epoch))?.also { keyCache["$chatId|$epoch"] = it }
    }

    /**
     * Called when a chat is opened: if we hold the current key, we seal it to members' devices
     * that joined after it was created (new phone, new member). Keeps the chat readable for all.
     */
    suspend fun shareKeys(chat: ChatEntity) {
        val key = keyFor(chat.id, chat.epoch) ?: return
        sealFor(chat, chat.epoch, key, chat.members.split(',').filter { it.isNotBlank() })
        onLocalChange()
    }

    fun canRead(chat: ChatEntity): Boolean = keyCache.containsKey("${chat.id}|${chat.epoch}")

    suspend fun hasKey(chat: ChatEntity): Boolean = keyFor(chat.id, chat.epoch) != null

    suspend fun decrypt(m: ChatMessageEntity): MessagePayload? {
        if (m.ciphertext.isEmpty()) return MessagePayload(deleted = true)
        val key = keyFor(m.chatId, m.epoch) ?: return null
        val plain = crypto.decrypt(key, m.nonce, m.ciphertext, aad(m)) ?: return null
        return runCatching { json.decodeFromString(MessagePayload.serializer(), plain.toString(Charsets.UTF_8)) }.getOrNull()
    }

    suspend fun send(chat: ChatEntity, senderId: String, payload: MessagePayload, kind: String, replyTo: String = "", mediaPath: String = "") {
        val key = keyFor(chat.id, chat.epoch) ?: throw IllegalStateException("No key yet for this chat")
        val base = ChatMessageEntity(
            id = uuid(), familyId = chat.familyId, chatId = chat.id, senderId = senderId, epoch = chat.epoch,
            nonce = "", ciphertext = "", kind = kind, sentAt = now(), replyTo = replyTo, mediaPath = mediaPath,
        )
        val (nonce, ct) = crypto.encrypt(key, json.encodeToString(MessagePayload.serializer(), payload).toByteArray(), aad(base))
        db.chatMessages().upsert(base.copy(nonce = nonce, ciphertext = ct))
        db.chats().upsert(chat.copy(lastMessageAt = now(), updatedAt = now(), dirty = true))
        markRead(chat, senderId)
        onLocalChange()
    }

    /** CHT-3: edit (sender only, enforced by row-level security too). */
    suspend fun edit(m: ChatMessageEntity, payload: MessagePayload) {
        val key = keyFor(m.chatId, m.epoch) ?: return
        val (nonce, ct) = crypto.encrypt(key, json.encodeToString(MessagePayload.serializer(), payload).toByteArray(), aad(m))
        db.chatMessages().upsert(m.copy(nonce = nonce, ciphertext = ct, editedAt = now(), updatedAt = now(), dirty = true))
        onLocalChange()
    }

    /** Delete for everyone: the ciphertext itself is wiped, not just hidden. */
    suspend fun deleteForEveryone(m: ChatMessageEntity) {
        db.chatMessages().upsert(m.copy(nonce = "", ciphertext = "", mediaPath = "", editedAt = now(), updatedAt = now(), dirty = true))
        onLocalChange()
    }

    suspend fun toggleReaction(m: ChatMessageEntity, userId: String, emoji: String) {
        val id = "${m.id}|$userId"
        val existing = db.chatReactions().get(id)
        val removing = existing != null && existing.deletedAt == null && existing.emoji == emoji
        db.chatReactions().upsert(
            ChatReactionEntity(id = id, familyId = m.familyId, chatId = m.chatId, messageId = m.id, userId = userId, emoji = emoji,
                deletedAt = if (removing) now() else null)
        )
        onLocalChange()
    }

    suspend fun markRead(chat: ChatEntity, userId: String) {
        val id = "${chat.id}|$userId"
        val existing = db.chatReads().get(id)
        if (existing != null && existing.lastReadAt >= chat.lastMessageAt && existing.lastReadAt > now() - 5_000) return
        db.chatReads().upsert(ChatReadEntity(id = id, familyId = chat.familyId, chatId = chat.id, userId = userId, lastReadAt = now()))
        onLocalChange()
    }

    /** CHT-13: when someone leaves a chat, a new key is made and sealed only to the remaining members. */
    suspend fun updateMembers(chat: ChatEntity, members: List<String>) {
        val removed = chat.members.split(',').filter { it.isNotBlank() } - members.toSet()
        val epoch = if (removed.isNotEmpty()) chat.epoch + 1 else chat.epoch
        val updated = chat.copy(members = members.distinct().joinToString(","), epoch = epoch, updatedAt = now(), dirty = true)
        db.chats().upsert(updated)
        val key = if (epoch != chat.epoch) crypto.newChatKey().also { keyCache["${chat.id}|$epoch"] = it } else keyFor(chat.id, epoch)
        if (key != null) sealFor(updated, epoch, key, members)
        onLocalChange()
    }

    suspend fun setDisappearing(chat: ChatEntity, days: Int) {
        db.chats().upsert(chat.copy(disappearDays = days, updatedAt = now(), dirty = true))
        onLocalChange()
    }

    // ---------- media ----------

    /** Encrypts on the phone and uploads to the private bucket; returns (path, key). */
    suspend fun uploadMedia(familyId: String, bytes: ByteArray): Pair<String, String> {
        val (key, blob) = crypto.encryptFile(bytes)
        val path = "$familyId/${uuid()}"
        api.uploadObject(BUCKET, path, blob)
        return path to crypto.b64(key)
    }

    suspend fun downloadMedia(path: String, keyB64: String): ByteArray? =
        crypto.decryptFile(crypto.unb64(keyB64), api.downloadObject(BUCKET, path))

    /**
     * KNW-2: sensitive notes (door codes, Wi-Fi) are encrypted end-to-end with the family chat's
     * key, so every family phone can read them and the server only sees ciphertext.
     */
    suspend fun encryptForFamily(familyId: String, plain: String): String? {
        val chat = db.chats().familyChat(familyId) ?: return null
        val key = keyFor(chat.id, chat.epoch) ?: return null
        val (n, ct) = crypto.encrypt(key, plain.toByteArray(), "note|${chat.id}|${chat.epoch}")
        return "enc1:${chat.id}:${chat.epoch}:$n:$ct"
    }

    suspend fun decryptNote(s: String): String? {
        if (!s.startsWith("enc1:")) return s
        val p = s.split(':')
        if (p.size < 5) return null
        val chatId = p[1]; val epoch = p[2].toIntOrNull() ?: return null
        val key = keyFor(chatId, epoch) ?: return null
        return crypto.decrypt(key, p[3], p.subList(4, p.size).joinToString(":"), "note|$chatId|$epoch")?.toString(Charsets.UTF_8)
    }

    suspend fun incomingSince(since: Long, me: String) = db.chatMessages().incomingSince(since, me)
    suspend fun chat(id: String) = db.chats().get(id)

    companion object { const val BUCKET = "chat-media" }
}
