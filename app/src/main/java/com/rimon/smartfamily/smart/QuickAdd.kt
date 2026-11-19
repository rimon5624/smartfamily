package com.rimon.smartfamily.smart

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

/** What a free sentence probably is (2.0 quick add). The user can always change the guess. */
enum class QuickKind { SHOPPING, EVENT, CHORE, NOTE }

data class QuickGuess(val kind: QuickKind, val title: String, val date: LocalDate?, val time: LocalTime?)

/**
 * "לקנות חלב וביצים" → shopping; "ביום שלישי ב-16:00 רופא שיניים לצור" → event;
 * "לנקות את המזגן מחר" → chore; "הסיסמה של הראוטר…" → note. Hebrew and English.
 */
object QuickAdd {
    private val shopWords = listOf("לקנות", "קניות", "תקנו", "תקני", "תקנה", "חסר", "חסרים", "נגמר", "נגמרו", "buy", "get ", "shopping")
    private val choreWords = listOf("לנקות", "לסדר", "לתקן", "לשטוף", "להוציא", "לכבס", "לקפל", "משימה", "תזכיר", "תזכורת", "לזכור", "clean", "fix", "remind", "task")
    private val noteWords = listOf("הערה", "לשמור", "סיסמה", "קוד ", "note", "save ")
    private val weekdays = mapOf(
        "ראשון" to DayOfWeek.SUNDAY, "שני" to DayOfWeek.MONDAY, "שלישי" to DayOfWeek.TUESDAY, "רביעי" to DayOfWeek.WEDNESDAY,
        "חמישי" to DayOfWeek.THURSDAY, "שישי" to DayOfWeek.FRIDAY, "שבת" to DayOfWeek.SATURDAY,
        "sunday" to DayOfWeek.SUNDAY, "monday" to DayOfWeek.MONDAY, "tuesday" to DayOfWeek.TUESDAY, "wednesday" to DayOfWeek.WEDNESDAY,
        "thursday" to DayOfWeek.THURSDAY, "friday" to DayOfWeek.FRIDAY, "saturday" to DayOfWeek.SATURDAY,
    )

    fun guess(text: String, today: LocalDate = LocalDate.now(JewishDays.ZONE)): QuickGuess {
        var t = " " + text.trim() + " "
        val low = t.lowercase()
        var date: LocalDate? = null
        var time: LocalTime? = null

        Regex("(\\d{1,2})[:.](\\d{2})").find(t)?.let { m ->
            val h = m.groupValues[1].toInt(); val mi = m.groupValues[2].toInt()
            if (h in 0..23 && mi in 0..59) { time = LocalTime.of(h, mi); t = t.replace(m.value, " ") }
        }
        Regex("(\\d{1,2})/(\\d{1,2})(?:/(\\d{2,4}))?").find(t)?.let { m ->
            val d = m.groupValues[1].toInt(); val mo = m.groupValues[2].toInt()
            val y = m.groupValues[3].takeIf { it.isNotEmpty() }?.toInt()?.let { if (it < 100) 2000 + it else it } ?: today.year
            runCatching { LocalDate.of(y, mo, d) }.getOrNull()?.let { date = if (m.groupValues[3].isEmpty() && it.isBefore(today)) it.plusYears(1) else it; t = t.replace(m.value, " ") }
        }
        when {
            low.contains("מחרתיים") || low.contains("day after tomorrow") -> { date = today.plusDays(2); t = t.replace("מחרתיים", " ") }
            low.contains("מחר") || low.contains("tomorrow") -> { date = today.plusDays(1); t = t.replace("מחר", " ").replace("tomorrow", " ", ignoreCase = true) }
            low.contains("היום") || low.contains("today") -> { date = today; t = t.replace("היום", " ").replace("today", " ", ignoreCase = true) }
        }
        if (date == null) for ((name, dow) in weekdays) {
            val r = Regex("(?<=\\s)(ביום |יום |ב|on )?$name(?=[ ,.]|$)", RegexOption.IGNORE_CASE)
            r.find(t)?.let { m ->
                date = today.with(TemporalAdjusters.next(dow)).let { n -> if (today.dayOfWeek == dow) today else n }
                t = t.replace(m.value, " ")
            }
            if (date != null) break
        }
        HebrewDateSearch.parse(text)?.let { r -> if (date == null) date = r.date }
        t = t.replace(Regex("\\s(ב-|בשעה|ב־|at)\\s"), " ").replace(Regex("\\s+"), " ").trim()

        val kind = when {
            shopWords.any { low.contains(it) } -> QuickKind.SHOPPING
            choreWords.any { low.contains(it) } -> QuickKind.CHORE
            noteWords.any { low.contains(it) } -> QuickKind.NOTE
            date != null || time != null -> QuickKind.EVENT
            t.split(' ').size <= 4 -> QuickKind.SHOPPING
            else -> QuickKind.NOTE
        }
        var title = t
        if (kind == QuickKind.SHOPPING) shopWords.forEach { title = title.replace(it, "", ignoreCase = true) }
        title = title.replace(Regex("\\s+"), " ").trim().trimStart(':', ',', '-', ' ')
        return QuickGuess(kind, title.ifBlank { text.trim() }, date, time)
    }
}
