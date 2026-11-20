package com.rimon.smartfamily.smart

import com.kosherjava.zmanim.ComplexZmanimCalendar
import com.kosherjava.zmanim.hebrewcalendar.HebrewDateFormatter
import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar
import com.kosherjava.zmanim.hebrewcalendar.JewishDate
import com.kosherjava.zmanim.util.GeoLocation
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

/** A place for zmanim, with its local candle-lighting custom (minutes before sunset). */
data class ZmanimCity(val key: String, val he: String, val en: String, val lat: Double, val lon: Double, val elevation: Double, val candleOffset: Double)

object Cities {
    /** Hand-checked cities with their local candle-lighting custom (Jerusalem 40, Haifa and Safed 30). Keys never change. */
    private val curated = listOf(
        ZmanimCity("jerusalem", "ירושלים", "Jerusalem", 31.778, 35.235, 754.0, 40.0),
        ZmanimCity("tel_aviv", "תל אביב", "Tel Aviv", 32.085, 34.782, 20.0, 20.0),
        ZmanimCity("haifa", "חיפה", "Haifa", 32.794, 34.990, 100.0, 30.0),
        ZmanimCity("beer_sheva", "באר שבע", "Beer Sheva", 31.252, 34.791, 260.0, 20.0),
        ZmanimCity("kiryat_malachi", "קריית מלאכי", "Kiryat Malachi", 31.731, 34.746, 80.0, 20.0),
        ZmanimCity("ashdod", "אשדוד", "Ashdod", 31.804, 34.655, 30.0, 20.0),
        ZmanimCity("ashkelon", "אשקלון", "Ashkelon", 31.669, 34.571, 30.0, 20.0),
        ZmanimCity("rehovot", "רחובות", "Rehovot", 31.894, 34.811, 60.0, 20.0),
        ZmanimCity("modiin", "מודיעין", "Modi'in", 31.898, 35.010, 280.0, 20.0),
        ZmanimCity("bnei_brak", "בני ברק", "Bnei Brak", 32.084, 34.835, 30.0, 20.0),
        ZmanimCity("petah_tikva", "פתח תקווה", "Petah Tikva", 32.087, 34.887, 50.0, 20.0),
        ZmanimCity("netanya", "נתניה", "Netanya", 32.322, 34.853, 30.0, 20.0),
        ZmanimCity("beit_shemesh", "בית שמש", "Beit Shemesh", 31.747, 34.988, 300.0, 30.0),
        ZmanimCity("safed", "צפת", "Safed", 32.965, 35.496, 900.0, 30.0),
        ZmanimCity("tiberias", "טבריה", "Tiberias", 32.796, 35.530, -200.0, 20.0),
        ZmanimCity("eilat", "אילת", "Eilat", 29.558, 34.952, 10.0, 20.0),
        ZmanimCity("kiryat_gat", "קריית גת", "Kiryat Gat", 31.610, 34.764, 120.0, 20.0),
        ZmanimCity("sderot", "שדרות", "Sderot", 31.525, 34.596, 90.0, 20.0),
        ZmanimCity("afula", "עפולה", "Afula", 32.607, 35.290, 60.0, 20.0),
        ZmanimCity("ariel", "אריאל", "Ariel", 32.105, 35.187, 600.0, 20.0),
    )

    /** Every Israeli city, town, moshav and kibbutz (about 1,250), read once from il_localities.tsv in the APK. */
    val all: List<ZmanimCity> by lazy {
        val more = runCatching {
            Cities::class.java.getResourceAsStream("/il_localities.tsv")!!.bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.filter { it.isNotBlank() && !it.startsWith("#") }.mapNotNull { l ->
                    val f = l.split('\t')
                    if (f.size < 6) null else ZmanimCity(f[0], f[1], f[2], f[3].toDouble(), f[4].toDouble(), f[5].toDoubleOrNull() ?: 0.0,
                        if (f[1].startsWith("ירושלים") || f[1] == "מעלה אדומים" || f[1] == "ביתר עילית") 40.0 else 20.0)
                }.toList()
            }
        }.getOrDefault(emptyList())
        // a hand-checked city replaces the same place from the big list (same place = within 2.5 km)
        val extra = more.filter { m -> curated.none { c -> kotlin.math.abs(c.lat - m.lat) < 0.022 && kotlin.math.abs(c.lon - m.lon) < 0.027 && norm(c.he).take(3) == norm(m.he).take(3) } }
        (curated + extra).sortedBy { it.he }
    }

    fun byKey(key: String) = curated.firstOrNull { it.key == key } ?: all.firstOrNull { it.key == key } ?: curated.first()

    /** Spelling-tolerant: קריית/קרית, hyphens, quotes, double letters; Hebrew or English. */
    fun norm(s: String) = s.lowercase().replace("קריית", "קרית").replace("יי", "י").replace("וו", "ו")
        .filter { it.isLetterOrDigit() }

    fun search(q: String): List<ZmanimCity> {
        val n = norm(q)
        if (n.isEmpty()) return all
        return all.filter { norm(it.he).contains(n) || norm(it.en).contains(n) }
            .sortedWith(compareBy({ !(norm(it.he).startsWith(n) || norm(it.en).startsWith(n)) }, { it.he.length }))
    }

    /** The closest locality to a point (for "use my location"). */
    fun nearest(lat: Double, lon: Double) = all.minBy { (it.lat - lat) * (it.lat - lat) + (it.lon - lon) * (it.lon - lon) * 0.72 }
}

data class ShabbatTimes(val candleLighting: Date, val havdalah: Date, val parasha: String, val holiday: String)

object Zmanim {
    private val tz: TimeZone = TimeZone.getTimeZone("Asia/Jerusalem")

    private fun calendarFor(city: ZmanimCity, day: Calendar): ComplexZmanimCalendar {
        val c = ComplexZmanimCalendar(GeoLocation(city.en, city.lat, city.lon, city.elevation, tz))
        c.calendar = day.clone() as Calendar
        c.candleLightingOffset = city.candleOffset
        return c
    }

    private fun jewish(day: Calendar) = JewishCalendar(day.time).apply { setInIsrael(true) }

    private fun formatter() = HebrewDateFormatter().apply { isHebrewFormat = true }

    /**
     * The next Shabbat or Yom Tov that is still ahead (or happening now): candle lighting on its
     * eve and havdalah when it ends, joining Shabbat and Yom Tov that run together.
     */
    fun nextShabbat(city: ZmanimCity, now: Calendar = Calendar.getInstance(tz)): ShabbatTimes {
        val day = (now.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY, 12) }
        // Walk back if today is already a holy day (find its eve), else forward to the next eve.
        while (jewish(day).isAssurBemelacha) day.add(Calendar.DAY_OF_YEAR, -1)
        var guard = 0
        while (!jewish(day).hasCandleLighting() && guard++ < 14) day.add(Calendar.DAY_OF_YEAR, 1)
        val eve = day.clone() as Calendar
        val candle = calendarFor(city, eve).candleLighting
        val end = (eve.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
        while (jewish((end.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }).isAssurBemelacha) end.add(Calendar.DAY_OF_YEAR, 1)
        val havdalah = calendarFor(city, end).tzais
        // If havdalah has passed, the next one is a week (or holiday) ahead.
        if (havdalah != null && havdalah.before(now.time)) {
            val after = (end.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
            return nextShabbat(city, after.apply { set(Calendar.HOUR_OF_DAY, 6) })
        }
        val shabbatDay = (eve.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
        val jc = jewish(shabbatDay)
        val f = formatter()
        val parasha = runCatching { f.formatParsha(jc) }.getOrDefault("")
        val holiday = runCatching { f.formatYomTov(jc) }.getOrDefault("")
        return ShabbatTimes(candle ?: eve.time, havdalah ?: end.time, parasha, holiday)
    }

    /** TOG-4: true from candle lighting until havdalah of a Shabbat or Yom Tov. */
    fun isHolyTime(city: ZmanimCity, now: Calendar = Calendar.getInstance(tz)): Boolean {
        val s = nextShabbat(city, now)
        return !now.time.before(s.candleLighting) && now.time.before(s.havdalah)
    }

    /** Hebrew date like "כ״ח חשון תשפ״ז". */
    fun hebrewDate(date: Date): String = formatter().format(JewishCalendar(date).apply { setInIsrael(true) })

    /** Day of the Hebrew month in letters, e.g. "כח". */
    fun hebrewDayLetters(date: Date): String {
        val jc = JewishCalendar(date)
        return formatter().apply { isUseGershGershayim = false }.formatHebrewNumber(jc.jewishDayOfMonth)
    }

    fun hebrewMonthName(date: Date): String = formatter().formatMonth(JewishCalendar(date))

    /** Holiday or special day name for a date in Israel, "" if none. */
    fun holidayName(date: Date): String {
        val jc = JewishCalendar(date).apply { setInIsrael(true) }
        return runCatching { formatter().formatYomTov(jc) }.getOrDefault("")
    }

    data class HebrewParts(val day: Int, val month: Int, val year: Int)

    fun toHebrew(date: Date, afterSunset: Boolean): HebrewParts {
        val c = Calendar.getInstance(tz).apply { time = date; if (afterSunset) add(Calendar.DAY_OF_YEAR, 1) }
        val jc = JewishCalendar(c.time)
        return HebrewParts(jc.jewishDayOfMonth, jc.jewishMonth, jc.jewishYear)
    }

    /** The Gregorian date of a Hebrew day/month in Hebrew year [year], with Adar and short months handled. */
    fun fromHebrew(day: Int, month: Int, year: Int): Date {
        val jd = JewishDate()
        val leap = JewishDate().apply { setJewishDate(year, JewishDate.TISHREI, 1) }.isJewishLeapYear
        var m = month
        if (!leap && m == JewishDate.ADAR_II) m = JewishDate.ADAR
        if (leap && m == JewishDate.ADAR) m = JewishDate.ADAR_II // Adar events in a leap year fall in Adar II
        jd.setJewishDate(year, m, 1)
        val d = day.coerceIn(1, jd.daysInJewishMonth)
        jd.setJewishDate(year, m, d)
        return jd.gregorianCalendar.time
    }

    fun currentHebrewYear(): Int = JewishCalendar(Date()).jewishYear

    fun hebrewMonthNames(): List<Pair<Int, String>> {
        val f = formatter()
        // Tishrei-first order, the way the year is usually read.
        val order = listOf(7, 8, 9, 10, 11, 12, 13, 1, 2, 3, 4, 5, 6)
        return order.map { m ->
            val jd = JewishDate().apply { setJewishDate(5784, m, 1) } // 5784 is a leap year: every month exists
            m to f.formatMonth(jd)
        }
    }
}
