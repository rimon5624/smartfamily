package com.rimon.smartfamily.backup

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.rimon.smartfamily.SmartFamilyApp
import java.util.concurrent.TimeUnit

/**
 * Weekly encrypted backup into Downloads/SmartFamily (the last [KEEP] are kept).
 * The password the user chose is kept in the Android Keystore on this phone only; the file itself is
 * the same Argon2id + AES-GCM format as a manual backup, so it restores on a new phone with that password.
 */
class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as SmartFamilyApp).container
        val pw = c.crypto.get(KEY) ?: return Result.success()
        return runCatching {
            val name = "smartfamily-auto-" + java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date()) + ".sfbackup"
            val uri = create(applicationContext, name) ?: return Result.retry()
            c.backup.export(uri, pw, c.settings.current().userId)
            prune(applicationContext)
            applicationContext.getSharedPreferences("sf_auto_backup", Context.MODE_PRIVATE).edit().putLong("last", System.currentTimeMillis()).apply()
            Result.success()
        }.getOrElse { Result.retry() }
    }

    companion object {
        private const val WORK = "sf_auto_backup"
        const val KEY = "auto_backup_password"
        private const val KEEP = 4
        private const val DIR = "SmartFamily"

        fun enable(context: Context, password: String) {
            (context.applicationContext as SmartFamilyApp).container.crypto.put(KEY, password)
            val req = PeriodicWorkRequestBuilder<AutoBackupWorker>(7, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build()).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, req)
        }

        fun disable(context: Context) {
            (context.applicationContext as SmartFamilyApp).container.crypto.put(KEY, null)
            WorkManager.getInstance(context).cancelUniqueWork(WORK)
        }

        fun isOn(context: Context) = (context.applicationContext as SmartFamilyApp).container.crypto.get(KEY) != null
        fun last(context: Context) = context.getSharedPreferences("sf_auto_backup", Context.MODE_PRIVATE).getLong("last", 0L)

        private fun create(context: Context, name: String): Uri? =
            if (Build.VERSION.SDK_INT >= 29) {
                val v = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, BackupManager.MIME)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + DIR)
                }
                context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v)
            } else {
                val dir = java.io.File(context.getExternalFilesDir(null), DIR).apply { mkdirs() }
                Uri.fromFile(java.io.File(dir, name).apply { createNewFile() })
            }

        private fun prune(context: Context) {
            if (Build.VERSION.SDK_INT >= 29) {
                val cr = context.contentResolver
                val found = mutableListOf<Pair<Long, String>>()
                cr.query(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Downloads._ID, MediaStore.Downloads.DISPLAY_NAME),
                    MediaStore.Downloads.DISPLAY_NAME + " LIKE ?", arrayOf("smartfamily-auto-%"), null,
                )?.use { cur -> while (cur.moveToNext()) found += cur.getLong(0) to cur.getString(1) }
                found.sortedByDescending { it.second }.drop(KEEP).forEach { (id, _) ->
                    runCatching { cr.delete(android.content.ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id), null, null) }
                }
            } else {
                java.io.File(context.getExternalFilesDir(null), DIR).listFiles { f -> f.name.startsWith("smartfamily-auto-") }
                    ?.sortedByDescending { it.name }?.drop(KEEP)?.forEach { it.delete() }
            }
        }
    }
}
