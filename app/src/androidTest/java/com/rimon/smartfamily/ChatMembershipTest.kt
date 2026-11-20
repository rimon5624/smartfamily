package com.rimon.smartfamily

import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.crypto.tink.InsecureSecretKeyAccess
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.TinkJsonProtoKeysetFormat
import com.google.crypto.tink.hybrid.HybridConfig
import com.rimon.smartfamily.data.db.MemberEntity
import com.rimon.smartfamily.data.db.UserKeyEntity
import com.rimon.smartfamily.model.AppModule
import com.rimon.smartfamily.data.repo.MessagePayload
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * The bug from 20/11/2026: a member who joins after the family chat exists never got into it.
 * Plays the second member's phone with its own key pair and checks, after one sync pass ([ChatRepository.maintain]):
 * she is in the family chat, she has the chat key, and she can decrypt a message sent before she joined.
 * Then she leaves: the key is rotated and she gets nothing for the new key.
 */
@RunWith(AndroidJUnit4::class)
class ChatMembershipTest {
    private val c get() = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as SmartFamilyApp).container

    @Test fun joinerGetsFamilyChatAndCanReadIt() = runBlocking {
        HybridConfig.register()
        val me = c.families.ensureLocalUser()
        val fam = c.families.createFamily("בדיקת צ'אט " + UUID.randomUUID().toString().take(4), AppModule.entries.toSet())

        // the family chat exists before she joins, and I send a message
        val chat = c.chat.ensureFamilyChat(fam.id, me, listOf(me), "family")!!
        c.chat.send(chat, me, MessagePayload(text = "שלום לפני שהצטרפת"), "text")
        assertFalse(chat.members.contains("wife"))

        // her phone: own key pair, published as a user key; then she becomes an active member
        val herKeys = KeysetHandle.generateNew(KeyTemplates.get("DHKEM_X25519_HKDF_SHA256_HKDF_SHA256_AES_256_GCM"))
        val herPub = TinkJsonProtoKeysetFormat.serializeKeysetWithoutSecret(herKeys.publicKeysetHandle)
        val herId = "wife-" + UUID.randomUUID().toString().take(6)
        val herDevice = "dev-" + UUID.randomUUID().toString().take(6)
        c.db.userKeys().upsert(UserKeyEntity(id = "$herDevice|${fam.id}", familyId = fam.id, userId = herId, deviceId = herDevice, publicKeyset = herPub))
        c.db.members().upsert(MemberEntity(id = UUID.randomUUID().toString(), familyId = fam.id, userId = herId, displayName = "Wife", role = "ADMIN"))

        // one sync pass on my phone
        c.chat.maintain(me)

        val after = c.db.chats().get(chat.id)!!
        assertTrue("she must be in the family chat", herId in after.members.split(','))
        val sealed = c.db.chatKeys().forEpoch(chat.id, after.epoch).firstOrNull { it.deviceId == herDevice }
        assertNotNull("her device must get the chat key", sealed)

        // she opens the key with her private key and reads my earlier message
        @Suppress("DEPRECATION")
        val dec = herKeys.getPrimitive(com.google.crypto.tink.HybridDecrypt::class.java)
        val chatKey = dec.decrypt(Base64.decode(sealed!!.wrapped, Base64.NO_WRAP), "smartfamily-chat|${chat.id}|${after.epoch}".toByteArray())
        val msg = c.db.chatMessages().observeChat(chat.id).first().first()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(chatKey, "AES"), GCMParameterSpec(128, Base64.decode(msg.nonce, Base64.NO_WRAP)))
        cipher.updateAAD("${msg.chatId}|${msg.epoch}|${msg.senderId}|${msg.id}".toByteArray())
        val text = String(cipher.doFinal(Base64.decode(msg.ciphertext, Base64.NO_WRAP)))
        assertTrue(text.contains("שלום לפני שהצטרפת"))

        // she leaves: a new key, and nothing sealed to her for it
        val her = c.db.members().find(fam.id, herId)!!
        c.db.members().upsert(her.copy(deletedAt = System.currentTimeMillis()))
        c.chat.maintain(me)
        val rotated = c.db.chats().get(chat.id)!!
        assertEquals(after.epoch + 1, rotated.epoch)
        assertFalse(herId in rotated.members.split(','))
        assertTrue(c.db.chatKeys().forEpoch(chat.id, rotated.epoch).none { it.deviceId == herDevice })
        Unit
    }
}
