package com.rimon.smartfamily.smart

import com.kosherjava.zmanim.ComplexZmanimCalendar
import com.kosherjava.zmanim.hebrewcalendar.HebrewDateFormatter
import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar
import com.kosherjava.zmanim.hebrewcalendar.JewishDate
import com.kosherjava.zmanim.util.GeoLocation
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar
import java.util.Date
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap

/** Everything the calendar shows about one day (DAT-5, DAT-9). */
data class DayInfo(
    val date: LocalDate,
    val hDay: Int, val hMonth: Int, val hYear: Int,
    val hDayLetters: String,
    val hFull: String,
    val holidays: List<String>,
    val yomTov: Boolean,
    val fast: Boolean,
    val roshChodesh: Boolean,
    val parsha: String,
    val omer: Int,
    val candles: Date?,
    val havdalah: Date?,
) {
    val shabbat get() = date.dayOfWeek == DayOfWeek.SATURDAY
}

/** Hebrew calendar helpers on top of KosherJava (free, runs on the phone). */
object JewishDays {
    val TZ: TimeZone = TimeZone.getTimeZone("Asia/Jerusalem")
    val ZONE: ZoneId = ZoneId.of("Asia/Jerusalem")
    private val cache = ConcurrentHashMap<String, DayInfo>()

    private fun f() = HebrewDateFormatter().apply { isHebrewFormat = true }
    private fun fLetters() = HebrewDateFormatter().apply { isHebrewFormat = true; isUseGershGershayim = false }

    fun noon(d: LocalDate): Date = Date.from(d.atTime(12, 0).atZone(ZONE).toInstant())
    fun toLocal(ms: Long): LocalDate = java.time.Instant.ofEpochMilli(ms).atZone(ZONE).toLocalDate()
    fun startMs(d: LocalDate): Long = d.atStartOfDay(ZONE).toInstant().toEpochMilli()

    private fun jc(d: LocalDate) = JewishCalendar(noon(d)).apply { setInIsrael(true) }

    private fun zc(city: ZmanimCity, d: LocalDate): ComplexZmanimCalendar {
        val c = ComplexZmanimCalendar(GeoLocation(city.en, city.lat, city.lon, city.elevation, TZ))
        c.calendar = Calendar.getInstance(TZ).apply { time = noon(d) }
        c.candleLightingOffset = city.candleOffset
        return c
    }

    fun info(d: LocalDate, city: ZmanimCity): DayInfo = cache.getOrPut("${city.key}|$d") {
        val j = jc(d)
        val fm = f()
        val hol = mutableListOf<String>()
        runCatching { fm.formatYomTov(j) }.getOrNull()?.takeIf { it.isNotBlank() }?.let { hol += it }
        if (j.isRoshChodesh) runCatching { fm.formatRoshChodesh(j) }.getOrNull()?.takeIf { it.isNotBlank() && it !in hol }?.let { hol += it }
        val omer = j.dayOfOmer
        val parsha = if (d.dayOfWeek == DayOfWeek.SATURDAY) runCatching { fm.formatParsha(j) }.getOrDefault("") else ""
        val special = if (d.dayOfWeek == DayOfWeek.SATURDAY) runCatching { fm.formatSpecialParsha(j) }.getOrDefault("") else ""
        if (special.isNotBlank()) hol += special
        val candles = if (j.hasCandleLighting()) runCatching { zc(city, d).candleLighting }.getOrNull() else null
        val tomorrow = jc(d.plusDays(1))
        val havdalah = if (j.isAssurBemelacha && !tomorrow.isAssurBemelacha) runCatching { zc(city, d).tzais }.getOrNull() else null
        DayInfo(
            date = d, hDay = j.jewishDayOfMonth, hMonth = j.jewishMonth, hYear = j.jewishYear,
            hDayLetters = fLetters().formatHebrewNumber(j.jewishDayOfMonth),
            hFull = fm.format(j),
            holidays = hol, yomTov = j.isYomTovAssurBemelacha, fast = j.isTaanis, roshChodesh = j.isRoshChodesh,
            parsha = parsha, omer = if (omer > 0) omer else 0, candles = candles, havdalah = havdalah,
        )
    }

    /** Daily zmanim on demand (DAT-9). */
    fun zmanim(d: LocalDate, city: ZmanimCity): List<Pair<String, Date?>> {
        val z = zc(city, d)
        return listOf(
            "alos" to z.alosHashachar, "sunrise" to z.sunrise, "shma" to z.sofZmanShmaGRA, "tfila" to z.sofZmanTfilaGRA,
            "chatzos" to z.chatzos, "mincha" to z.minchaGedola, "plag" to z.plagHamincha, "sunset" to z.sunset, "tzais" to z.tzais,
        )
    }

    fun monthName(month: Int, year: Int): String {
        val jd = JewishDate().apply { setJewishDate(year, month, 1) }
        return f().formatMonth(jd)
    }

    fun yearLetters(year: Int): String = f().formatHebrewNumber(year)
    fun dayLetters(day: Int): String = fLetters().formatHebrewNumber(day)

    fun isLeap(year: Int) = JewishDate().apply { setJewishDate(year, JewishDate.TISHREI, 1) }.isJewishLeapYear

    /** Months of a Hebrew year in reading order (Tishrei first); Adar I/II in leap years. */
    fun monthsOfYear(year: Int): List<Int> =
        if (isLeap(year)) listOf(7, 8, 9, 10, 11, 12, 13, 1, 2, 3, 4, 5, 6) else listOf(7, 8, 9, 10, 11, 12, 1, 2, 3, 4, 5, 6)

    fun daysInMonth(month: Int, year: Int): Int = JewishDate().apply { setJewishDate(year, month, 1) }.daysInJewishMonth

    /** Exact conversion; the day is clamped to the month's length (29/30). */
    fun toGregorian(day: Int, month: Int, year: Int): LocalDate {
        val m = if (!isLeap(year) && month == 13) 12 else month
        val jd = JewishDate().apply { setJewishDate(year, m, 1) }
        jd.setJewishDate(year, m, day.coerceIn(1, jd.daysInJewishMonth))
        val g = jd.gregorianCalendar
        return LocalDate.of(g.get(Calendar.YEAR), g.get(Calendar.MONTH) + 1, g.get(Calendar.DAY_OF_MONTH))
    }

    /** First day of the Hebrew month containing [d]. */
    fun hebMonthStart(d: LocalDate): LocalDate { val j = jc(d); return d.minusDays((j.jewishDayOfMonth - 1).toLong()) }

    /** Shifts a Hebrew month start by [n] months. */
    fun addHebMonths(start: LocalDate, n: Int): LocalDate {
        var s = hebMonthStart(start)
        repeat(kotlin.math.abs(n)) { s = if (n > 0) s.plusDays(daysInMonth(jc(s).jewishMonth, jc(s).jewishYear).toLong()) else hebMonthStart(s.minusDays(1)) }
        return s
    }

    fun hebMonthTitle(start: LocalDate): String { val j = jc(start); return monthName(j.jewishMonth, j.jewishYear) + " " + yearLetters(j.jewishYear) }

    /**
     * The date of a Hebrew-calendar anniversary in Hebrew year [year] (DAT-2): Adar in a leap year
     * falls in Adar II for birthdays (Adar I for yahrzeits of someone who died in a regular Adar is
     * a custom; we follow Adar II), 30 Cheshvan/Kislev falls back to the 29th / 1st when missing.
     */
    fun anniversary(day: Int, month: Int, year: Int, yahrzeit: Boolean = false): LocalDate {
        var m = month
        if (isLeap(year) && month == 12 && !yahrzeit) m = 13
        if (!isLeap(year) && month == 13) m = 12
        val dim = daysInMonth(m, year)
        return if (day == 30 && dim == 29) {
            if (yahrzeit) toGregorian(29, m, year) else toGregorian(1, m, year).plusDays(29)
        } else toGregorian(day, m, year)
    }
}
