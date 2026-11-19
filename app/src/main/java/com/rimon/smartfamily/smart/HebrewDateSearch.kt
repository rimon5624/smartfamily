package com.rimon.smartfamily.smart

import java.time.LocalDate

/**
 * Free-text Hebrew date search: "ט״ו בשבט תשפ״ז", "15 שבט 5787", "כ אדר ב", "ל חשון".
 * Returns the Gregorian date, or null when the text is not a Hebrew date.
 */
object HebrewDateSearch {
    private val letters = mapOf(
        'א' to 1, 'ב' to 2, 'ג' to 3, 'ד' to 4, 'ה' to 5, 'ו' to 6, 'ז' to 7, 'ח' to 8, 'ט' to 9,
        'י' to 10, 'כ' to 20, 'ך' to 20, 'ל' to 30, 'מ' to 40, 'ם' to 40, 'נ' to 50, 'ן' to 50, 'ס' to 60, 'ע' to 70,
        'פ' to 80, 'ף' to 80, 'צ' to 90, 'ץ' to 90, 'ק' to 100, 'ר' to 200, 'ש' to 300, 'ת' to 400,
    )

    /** KosherJava month numbers: 1 = Nisan … 7 = Tishrei … 12 = Adar (Adar I in a leap year), 13 = Adar II. */
    private val months = listOf(
        listOf("תשרי") to 7, listOf("חשון", "חשוון", "מרחשון", "מרחשוון", "מר חשון") to 8, listOf("כסלו", "כסליו") to 9,
        listOf("טבת") to 10, listOf("שבט") to 11, listOf("אדר א", "אדר ראשון", "אדרא") to 12, listOf("אדר ב", "אדר שני", "אדרב") to 13,
        listOf("אדר") to 12, listOf("ניסן") to 1, listOf("אייר", "איר") to 2, listOf("סיון", "סיוון") to 3, listOf("תמוז") to 4,
        listOf("אב", "מנחם אב") to 5, listOf("אלול") to 6,
    )

    fun gematria(s: String): Int? {
        val clean = s.filter { it in letters }
        if (clean.isEmpty()) return null
        return clean.sumOf { letters[it] ?: 0 }
    }

    private fun norm(s: String) = s.replace(Regex("[״\"׳'`.,]"), "").replace(Regex("\\s+"), " ").trim()

    fun parse(input: String, defaultYear: Int = JewishDays.run { com.kosherjava.zmanim.hebrewcalendar.JewishCalendar(noon(LocalDate.now(ZONE))).jewishYear }): Result? {
        var t = " " + norm(input) + " "
        // month (longest names first so "אדר ב" wins over "אדר")
        var month: Int? = null
        for ((names, m) in months.sortedByDescending { p -> p.first.maxOf { it.length } }) {
            val hit = names.sortedByDescending { it.length }.firstOrNull { n -> Regex("[ ב]" + Regex.escape(n) + "(?=[ ]|$)").containsMatchIn(t) }
            if (hit != null) {
                month = m
                t = t.replaceFirst(Regex("[ ב]" + Regex.escape(hit) + "(?=[ ]|$)"), " ")
                break
            }
        }
        if (month == null) return null
        val parts = t.trim().split(' ').filter { it.isNotBlank() }
        if (parts.isEmpty()) return null
        val day = parts[0].toIntOrNull() ?: gematria(parts[0]) ?: return null
        if (day !in 1..30) return null
        var year = defaultYear
        parts.getOrNull(1)?.let { y ->
            year = y.toIntOrNull()?.let { if (it < 1000) it + 5000 else it } ?: gematria(y)?.let { g -> if (g < 1000) g + 5000 else g } ?: defaultYear
        }
        if (year !in 3000..6500) return null
        val m = if (month == 13 && !JewishDays.isLeap(year)) 12 else month
        val dim = JewishDays.daysInMonth(m, year)
        return Result(JewishDays.toGregorian(day.coerceAtMost(dim), m, year), day, m, year, adjusted = day > dim)
    }

    data class Result(val date: LocalDate, val day: Int, val month: Int, val year: Int, val adjusted: Boolean)
}
