package com.rimon.smartfamily.ui

import android.app.Application
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rimon.smartfamily.R
import com.rimon.smartfamily.SmartFamilyApp
import com.rimon.smartfamily.data.db.ChatEntity
import com.rimon.smartfamily.data.db.ChatMessageEntity
import com.rimon.smartfamily.data.repo.MessagePayload
import com.rimon.smartfamily.data.repo.ShownMessage
import com.rimon.smartfamily.smart.AiNotConfiguredException
import com.rimon.smartfamily.smart.ChatNotifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

class ChatViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as SmartFamilyApp).container
    private val repo = c.chat
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _recording = MutableStateFlow(false)
    val recording = _recording.asStateFlow()

    /** Transcripts made on this phone for others' voice messages (CHT-8; the sender's own go into the message). */
    private val transcriptPrefs = app.getSharedPreferences("sf_transcripts", android.content.Context.MODE_PRIVATE)
    private val _transcripts = MutableStateFlow<Map<String, String>>(emptyMap())
    val transcripts = _transcripts.asStateFlow()

    private fun say(id: Int, arg: String? = null) { _messages.tryEmit(UiMessage.Res(id, arg)) }
    private fun launchSafe(block: suspend () -> Unit) = viewModelScope.launch {
        _busy.value = true
        try { block() }
        catch (e: AiNotConfiguredException) { say(R.string.ai_needed) }
        catch (e: Exception) { _messages.tryEmit(UiMessage.Text(e.message ?: e.javaClass.simpleName)) }
        finally { _busy.value = false }
    }
    private suspend fun uid() = c.settings.current().userId

    val signedIn get() = c.api.isSignedIn
    fun chats(userId: String) = repo.observeChats(userId)
    fun chat(id: String) = repo.observeChat(id)
    fun reactions(chatId: String) = repo.observeReactions(chatId)
    fun reads(chatId: String) = repo.observeReads(chatId)
    fun myReads(familyId: String, userId: String) = repo.observeMyReads(familyId, userId)
    fun members(familyId: String) = c.families.observeMembers(familyId)
    /** Everyone I can chat with: my families and linked families (0.9), one entry per person. */
    val people = repo.observePeople().map { list -> list.distinctBy { it.userId } }
    fun keys() = repo.observeKeys()
    fun myFingerprint() = repo.myFingerprint()
    val myDevice get() = repo.myDeviceId

    /** Messages decrypted on the phone, re-decrypted whenever the table changes. */
    fun shown(chatId: String): Flow<List<ShownMessage>> = repo.observeMessages(chatId).map { list ->
        list.map { ShownMessage(it, repo.decrypt(it)) }
    }

    fun lastPreview(chatId: String): Flow<String> = repo.observeLast(chatId).map { m ->
        if (m == null) "" else {
            val p = repo.decrypt(m)
            when {
                p == null -> getApplication<Application>().getString(R.string.chat_encrypted)
                p.deleted -> getApplication<Application>().getString(R.string.chat_deleted)
                m.kind == "voice" -> "🎤 " + getApplication<Application>().getString(R.string.chat_voice)
                m.kind == "image" -> "📷 " + getApplication<Application>().getString(R.string.chat_photo)
                m.kind == "file" -> "📎 " + p.mediaName
                else -> p.text
            }
        }
    }

    /** Publishes this phone's key and makes sure the family chat exists. */
    fun prepare(familyId: String, memberIds: List<String>) = launchSafe {
        val me = uid()
        repo.ensureMyKey(familyId, me)
        repo.ensureFamilyChat(familyId, me, memberIds, getApplication<Application>().getString(R.string.chat_family),
            chatsPulled = c.settings.current().let { st -> !st.supabaseConfigured || st.lastSyncAt > 0 })
    }

    /** While the chat list is open: new chats and messages show within seconds, and keys are shared. */
    suspend fun liveList() {
        while (true) {
            c.sync.syncTables(setOf("family_members", "user_keys", "chats", "chat_keys", "chat_messages", "chat_reads"))
            runCatching { repo.maintain(uid()) }
            delay(6_000)
        }
    }

    /** While a chat is open: fast sync of the chat tables every 4 seconds, keys shared, read marker set. */
    suspend fun live(chat: ChatEntity) {
        ChatNotifier.openChatId = chat.id
        try {
            while (true) {
                c.sync.syncTables(setOf("user_keys", "chats", "chat_keys", "chat_messages", "chat_reactions", "chat_reads"))
                runCatching { repo.shareKeys(repo.chat(chat.id) ?: chat) }
                repo.markRead(repo.chat(chat.id) ?: chat, uid())
                delay(4_000)
            }
        } finally {
            ChatNotifier.openChatId = null
        }
    }

    fun createChat(familyId: String, title: String, members: List<String>, direct: Boolean) = launchSafe {
        repo.createChat(familyId, uid(), if (direct) "DIRECT" else "GROUP", title, members)
    }

    /** A shared photo album is an encrypted chat of photos (2.0). */
    fun createAlbum(familyId: String, title: String, members: List<String>) = launchSafe {
        repo.ensureMyKey(familyId, uid())
        repo.createChat(familyId, uid(), "ALBUM", title, members)
    }

    fun updateMembers(chat: ChatEntity, members: List<String>) = launchSafe { repo.updateMembers(chat, members) }
    fun setDisappearing(chat: ChatEntity, days: Int) = launchSafe { repo.setDisappearing(chat, days) }

    fun sendText(chat: ChatEntity, text: String, replyTo: String) = launchSafe {
        if (!repo.hasKey(chat)) return@launchSafe say(R.string.chat_waiting_key)
        repo.send(chat, uid(), MessagePayload(text = text.trim()), "text", replyTo)
    }

    fun sendMedia(chat: ChatEntity, uri: Uri, kind: String) = launchSafe {
        if (!repo.hasKey(chat)) return@launchSafe say(R.string.chat_waiting_key)
        val cr = getApplication<Application>().contentResolver
        val (bytes, name, mime) = withContext(Dispatchers.IO) {
            val n = cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null } ?: "file"
            Triple(cr.openInputStream(uri)!!.use { it.readBytes() }, n, cr.getType(uri) ?: "application/octet-stream")
        }
        if (bytes.size > 20 * 1024 * 1024) return@launchSafe say(R.string.chat_too_big)
        val (path, key) = repo.uploadMedia(chat.familyId, bytes)
        repo.send(chat, uid(), MessagePayload(mediaKey = key, mediaMime = mime, mediaName = name), kind, mediaPath = path)
    }

    // ---------- voice ----------

    private var recorder: MediaRecorder? = null
    private var recordFile: File? = null
    private var recordStart = 0L

    fun startRecording() {
        val f = File(getApplication<Application>().cacheDir, "voice_${System.currentTimeMillis()}.m4a")
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(getApplication()) else @Suppress("DEPRECATION") MediaRecorder()
        runCatching {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioSamplingRate(44_100)
            r.setAudioEncodingBitRate(64_000)
            r.setOutputFile(f.absolutePath)
            r.prepare()
            r.start()
            recorder = r; recordFile = f; recordStart = System.currentTimeMillis(); _recording.value = true
        }.onFailure { r.release(); _messages.tryEmit(UiMessage.Text(it.message ?: "Recorder")) }
    }

    fun stopRecording(chat: ChatEntity, send: Boolean) {
        val r = recorder ?: return
        val f = recordFile
        runCatching { r.stop() }
        r.release()
        recorder = null; _recording.value = false
        val seconds = ((System.currentTimeMillis() - recordStart) / 1000).toInt()
        if (!send || f == null || seconds < 1) { f?.delete(); return }
        launchSafe {
            if (!repo.hasKey(chat)) return@launchSafe say(R.string.chat_waiting_key)
            val bytes = withContext(Dispatchers.IO) { f.readBytes() }
            val (path, key) = repo.uploadMedia(chat.familyId, bytes)
            repo.send(chat, uid(), MessagePayload(mediaKey = key, mediaMime = "audio/mp4", mediaName = f.name, durationSec = seconds), "voice", mediaPath = path)
            f.delete()
        }
    }

    /** Downloads and decrypts a media file into the cache; returns its local file. */
    suspend fun media(m: ShownMessage): File? {
        val p = m.payload ?: return null
        if (m.entity.mediaPath.isEmpty()) return null
        val dir = File(getApplication<Application>().cacheDir, "chat").apply { mkdirs() }
        val f = File(dir, m.entity.id + "_" + p.mediaName.ifBlank { "media" }.replace(Regex("[^\\w.\\-]"), "_"))
        if (f.exists()) return f
        return runCatching {
            val bytes = repo.downloadMedia(m.entity.mediaPath, p.mediaKey) ?: return null
            withContext(Dispatchers.IO) { f.writeBytes(bytes) }
            f
        }.getOrNull()
    }

    /** CHT-6/7: cloud transcription with Gemini (free tier); audio leaves the phone only for this. */
    fun transcribe(m: ShownMessage) = launchSafe {
        val f = media(m) ?: return@launchSafe
        val lang = getApplication<Application>().resources.configuration.locales[0].language.let { if (it == "iw" || it == "he") "Hebrew" else "the spoken language" }
        val text = c.gemini.ask(
            "Transcribe this voice message word for word in $lang. Answer only JSON: {\"text\":string}",
            listOf("audio/mp4" to withContext(Dispatchers.IO) { f.readBytes() }),
        )
        val t = runCatching { (json.parseToJsonElement(text) as JsonObject)["text"]?.jsonPrimitive?.contentOrNull }.getOrNull() ?: text
        if (m.entity.senderId == uid() && m.payload != null) {
            repo.edit(m.entity, m.payload.copy(transcript = t))
        } else {
            transcriptPrefs.edit().putString(m.entity.id, t).apply()
            _transcripts.value = _transcripts.value + (m.entity.id to t)
        }
    }

    fun loadTranscripts(ids: List<String>) {
        _transcripts.value = ids.mapNotNull { id -> transcriptPrefs.getString(id, null)?.let { id to it } }.toMap()
    }

    // ---------- 2.0 ----------

    /** CHT-2: a recipe, list, event, poll… sent as a card that opens the item. */
    // ---------- polls live inside the chat (a poll is a message; votes update in place) ----------
    fun polls(familyId: String) = c.extras.polls(familyId)
    fun votes(familyId: String) = c.extras.votes(familyId)
    fun createPoll(chat: ChatEntity, question: String, options: List<String>, multi: Boolean, anonymous: Boolean, closesAt: Long) = launchSafe {
        if (!repo.hasKey(chat)) return@launchSafe say(R.string.chat_waiting_key)
        val poll = com.rimon.smartfamily.data.db.PollEntity(id = c.extras.newId(), familyId = chat.familyId, question = question.trim(),
            options = options.map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n"), multi = multi, anonymous = anonymous, closesAt = closesAt, createdBy = uid())
        c.extras.savePoll(poll)
        repo.send(chat, uid(), MessagePayload(text = "📊 " + poll.question, cardType = "poll", cardId = poll.id, cardTitle = poll.question), "card")
    }
    fun vote(p: com.rimon.smartfamily.data.db.PollEntity, choice: Int) = launchSafe {
        if (p.closed || (p.closesAt in 1 until System.currentTimeMillis())) return@launchSafe say(R.string.poll_closed)
        c.extras.vote(p, uid(), choice)
    }
    fun closePoll(p: com.rimon.smartfamily.data.db.PollEntity) = launchSafe { c.extras.savePoll(p.copy(closed = true)) }

    fun sendCard(chat: ChatEntity, type: String, id: String, title: String) = launchSafe {
        if (!repo.hasKey(chat)) return@launchSafe say(R.string.chat_waiting_key)
        repo.send(chat, uid(), MessagePayload(text = title, cardType = type, cardId = id, cardTitle = title), "card")
    }

    fun cardItems(familyId: String, module: com.rimon.smartfamily.model.AppModule, onItems: (List<com.rimon.smartfamily.data.repo.ShareItem>) -> Unit) = launchSafe { onItems(c.shares.items(familyId, module)) }

    @android.annotation.SuppressLint("MissingPermission")
    fun sendLocation(chat: ChatEntity) = launchSafe {
        say(R.string.chat_locating)
        val loc = com.rimon.smartfamily.smart.OneShotLocation.get(getApplication()) ?: return@launchSafe say(R.string.chat_no_location)
        repo.send(chat, uid(), MessagePayload(text = getApplication<Application>().getString(R.string.chat_my_location), lat = loc.latitude, lon = loc.longitude), "location")
    }

    fun forward(m: ShownMessage, to: ChatEntity) = launchSafe {
        val p = m.payload ?: return@launchSafe
        if (!repo.hasKey(to)) return@launchSafe say(R.string.chat_waiting_key)
        repo.send(to, uid(), p.copy(forwarded = true, transcript = ""), m.entity.kind, mediaPath = m.entity.mediaPath)
        say(R.string.chat_forwarded)
    }

    /** CHT-3 pinned messages are a shared 📌 reaction; starred ones stay on this phone. */
    fun togglePin(m: ChatMessageEntity) = react(m, "📌")
    private val starPrefs = app.getSharedPreferences("sf_starred", android.content.Context.MODE_PRIVATE)
    val starred = MutableStateFlow(starPrefs.getStringSet("ids", emptySet())!!.toSet())
    fun toggleStar(id: String) {
        val now = if (id in starred.value) starred.value - id else starred.value + id
        starPrefs.edit().putStringSet("ids", now).apply(); starred.value = now
    }

    fun scheduled(chatId: String) = c.db.scheduledMessages().observeChat(chatId)
    fun schedule(chat: ChatEntity, text: String, at: Long) = launchSafe {
        c.db.scheduledMessages().upsert(com.rimon.smartfamily.data.db.ScheduledMessageEntity(java.util.UUID.randomUUID().toString(), chat.id, text.trim(), at))
        say(R.string.chat_scheduled)
    }
    fun cancelScheduled(id: String) = launchSafe { c.db.scheduledMessages().delete(id) }

    /** CHT-9: a short summary of what was written since I last read (Gemini, free tier; only on request). */
    private val _summary = MutableStateFlow<String?>(null)
    val summary = _summary.asStateFlow()
    fun clearSummary() { _summary.value = null }
    fun summarize(chat: ChatEntity, shown: List<ShownMessage>, since: Long, names: Map<String, String>) = launchSafe {
        val lines = shown.filter { it.entity.sentAt > since && it.payload?.text?.isNotBlank() == true }.takeLast(150)
            .joinToString("\n") { (names[it.entity.senderId] ?: "?") + ": " + (it.payload?.text ?: it.payload?.transcript ?: "") }
        if (lines.isBlank()) return@launchSafe say(R.string.chat_nothing_new)
        val lang = getApplication<Application>().resources.configuration.locales[0].language.let { if (it == "iw" || it == "he") "Hebrew" else "English" }
        _summary.value = c.gemini.ask("Summarize this family group chat in $lang in up to 6 short bullet points: decisions, questions waiting for an answer, things to do. Chat:\n$lines").trim()
    }

    fun edit(m: ShownMessage, text: String) = launchSafe { m.payload?.let { repo.edit(m.entity, it.copy(text = text.trim())) } }
    fun delete(m: ChatMessageEntity) = launchSafe { repo.deleteForEveryone(m) }
    fun react(m: ChatMessageEntity, emoji: String) = launchSafe { repo.toggleReaction(m, uid(), emoji) }
}
