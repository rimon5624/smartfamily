package com.rimon.smartfamily.smart

import com.rimon.smartfamily.AppContainer
import com.rimon.smartfamily.security.KeystoreCrypto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 2.2: calendars imported from a secret iCal address (Google Calendar, Outlook, a school's calendar) and refreshed
 * every few hours. The address is a password in disguise, so it is kept encrypted on this phone only.
 */
object IcsSubscriptions {
    @Serializable data class Sub(val url: String, val familyId: String, val owner: String, val everyHours: Int = 24, val last: Long = 0)
    private const val KEY = "ics_subscriptions"
    private val json = Json { ignoreUnknownKeys = true }
    private val http = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(40, TimeUnit.SECONDS).build()

    fun list(crypto: KeystoreCrypto): List<Sub> = crypto.get(KEY)?.let { runCatching { json.decodeFromString<List<Sub>>(it) }.getOrNull() }.orEmpty()
    private fun save(crypto: KeystoreCrypto, l: List<Sub>) = crypto.put(KEY, json.encodeToString(l))
    fun add(crypto: KeystoreCrypto, url: String, familyId: String, owner: String, everyHours: Int) =
        save(crypto, list(crypto).filter { it.url != normalize(url) } + Sub(normalize(url), familyId, owner, everyHours))
    fun remove(crypto: KeystoreCrypto, url: String) = save(crypto, list(crypto).filter { it.url != url })

    fun normalize(url: String) = url.trim().replaceFirst(Regex("^webcals?://", RegexOption.IGNORE_CASE), "https://")

    /** Downloads and parses the calendar (HTTPS only). */
    suspend fun fetch(url: String): List<Ics.Parsed> = withContext(Dispatchers.IO) {
        val u = normalize(url)
        if (!u.startsWith("https://", true)) throw IOException("https")
        http.newCall(Request.Builder().url(u).build()).execute().use { r ->
            if (r.code == 404 || r.code == 401 || r.code == 403) throw IOException("not found")
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            val text = r.body?.string().orEmpty()
            if (!text.contains("BEGIN:VCALENDAR")) throw IOException("not a calendar")
            Ics.parse(text)
        }
    }

    /** Adds the calendar's new events (title + start not already there); returns how many. */
    suspend fun refreshOne(c: AppContainer, s: Sub): Int {
        val parsed = fetch(s.url)
        val existing = c.homeLife.allEventsForReminders().filter { it.familyId == s.familyId }.map { it.title to it.startAt }.toSet()
        var n = 0
        parsed.filter { (it.title to it.start) !in existing }.forEach { c.homeLife.saveEvent(Ics.toEntity(it, s.familyId, s.owner)); n++ }
        save(c.crypto, list(c.crypto).map { if (it.url == s.url) it.copy(last = System.currentTimeMillis()) else it })
        return n
    }

    /** Called from the 15-minute worker: refreshes the subscriptions that are due. */
    suspend fun refreshDue(c: AppContainer) {
        val now = System.currentTimeMillis()
        list(c.crypto).filter { now - it.last > it.everyHours * 3_600_000L }.forEach { runCatching { refreshOne(c, it) } }
    }
}
