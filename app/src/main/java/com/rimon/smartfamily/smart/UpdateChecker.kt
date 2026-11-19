package com.rimon.smartfamily.smart

import com.rimon.smartfamily.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class UpdateInfo(val version: String, val apkUrl: String, val pageUrl: String, val notes: String)

/** REL-4: the app is sideloaded, so it checks the GitHub releases of its own (public) repository. */
object UpdateChecker {
    private const val REPO = "rimon5624/smartfamily"
    private val http = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build()

    /** The newest release when it is newer than this app, otherwise null. */
    suspend fun latest(): UpdateInfo? = withContext(Dispatchers.IO) {
        val req = Request.Builder().url("https://api.github.com/repos/$REPO/releases/latest").header("Accept", "application/vnd.github+json").build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) return@withContext null
            val o = Json.parseToJsonElement(r.body!!.string()).jsonObject
            val tag = o["tag_name"]?.jsonPrimitive?.contentOrNull?.removePrefix("v") ?: return@withContext null
            if (compare(tag, BuildConfig.VERSION_NAME) <= 0) return@withContext null
            val apk = o["assets"]?.jsonArray?.map { it.jsonObject }?.firstOrNull { it["name"]?.jsonPrimitive?.contentOrNull?.endsWith(".apk") == true }
            UpdateInfo(
                tag,
                apk?.get("browser_download_url")?.jsonPrimitive?.contentOrNull.orEmpty(),
                o["html_url"]?.jsonPrimitive?.contentOrNull ?: "https://github.com/$REPO/releases",
                o["body"]?.jsonPrimitive?.contentOrNull.orEmpty().take(1500),
            )
        }
    }

    fun compare(a: String, b: String): Int {
        val x = a.split('.', '-').map { it.toIntOrNull() ?: 0 }
        val y = b.split('.', '-').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = (x.getOrElse(i) { 0 }).compareTo(y.getOrElse(i) { 0 })
            if (d != 0) return d
        }
        return 0
    }
}
