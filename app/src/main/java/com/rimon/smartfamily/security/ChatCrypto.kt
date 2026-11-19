package com.rimon.smartfamily.security

import android.util.Base64
import com.google.crypto.tink.HybridDecrypt
import com.google.crypto.tink.HybridEncrypt
import com.google.crypto.tink.InsecureSecretKeyAccess
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.TinkJsonProtoKeysetFormat
import com.google.crypto.tink.hybrid.HybridConfig
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * End-to-end encryption for the family chat (CHT-10..13), built only from audited primitives:
 *  - every device has an HPKE key pair (X25519 + HKDF-SHA256 + AES-256-GCM, Google Tink);
 *    the private half is stored encrypted by the Android Keystore and never leaves the phone
 *    (except inside a password-protected backup, BAK-2);
 *  - every chat has a random 256-bit key per "epoch", sealed to each member device's public key;
 *  - messages and media are AES-256-GCM encrypted with that chat key before upload.
 * The server stores only public keys, sealed chat keys and ciphertext.
 */
class ChatCrypto(private val store: KeystoreCrypto) {

    init { HybridConfig.register() }

    private val rnd = SecureRandom()

    val deviceId: String
        get() = store.get(DEVICE) ?: UUID.randomUUID().toString().also { store.put(DEVICE, it) }

    private fun keyset(): KeysetHandle {
        store.get(KEYSET)?.let { json ->
            return TinkJsonProtoKeysetFormat.parseKeyset(json, InsecureSecretKeyAccess.get())
        }
        val handle = KeysetHandle.generateNew(KeyTemplates.get("DHKEM_X25519_HKDF_SHA256_HKDF_SHA256_AES_256_GCM"))
        store.put(KEYSET, TinkJsonProtoKeysetFormat.serializeKeyset(handle, InsecureSecretKeyAccess.get()))
        return handle
    }

    fun publicKeyset(): String = TinkJsonProtoKeysetFormat.serializeKeysetWithoutSecret(keyset().publicKeysetHandle)

    /** For the encrypted backup only. */
    fun exportPrivate(): String? = store.get(KEYSET)
    fun importPrivate(json: String, device: String) {
        TinkJsonProtoKeysetFormat.parseKeyset(json, InsecureSecretKeyAccess.get()) // validate
        store.put(KEYSET, json)
        store.put(DEVICE, device)
    }

    /** A safety number for comparing keys in person (CHT-12): 6 groups of 5 digits. */
    fun fingerprint(publicKeysetJson: String): String {
        val h = MessageDigest.getInstance("SHA-256").digest(publicKeysetJson.toByteArray())
        val digits = h.joinToString("") { (it.toInt() and 0xff).toString().padStart(3, '0') }
        return digits.take(30).chunked(5).joinToString(" ")
    }

    fun newChatKey(): ByteArray = ByteArray(32).also { rnd.nextBytes(it) }

    fun seal(chatKey: ByteArray, recipientPublicKeyset: String, context: String): String {
        val pub = TinkJsonProtoKeysetFormat.parseKeysetWithoutSecret(recipientPublicKeyset)
        @Suppress("DEPRECATION")
        val enc = pub.getPrimitive(HybridEncrypt::class.java)
        return b64(enc.encrypt(chatKey, context.toByteArray()))
    }

    fun open(sealed: String, context: String): ByteArray? = runCatching {
        @Suppress("DEPRECATION")
        val dec = keyset().getPrimitive(HybridDecrypt::class.java)
        dec.decrypt(unb64(sealed), context.toByteArray())
    }.getOrNull()

    /** Returns (nonce, ciphertext) in base64. [aad] binds the ciphertext to its chat, epoch and sender. */
    fun encrypt(key: ByteArray, plain: ByteArray, aad: String): Pair<String, String> {
        val nonce = ByteArray(12).also { rnd.nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        c.updateAAD(aad.toByteArray())
        return b64(nonce) to b64(c.doFinal(plain))
    }

    fun decrypt(key: ByteArray, nonce: String, ciphertext: String, aad: String): ByteArray? = runCatching {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, unb64(nonce)))
        c.updateAAD(aad.toByteArray())
        c.doFinal(unb64(ciphertext))
    }.getOrNull()

    /** Media: a fresh key per file; the key travels inside the (encrypted) message. Output = nonce || ciphertext. */
    fun encryptFile(bytes: ByteArray): Pair<ByteArray, ByteArray> {
        val key = ByteArray(32).also { rnd.nextBytes(it) }
        val nonce = ByteArray(12).also { rnd.nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        return key to (nonce + c.doFinal(bytes))
    }

    fun decryptFile(key: ByteArray, blob: ByteArray): ByteArray? = runCatching {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, blob.copyOfRange(0, 12)))
        c.doFinal(blob.copyOfRange(12, blob.size))
    }.getOrNull()

    fun b64(b: ByteArray): String = Base64.encodeToString(b, Base64.NO_WRAP)
    fun unb64(s: String): ByteArray = Base64.decode(s, Base64.NO_WRAP)

    companion object {
        private const val KEYSET = "chat_keyset"
        private const val DEVICE = "chat_device_id"
    }
}
