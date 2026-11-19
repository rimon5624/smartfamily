package com.rimon.smartfamily.data.repo

import com.rimon.smartfamily.data.db.AppDatabase
import com.rimon.smartfamily.data.db.BudgetEntryEntity
import com.rimon.smartfamily.data.db.BudgetLimitEntity
import com.rimon.smartfamily.data.db.HomeEntryEntity
import com.rimon.smartfamily.data.db.PollEntity
import com.rimon.smartfamily.data.db.PollVoteEntity
import com.rimon.smartfamily.data.db.TripEntity
import com.rimon.smartfamily.data.db.TripItemEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Calendar
import java.util.UUID
import java.util.concurrent.TimeUnit

/** One place found by the free Open-Meteo geocoding service. */
data class Place(val name: String, val country: String, val lat: Double, val lon: Double)

/** Daily forecast summary for a trip. */
data class TripWeather(val minC: Double, val maxC: Double, val rainChance: Int, val days: Int) {
    val cold get() = minC < 12
    val hot get() = maxC >= 28
    val rainy get() = rainChance >= 40
}

/** Open-Meteo: free, no key, no account (forecast up to 16 days ahead). */
class OpenMeteo {
    private val http = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true }

    private fun get(url: okhttp3.HttpUrl): JsonObject {
        http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) throw java.io.IOException("Open-Meteo HTTP ${r.code}")
            return json.parseToJsonElement(r.body!!.string()).jsonObject
        }
    }

    suspend fun search(name: String, lang: String): List<Place> = withContext(Dispatchers.IO) {
        val url = "https://geocoding-api.open-meteo.com/v1/search".toHttpUrl().newBuilder()
            .addQueryParameter("name", name).addQueryParameter("count", "6").addQueryParameter("language", lang).build()
        (get(url)["results"] as? JsonArray).orEmpty().map { it.jsonObject }.map {
            Place(
                it["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                it["country"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                it["latitude"]!!.jsonPrimitive.double, it["longitude"]!!.jsonPrimitive.double,
            )
        }
    }

    /** Null when the dates are too far ahead (more than 16 days) for a forecast. */
    suspend fun forecast(lat: Double, lon: Double, start: String, end: String): TripWeather? = withContext(Dispatchers.IO) {
        val url = "https://api.open-meteo.com/v1/forecast".toHttpUrl().newBuilder()
            .addQueryParameter("latitude", lat.toString()).addQueryParameter("longitude", lon.toString())
            .addQueryParameter("daily", "temperature_2m_max,temperature_2m_min,precipitation_probability_max")
            .addQueryParameter("timezone", "auto")
            .addQueryParameter("start_date", start).addQueryParameter("end_date", end).build()
        val daily = runCatching { get(url)["daily"]?.jsonObject }.getOrNull() ?: return@withContext null
        fun list(k: String) = daily[k]?.jsonArray?.mapNotNull { it.jsonPrimitive.doubleOrNull }.orEmpty()
        val max = list("temperature_2m_max"); val min = list("temperature_2m_min"); val rain = list("precipitation_probability_max")
        if (max.isEmpty()) null else TripWeather(min.minOrNull() ?: 0.0, max.max(), (rain.maxOrNull() ?: 0.0).toInt(), max.size)
    }
}

/** Built-in packing list (spec: travel packing). Titles in Hebrew and English. */
object PackingLists {
    data class Item(val he: String, val en: String, val cat: String, val perPerson: Boolean = false, val perDay: Boolean = false)

    private val base = listOf(
        Item("תעודות זהות", "ID cards", "docs"), Item("מטענים", "Chargers", "tech"), Item("מטען נייד", "Power bank", "tech"),
        Item("תרופות קבועות", "Regular medicines", "health"), Item("ערכת עזרה ראשונה", "First-aid kit", "health"),
        Item("מברשת ומשחת שיניים", "Toothbrush & toothpaste", "toiletries", perPerson = true), Item("שמפו וסבון", "Shampoo & soap", "toiletries"),
        Item("דאודורנט", "Deodorant", "toiletries"), Item("פיג'מה", "Pajamas", "clothes", perPerson = true),
        Item("תחתונים", "Underwear", "clothes", perPerson = true, perDay = true), Item("גרביים", "Socks", "clothes", perPerson = true, perDay = true),
        Item("חולצות", "Shirts", "clothes", perPerson = true, perDay = true), Item("מכנסיים", "Pants", "clothes", perPerson = true),
        Item("נעלי הליכה", "Walking shoes", "clothes", perPerson = true), Item("בקבוקי מים", "Water bottles", "general", perPerson = true),
        Item("חטיפים לדרך", "Snacks for the road", "food"), Item("שקיות לכביסה מלוכלכת", "Laundry bags", "general"),
    )
    private val byKind = mapOf(
        "BEACH" to listOf(Item("בגד ים", "Swimsuit", "clothes", perPerson = true), Item("מגבת חוף", "Beach towel", "general", perPerson = true),
            Item("קרם הגנה", "Sunscreen", "health"), Item("כובע", "Hat", "clothes", perPerson = true), Item("כפכפים", "Flip-flops", "clothes", perPerson = true),
            Item("משקפי שמש", "Sunglasses", "general", perPerson = true), Item("צעצועי חול", "Sand toys", "kids")),
        "NATURE" to listOf(Item("תיק גב", "Backpack", "general", perPerson = true), Item("כובע", "Hat", "clothes", perPerson = true),
            Item("קרם הגנה", "Sunscreen", "health"), Item("דוחה יתושים", "Insect repellent", "health"), Item("פנס", "Flashlight", "tech")),
        "CAMPING" to listOf(Item("אוהל", "Tent", "camping"), Item("שקי שינה", "Sleeping bags", "camping", perPerson = true),
            Item("מזרנים", "Mattresses", "camping", perPerson = true), Item("פנס ראש", "Headlamp", "camping", perPerson = true),
            Item("גזייה וגז", "Camping stove & gas", "camping"), Item("כלי אוכל חד פעמיים", "Disposable dishes", "food"),
            Item("צידנית", "Cooler", "food"), Item("שקיות זבל", "Trash bags", "camping"), Item("נייר טואלט", "Toilet paper", "camping"),
            Item("כיסאות מתקפלים", "Folding chairs", "camping")),
        "CITY" to listOf(Item("נעליים נוחות", "Comfortable shoes", "clothes", perPerson = true), Item("תיק יום", "Day bag", "general"),
            Item("מטריה מתקפלת", "Small umbrella", "general")),
        "HOTEL" to listOf(Item("בגדים לערב", "Evening clothes", "clothes", perPerson = true), Item("בגד ים לבריכה", "Swimsuit for the pool", "clothes", perPerson = true)),
        "ABROAD" to listOf(Item("דרכונים", "Passports", "docs"), Item("כרטיסי טיסה", "Flight tickets", "docs"), Item("ביטוח נסיעות", "Travel insurance", "docs"),
            Item("מתאם חשמל", "Plug adapter", "tech"), Item("מטבע זר וכרטיס אשראי", "Foreign cash & card", "docs"), Item("כרית צוואר", "Neck pillow", "general", perPerson = true)),
        "SHABBAT" to listOf(Item("בגדי שבת", "Shabbat clothes", "clothes", perPerson = true), Item("נרות שבת", "Shabbat candles", "shabbat"),
            Item("גביע קידוש ויין", "Kiddush cup & wine", "shabbat"), Item("סידורים", "Siddurim", "shabbat", perPerson = true),
            Item("פלטה / מיחם", "Hot plate / urn", "shabbat"), Item("חלות", "Challot", "shabbat"), Item("נייר טואלט חתוך", "Pre-torn tissues", "shabbat")),
        "BABY" to listOf(Item("חיתולים", "Diapers", "kids", perDay = true), Item("מגבונים", "Wipes", "kids"), Item("בקבוקים ומטרנה", "Bottles & formula", "kids"),
            Item("מוצץ", "Pacifier", "kids"), Item("עגלה", "Stroller", "kids"), Item("לול מתקפל", "Travel cot", "kids"), Item("משחקים", "Toys", "kids")),
    )
    private val cold = listOf(Item("מעיל", "Coat", "clothes", perPerson = true), Item("סוודר", "Sweater", "clothes", perPerson = true),
        Item("כובע צמר וכפפות", "Beanie & gloves", "clothes", perPerson = true), Item("גופייה תרמית", "Thermal underwear", "clothes", perPerson = true))
    private val hot = listOf(Item("מכנסיים קצרים", "Shorts", "clothes", perPerson = true), Item("קרם הגנה", "Sunscreen", "health"), Item("כובע", "Hat", "clothes", perPerson = true))
    private val rainy = listOf(Item("מעיל גשם", "Raincoat", "clothes", perPerson = true), Item("מטריה", "Umbrella", "general"), Item("נעליים להחלפה", "Spare shoes", "clothes", perPerson = true))

    fun build(kinds: Set<String>, weather: TripWeather?, travelers: Int, days: Int, hebrew: Boolean): List<Triple<String, String, Int>> {
        val all = (base + kinds.flatMap { byKind[it].orEmpty() } +
            (if (weather?.cold == true) cold else emptyList()) +
            (if (weather?.hot == true) hot else emptyList()) +
            (if (weather?.rainy == true) rainy else emptyList())).distinctBy { it.en }
        val n = travelers.coerceAtLeast(1); val d = days.coerceIn(1, 14)
        return all.map { i ->
            val qty = (if (i.perPerson) n else 1) * (if (i.perDay) d else 1)
            Triple(if (hebrew) i.he else i.en, i.cat, qty)
        }
    }
}

class ExtrasRepository(private val db: AppDatabase, private val onLocalChange: () -> Unit) {
    private fun now() = System.currentTimeMillis()
    private fun uuid() = UUID.randomUUID().toString()
    val meteo = OpenMeteo()

    // ---------- travel ----------
    fun trips(familyId: String) = db.trips().observeSorted(familyId)
    fun trip(id: String) = db.trips().observeOne(id)
    fun tripItems(tripId: String) = db.tripItems().observeTrip(tripId)

    suspend fun saveTrip(t: TripEntity) { db.trips().upsert(t.copy(updatedAt = now(), dirty = true)); onLocalChange() }
    suspend fun deleteTrip(t: TripEntity) { db.trips().upsert(t.copy(deletedAt = now(), updatedAt = now(), dirty = true)); onLocalChange() }

    /** Adds the generated items that are not on the list yet. */
    suspend fun addItems(trip: TripEntity, items: List<Triple<String, String, Int>>) {
        val have = db.tripItems().forTrip(trip.id).map { it.title.lowercase() }.toSet()
        items.filter { it.first.lowercase() !in have }.forEachIndexed { i, (title, cat, qty) ->
            db.tripItems().upsert(TripItemEntity(id = uuid(), familyId = trip.familyId, tripId = trip.id, title = title, category = cat, qty = qty, sort = i))
        }
        onLocalChange()
    }

    suspend fun saveItem(i: TripItemEntity) { db.tripItems().upsert(i.copy(updatedAt = now(), dirty = true)); onLocalChange() }
    suspend fun deleteItem(i: TripItemEntity) { db.tripItems().upsert(i.copy(deletedAt = now(), updatedAt = now(), dirty = true)); onLocalChange() }
    suspend fun resetPacked(tripId: String) {
        db.tripItems().forTrip(tripId).filter { it.packed }.forEach { db.tripItems().upsert(it.copy(packed = false, packedBy = "", updatedAt = now(), dirty = true)) }
        onLocalChange()
    }

    // ---------- budget ----------
    fun month(familyId: String, year: Int, month: Int): kotlinx.coroutines.flow.Flow<List<BudgetEntryEntity>> {
        val cal = Calendar.getInstance().apply { clear(); set(year, month, 1) }
        val from = cal.timeInMillis
        cal.add(Calendar.MONTH, 1)
        return db.budgetEntrys().observeMonth(familyId, from, cal.timeInMillis)
    }
    fun limits(familyId: String) = db.budgetLimits().observeForFamily(familyId)
    suspend fun saveEntry(e: BudgetEntryEntity) { db.budgetEntrys().upsert(e.copy(updatedAt = now(), dirty = true)); onLocalChange() }
    suspend fun deleteEntry(e: BudgetEntryEntity) { db.budgetEntrys().upsert(e.copy(deletedAt = now(), updatedAt = now(), dirty = true)); onLocalChange() }
    suspend fun setLimit(familyId: String, category: String, monthly: Double) {
        db.budgetLimits().upsert(BudgetLimitEntity(id = "$familyId|$category", familyId = familyId, category = category, monthly = monthly))
        onLocalChange()
    }

    // ---------- home book & knowledge box ----------
    fun section(familyId: String, section: String) = db.homeEntrys().observeSection(familyId, section)
    suspend fun saveHomeEntry(e: HomeEntryEntity) { db.homeEntrys().upsert(e.copy(updatedAt = now(), dirty = true)); onLocalChange() }
    suspend fun deleteHomeEntry(e: HomeEntryEntity) { db.homeEntrys().upsert(e.copy(deletedAt = now(), updatedAt = now(), dirty = true)); onLocalChange() }
    suspend fun expiring(withinDays: Int) = db.homeEntrys().expiring(now(), now() + withinDays * 86_400_000L)

    // ---------- polls ----------
    fun polls(familyId: String) = db.polls().observeSorted(familyId)
    fun votes(familyId: String) = db.pollVotes().observeForFamily(familyId)
    suspend fun savePoll(p: PollEntity) { db.polls().upsert(p.copy(updatedAt = now(), dirty = true)); onLocalChange() }
    suspend fun deletePoll(p: PollEntity) { db.polls().upsert(p.copy(deletedAt = now(), updatedAt = now(), dirty = true)); onLocalChange() }
    suspend fun vote(p: PollEntity, userId: String, choice: Int) {
        val id = "${p.id}|$userId"
        val current = db.pollVotes().get(id)?.takeIf { it.deletedAt == null }?.choices?.split(',')?.mapNotNull { it.toIntOrNull() }.orEmpty()
        val next = when {
            choice in current -> current - choice
            p.multi -> current + choice
            else -> listOf(choice)
        }
        db.pollVotes().upsert(PollVoteEntity(id = id, familyId = p.familyId, pollId = p.id, userId = userId, choices = next.sorted().joinToString(",")))
        onLocalChange()
    }

    fun newId() = uuid()
}
