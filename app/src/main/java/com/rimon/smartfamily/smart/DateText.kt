package com.rimon.smartfamily.smart

import android.content.Context
import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

/**
 * 2.2: dates are stored as yyyy-MM-dd (a trailing "~" marks "after sunset" for births and deaths) and shown as the
 * user chose: Gregorian, Hebrew, both (default) or Hebrew with the Gregorian year.
 */
object DateText {
    private fun prefs(c: Context) = c.getSharedPreferences("sf_dates", Context.MODE_PRIVATE)
    /** "both" | "greg" | "heb" | "heb_greg" */
    fun mode(c: Context): String = prefs(c).getString("display", "both")!!
    fun setMode(c: Context, m: String) = prefs(c).edit().putString("display", m).apply()

    fun isAfterSunset(v: String) = v.endsWith("~")
    fun plain(v: String) = v.trimEnd('~')
    fun parse(v: String): LocalDate? = runCatching { LocalDate.parse(plain(v).take(10)) }.getOrNull()

    data class Heb(val day: Int, val month: Int, val year: Int)
    fun hebrew(d: LocalDate, afterSunset: Boolean = false): Heb {
        val date = Date.from((if (afterSunset) d.plusDays(1) else d).atTime(12, 0).atZone(ZoneId.of("Asia/Jerusalem")).toInstant())
        val jc = JewishCalendar(date)
        return Heb(jc.jewishDayOfMonth, jc.jewishMonth, jc.jewishYear)
    }
    fun hebrewText(d: LocalDate, afterSunset: Boolean = false, withYear: Boolean = true): String {
        val h = hebrew(d, afterSunset)
        return JewishDays.dayLetters(h.day) + " " + JewishDays.monthName(h.month, h.year) + if (withYear) " " + JewishDays.yearLetters(h.year) else ""
    }
    fun gregText(d: LocalDate) = "%02d/%02d/%d".format(d.dayOfMonth, d.monthValue, d.year)

    /** The text for a stored date, in the user's display mode. */
    fun format(c: Context, v: String): String {
        val d = parse(v) ?: return v
        val sun = isAfterSunset(v)
        return when (mode(c)) {
            "greg" -> gregText(d)
            "heb" -> hebrewText(d, sun)
            "heb_greg" -> hebrewText(d, sun, withYear = false) + " " + d.year
            else -> gregText(d) + " · " + hebrewText(d, sun, withYear = false)
        }
    }
}
