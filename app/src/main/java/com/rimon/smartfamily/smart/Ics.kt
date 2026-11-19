package com.rimon.smartfamily.smart

import com.rimon.smartfamily.data.db.EventEntity
import com.rimon.smartfamily.data.db.FamilyDateEntity
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/**
 * Exchange with the phone's calendar (Google Calendar, Samsung, Outlook) through standard .ics files —
 * no calendar permission, works offline. Hebrew recurrences are expanded into dated events for three years.
 */
object Ics {
    private fun utc() = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
    private fun day() = SimpleDateFormat("yyyyMMdd", Locale.US).apply { timeZone = TimeZone.getTimeZone("Asia/Jerusalem") }
    private fun esc(s: String) = s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\n", "\\n")
    private fun unesc(s: String) = s.replace("\\n", "\n").replace("\\N", "\n").replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\")

    fun export(events: List<EventEntity>, expandHebrew: (EventEntity) -> List<Pair<Long, Long>>, dates: List<Pair<FamilyDateEntity, List<java.time.LocalDate>>>): String {
        val sb = StringBuilder("BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//SmartFamily//HE\r\nCALSCALE:GREGORIAN\r\n")
        fun vevent(uid: String, title: String, start: Long, end: Long, allDay: Boolean, rrule: String?, loc: String, note: String) {
            sb.append("BEGIN:VEVENT\r\nUID:").append(uid).append("@smartfamily\r\nDTSTAMP:").append(utc().format(System.currentTimeMillis())).append("\r\n")
            if (allDay) {
                sb.append("DTSTART;VALUE=DATE:").append(day().format(start)).append("\r\n")
                sb.append("DTEND;VALUE=DATE:").append(day().format(maxOf(end, start) + 86_400_000L)).append("\r\n")
            } else {
                sb.append("DTSTART:").append(utc().format(start)).append("\r\nDTEND:").append(utc().format(maxOf(end, start + 60_000))).append("\r\n")
            }
            sb.append("SUMMARY:").append(esc(title)).append("\r\n")
            if (loc.isNotBlank()) sb.append("LOCATION:").append(esc(loc)).append("\r\n")
            if (note.isNotBlank()) sb.append("DESCRIPTION:").append(esc(note)).append("\r\n")
            rrule?.let { sb.append("RRULE:").append(it).append("\r\n") }
            sb.append("END:VEVENT\r\n")
        }
        events.filter { it.deletedAt == null }.forEach { e ->
            when (e.recurrence) {
                "YEARLY_HEBREW", "MONTHLY_HEBREW" -> expandHebrew(e).forEachIndexed { i, (s, en) -> vevent("${e.id}-$i", e.title, s, en, e.allDay, null, e.location, e.note) }
                else -> vevent(e.id, e.title, e.startAt, e.endAt, e.allDay, when (e.recurrence) { "DAILY" -> "FREQ=DAILY"; "WEEKLY" -> "FREQ=WEEKLY"; "MONTHLY" -> "FREQ=MONTHLY"; "YEARLY" -> "FREQ=YEARLY"; else -> null }, e.location, e.note)
            }
        }
        dates.forEach { (d, days) ->
            days.forEachIndexed { i, ld ->
                val ms = ld.atStartOfDay(java.time.ZoneId.of("Asia/Jerusalem")).toInstant().toEpochMilli()
                vevent("${d.id}-$i", d.title, ms, ms, true, null, "", "")
            }
        }
        return sb.append("END:VCALENDAR\r\n").toString()
    }

    data class Parsed(val title: String, val start: Long, val end: Long, val allDay: Boolean, val location: String, val note: String, val recurrence: String)

    fun parse(text: String): List<Parsed> {
        // unfold continuation lines
        val lines = text.replace("\r\n ", "").replace("\n ", "").replace("\r\n\t", "").lines()
        val out = mutableListOf<Parsed>()
        var cur: MutableMap<String, String>? = null
        for (raw in lines) {
            val line = raw.trimEnd('\r')
            when {
                line == "BEGIN:VEVENT" -> cur = mutableMapOf()
                line == "END:VEVENT" -> { cur?.let { toParsed(it)?.let(out::add) }; cur = null }
                cur != null && ':' in line -> {
                    val key = line.substringBefore(':'); val value = line.substringAfter(':')
                    val name = key.substringBefore(';')
                    cur[name] = value
                    if (name == "DTSTART" || name == "DTEND") cur["$name#p"] = key
                }
            }
        }
        return out
    }

    private fun time(value: String, params: String): Pair<Long, Boolean>? = runCatching {
        if (params.contains("VALUE=DATE") || value.length == 8) {
            day().parse(value.take(8))!!.time to true
        } else if (value.endsWith("Z")) {
            utc().parse(value)!!.time to false
        } else {
            val tz = Regex("TZID=([^;:]+)").find(params)?.groupValues?.get(1) ?: "Asia/Jerusalem"
            SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.US).apply { timeZone = TimeZone.getTimeZone(tz) }.parse(value.take(15))!!.time to false
        }
    }.getOrNull()

    private fun toParsed(m: Map<String, String>): Parsed? {
        val (start, allDay) = time(m["DTSTART"] ?: return null, m["DTSTART#p"].orEmpty()) ?: return null
        var end = m["DTEND"]?.let { time(it, m["DTEND#p"].orEmpty())?.first } ?: start
        if (allDay && end > start) end -= 86_400_000L
        val rr = m["RRULE"].orEmpty()
        val rec = when {
            "FREQ=DAILY" in rr -> "DAILY"; "FREQ=WEEKLY" in rr -> "WEEKLY"; "FREQ=MONTHLY" in rr -> "MONTHLY"; "FREQ=YEARLY" in rr -> "YEARLY"; else -> "NONE"
        }
        return Parsed(unesc(m["SUMMARY"].orEmpty()).ifBlank { "—" }, start, end, allDay, unesc(m["LOCATION"].orEmpty()), unesc(m["DESCRIPTION"].orEmpty()), rec)
    }

    fun toEntity(p: Parsed, familyId: String, owner: String) = EventEntity(
        id = UUID.randomUUID().toString(), familyId = familyId, title = p.title, startAt = p.start, endAt = p.end, allDay = p.allDay,
        recurrence = p.recurrence, location = p.location, note = p.note, ownerId = owner, source = "ics",
    )
}
