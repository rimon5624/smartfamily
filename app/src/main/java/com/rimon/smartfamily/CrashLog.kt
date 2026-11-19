package com.rimon.smartfamily

import android.content.Context
import android.os.Build
import java.io.File

/**
 * If the app crashes, the error is saved on the phone (nothing is sent anywhere) and shown on the
 * next start, so it can be copied and sent to whoever maintains the app.
 */
object CrashLog {
    private fun file(c: Context) = File(c.filesDir, "last_crash.txt")

    fun install(c: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching {
                file(c).writeText(
                    "SmartFamily ${BuildConfig.VERSION_NAME} · Android ${Build.VERSION.RELEASE} · ${Build.MANUFACTURER} ${Build.MODEL}\n" +
                        "Thread: ${t.name}\n\n" + e.stackTraceToString().take(12_000)
                )
            }
            previous?.uncaughtException(t, e)
        }
    }

    fun take(c: Context): String? = file(c).takeIf { it.exists() }?.let { f -> runCatching { f.readText() }.getOrNull().also { f.delete() } }
}
