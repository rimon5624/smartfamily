package com.rimon.smartfamily.backup

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.room.withTransaction
import com.rimon.smartfamily.BuildConfig
import com.rimon.smartfamily.data.db.AppDatabase
import com.rimon.smartfamily.data.db.FamilyEntity
import com.rimon.smartfamily.data.db.InviteEntity
import com.rimon.smartfamily.data.db.MemberEntity
import com.rimon.smartfamily.data.db.ModuleSettingEntity
import com.rimon.smartfamily.data.db.ProfileEntity
import com.rimon.smartfamily.model.SchemaInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@Serializable
data class BackupPayload(
    val schemaVersion: Int,
    val userId: String,
    val profiles: List<ProfileEntity> = emptyList(),
    val families: List<FamilyEntity> = emptyList(),
    val members: List<MemberEntity> = emptyList(),
    val invites: List<InviteEntity> = emptyList(),
    val moduleSettings: List<ModuleSettingEntity> = emptyList(),
)

/** Unencrypted header: everything needed to derive the key, nothing about the content. */
@Serializable
data class BackupFile(
    val format: String = SchemaInfo.BACKUP_FORMAT,
    val formatVersion: Int = SchemaInfo.BACKUP_FORMAT_VERSION,
    val appVersion: String,
    val createdAt: Long,
    val kdf: String = "argon2id",
    val argonMemoryKb: Int,
    val argonIterations: Int,
    val argonParallelism: Int,
    val salt: String,
    val iv: String,
    val ciphertext: String,
)

class WrongPasswordException : Exception("Wrong backup password or damaged file")
class UnsupportedBackupException(msg: String) : Exception(msg)

/**
 * BAK-2: the whole local database in one file, encrypted with AES-256-GCM under a key derived
 * from the backup password with Argon2id. The file is useless without the password.
 */
class BackupManager(private val context: Context, private val db: AppDatabase) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun export(uri: Uri, password: String, userId: String) = withContext(Dispatchers.IO) {
        require(password.length >= 8) { "Password too short" }
        val payload = BackupPayload(
            schemaVersion = SchemaInfo.DB_VERSION,
            userId = userId,
            profiles = db.profiles().all(),
            families = db.families().all(),
            members = db.members().all(),
            invites = db.invites().all(),
            moduleSettings = db.moduleSettings().all(),
        )
        val plain = json.encodeToString(BackupPayload.serializer(), payload).toByteArray(Charsets.UTF_8)
        val rnd = SecureRandom()
        val salt = ByteArray(16).also { rnd.nextBytes(it) }
        val iv = ByteArray(12).also { rnd.nextBytes(it) }
        val key = deriveKey(password, salt, MEMORY_KB, ITERATIONS, PARALLELISM)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        cipher.updateAAD(SchemaInfo.BACKUP_FORMAT.toByteArray())
        val file = BackupFile(
            appVersion = BuildConfig.VERSION_NAME,
            createdAt = System.currentTimeMillis(),
            argonMemoryKb = MEMORY_KB,
            argonIterations = ITERATIONS,
            argonParallelism = PARALLELISM,
            salt = b64(salt),
            iv = b64(iv),
            ciphertext = b64(cipher.doFinal(plain)),
        )
        context.contentResolver.openOutputStream(uri, "wt")!!.use {
            it.write(json.encodeToString(BackupFile.serializer(), file).toByteArray(Charsets.UTF_8))
        }
    }

    /** Reads and decrypts a backup without changing anything, so the UI can confirm first. */
    suspend fun read(uri: Uri, password: String): BackupPayload = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
        val file = runCatching { json.decodeFromString(BackupFile.serializer(), text) }
            .getOrElse { throw UnsupportedBackupException("Not a SmartFamily backup file") }
        if (file.format != SchemaInfo.BACKUP_FORMAT) throw UnsupportedBackupException("Not a SmartFamily backup file")
        if (file.formatVersion > SchemaInfo.BACKUP_FORMAT_VERSION) {
            throw UnsupportedBackupException("This backup was made by a newer app version (${file.appVersion}); update the app first")
        }
        val key = deriveKey(password, unb64(file.salt), file.argonMemoryKb, file.argonIterations, file.argonParallelism)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, unb64(file.iv)))
        cipher.updateAAD(SchemaInfo.BACKUP_FORMAT.toByteArray())
        val plain = runCatching { cipher.doFinal(unb64(file.ciphertext)) }.getOrElse { throw WrongPasswordException() }
        // BAK-8: older payload versions are upgraded here as the schema grows.
        json.decodeFromString(BackupPayload.serializer(), plain.toString(Charsets.UTF_8))
    }

    /** Replaces the local data with the backup; rows are marked dirty so they sync back up. */
    suspend fun restore(payload: BackupPayload) = withContext(Dispatchers.IO) {
        db.withTransaction {
            db.moduleSettings().clear(); db.invites().clear(); db.members().clear()
            db.families().clear(); db.profiles().clear()
            db.profiles().upsertAll(payload.profiles.map { it.copy(dirty = true) })
            db.families().upsertAll(payload.families.map { it.copy(dirty = true) })
            db.members().upsertAll(payload.members.map { it.copy(dirty = true) })
            db.invites().upsertAll(payload.invites.map { it.copy(dirty = true) })
            db.moduleSettings().upsertAll(payload.moduleSettings.map { it.copy(dirty = true) })
        }
    }

    private fun deriveKey(password: String, salt: ByteArray, memoryKb: Int, iterations: Int, parallelism: Int): ByteArray {
        val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withSalt(salt)
            .withMemoryAsKB(memoryKb)
            .withIterations(iterations)
            .withParallelism(parallelism)
            .build()
        val out = ByteArray(32)
        Argon2BytesGenerator().apply { init(params) }.generateBytes(password.toCharArray(), out)
        return out
    }

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun unb64(s: String) = Base64.decode(s, Base64.NO_WRAP)

    companion object {
        // OWASP baseline for Argon2id: 19 MiB memory, 2 iterations, 1 lane.
        private const val MEMORY_KB = 19 * 1024
        private const val ITERATIONS = 2
        private const val PARALLELISM = 1
        const val MIME = "application/octet-stream"
        fun suggestedName(): String {
            val d = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
            return "smartfamily-backup-$d.sfbackup"
        }
    }
}
