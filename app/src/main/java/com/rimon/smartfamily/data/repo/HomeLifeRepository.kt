package com.rimon.smartfamily.data.repo

import androidx.room.withTransaction
import com.rimon.smartfamily.data.db.AppDatabase
import com.rimon.smartfamily.data.db.ChoreCompletionEntity
import com.rimon.smartfamily.data.db.ChoreEntity
import com.rimon.smartfamily.data.db.EventEntity
import com.rimon.smartfamily.data.db.FamilyDateEntity
import com.rimon.smartfamily.data.db.PointsEntryEntity
import com.rimon.smartfamily.data.db.RewardEntity
import com.rimon.smartfamily.smart.Zmanim
import com.rimon.smartfamily.smart.ZmanimCity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

enum class Schedule { DAILY, WEEKLY, EVERY_N_DAYS, BEFORE_SHABBAT, ONCE }

/** A built-in chore (spec section 12); [titleKey] picks the localised title. */
data class ChoreTemplate(
    val titleKey: String,
    val schedule: Schedule,
    val intervalDays: Int = 7,
    val weekdays: Int = 0,
    val hoursBeforeShabbat: Int = 0,
    val minutes: Int = 15,
    val category: String = "home",
)

/** One upcoming occurrence of a family date. */
data class DateOccurrence(val date: FamilyDateEntity, val on: Date, val years: Int?, val daysAway: Int, val hebrew: Boolean = false)

/** One occurrence of a calendar event (recurrence expanded). */
data class EventOccurrence(val event: EventEntity, val start: Date, val end: Date)

class HomeLifeRepository(
    private val db: AppDatabase,
    private val onLocalChange: () -> Unit,
) {
    private val tz: TimeZone = TimeZone.getTimeZone("Asia/Jerusalem")
    private fun now() = System.currentTimeMillis()
    private fun uuid() = UUID.randomUUID().toString()
    private fun cal(ms: Long = now()) = Calendar.getInstance(tz).apply { timeInMillis = ms }

    // ---------------- chores ----------------

    fun observeChores(familyId: String) = db.chores().observeActive(familyId)
    fun observeCompletionsSince(familyId: String, since: Long) = db.choreCompletions().observeSince(familyId, since)
    fun observePendingApprovals(familyId: String) = db.choreCompletions().observePending(familyId)
    fun observeBalances(familyId: String) = db.pointsEntrys().observeBalances(familyId)
    fun observeRewards(familyId: String) = db.rewards().observeForFamily(familyId)

    /** CHR-1: when a chore is next due, after [from]. */
    fun nextDue(c: ChoreEntity, from: Long, city: ZmanimCity): Long {
        val schedule = runCatching { Schedule.valueOf(c.schedule) }.getOrDefault(Schedule.WEEKLY)
        fun atTime(k: Calendar) = k.apply {
            set(Calendar.HOUR_OF_DAY, c.timeOfDay / 60); set(Calendar.MINUTE, c.timeOfDay % 60)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        return when (schedule) {
            Schedule.ONCE -> if (c.lastDoneAt == null) (if (c.nextDue > 0) c.nextDue else from) else 0L
            Schedule.DAILY -> {
                val k = atTime(cal(from))
                if (k.timeInMillis <= from) k.add(Calendar.DAY_OF_YEAR, 1)
                k.timeInMillis
            }
            Schedule.EVERY_N_DAYS -> atTime(cal(from).apply { add(Calendar.DAY_OF_YEAR, c.intervalDays.coerceAtLeast(1)) }).timeInMillis
            Schedule.WEEKLY -> {
                if (c.weekdays == 0) return atTime(cal(from).apply { add(Calendar.DAY_OF_YEAR, 7) }).timeInMillis
                val k = atTime(cal(from))
                repeat(8) {
                    val bit = 1 shl (k.get(Calendar.DAY_OF_WEEK) - 1)
                    if (c.weekdays and bit != 0 && k.timeInMillis > from) return k.timeInMillis
                    k.add(Calendar.DAY_OF_YEAR, 1)
                }
                k.timeInMillis
            }
            Schedule.BEFORE_SHABBAT -> {
                var at = cal(from)
                repeat(3) {
                    val s = Zmanim.nextShabbat(city, at)
                    val due = s.candleLighting.time - c.hoursBeforeShabbat * 3_600_000L
                    if (due > from) return due
                    at = cal(s.havdalah.time + 60_000)
                }
                from + 7 * 86_400_000L
            }
        }
    }

    suspend fun saveChore(c: ChoreEntity, city: ZmanimCity) {
        val withDue = if (c.nextDue == 0L || c.schedule == Schedule.BEFORE_SHABBAT.name) c.copy(nextDue = nextDue(c, now(), city)) else c
        db.chores().upsert(withDue.copy(updatedAt = now(), dirty = true))
        onLocalChange()
    }

    suspend fun newChore(familyId: String, title: String, userId: String, schedule: Schedule, city: ZmanimCity, base: ChoreEntity? = null): ChoreEntity {
        val c = (base ?: ChoreEntity(id = uuid(), familyId = familyId, title = title, createdBy = userId))
            .copy(id = uuid(), familyId = familyId, title = title, schedule = schedule.name, createdBy = userId, nextDue = 0L)
        saveChore(c, city)
        return c
    }

    suspend fun addTemplates(familyId: String, userId: String, templates: List<Pair<ChoreTemplate, String>>, city: ZmanimCity) {
        db.withTransaction {
            templates.forEach { (t, title) ->
                val c = ChoreEntity(
                    id = uuid(), familyId = familyId, title = title, category = t.category, schedule = t.schedule.name,
                    intervalDays = t.intervalDays, weekdays = t.weekdays, hoursBeforeShabbat = t.hoursBeforeShabbat,
                    estimatedMinutes = t.minutes, createdBy = userId,
                )
                db.chores().upsert(c.copy(nextDue = nextDue(c, now(), city)))
            }
        }
        onLocalChange()
    }

    /**
     * Marks a chore done (CHR-3, CHR-7). A child's points wait for a parent's approval; rotation
     * passes the chore to the next person.
     */
    suspend fun complete(choreId: String, doneBy: String, needsApproval: Boolean, city: ZmanimCity) {
        val c = db.chores().get(choreId) ?: return
        db.withTransaction {
            val approved = !needsApproval || c.points == 0
            db.choreCompletions().upsert(
                ChoreCompletionEntity(
                    id = uuid(), familyId = c.familyId, choreId = c.id, title = c.title, doneBy = doneBy,
                    doneAt = now(), points = c.points, approved = approved, approvedBy = if (approved) doneBy else null,
                )
            )
            if (approved && c.points > 0) addPoints(c.familyId, doneBy, c.points, c.title)
            val rotation = c.rotation.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            val nextAssignee = if (rotation.size > 1) rotation[(rotation.indexOf(c.assignee) + 1).mod(rotation.size)] else c.assignee
            val done = c.copy(lastDoneAt = now(), lastDoneBy = doneBy, assignee = nextAssignee)
            val due = nextDue(done, now(), city)
            db.chores().upsert(done.copy(nextDue = due, active = due != 0L || c.schedule != Schedule.ONCE.name, updatedAt = now(), dirty = true))
        }
        onLocalChange()
    }

    suspend fun snooze(choreId: String, hours: Int) {
        val c = db.chores().get(choreId) ?: return
        db.chores().upsert(c.copy(nextDue = now() + hours * 3_600_000L, updatedAt = now(), dirty = true))
        onLocalChange()
    }

    suspend fun deleteChore(choreId: String) {
        val c = db.chores().get(choreId) ?: return
        db.chores().upsert(c.copy(deletedAt = now(), updatedAt = now(), dirty = true))
        onLocalChange()
    }

    suspend fun approveCompletion(id: String, parentId: String) {
        val comp = db.choreCompletions().get(id) ?: return
        db.withTransaction {
            db.choreCompletions().upsert(comp.copy(approved = true, approvedBy = parentId, updatedAt = now(), dirty = true))
            if (comp.points > 0) addPoints(comp.familyId, comp.doneBy, comp.points, comp.title)
        }
        onLocalChange()
    }

    suspend fun rejectCompletion(id: String) {
        val comp = db.choreCompletions().get(id) ?: return
        db.choreCompletions().upsert(comp.copy(deletedAt = now(), updatedAt = now(), dirty = true))
        onLocalChange()
    }

    private suspend fun addPoints(familyId: String, userId: String, delta: Int, reason: String) {
        db.pointsEntrys().upsert(PointsEntryEntity(id = uuid(), familyId = familyId, userId = userId, delta = delta, reason = reason, createdAt = now()))
    }

    suspend fun saveReward(r: RewardEntity) {
        db.rewards().upsert(r.copy(updatedAt = now(), dirty = true))
        onLocalChange()
    }

    suspend fun deleteReward(id: String) {
        val r = db.rewards().get(id) ?: return
        db.rewards().upsert(r.copy(deletedAt = now(), updatedAt = now(), dirty = true))
        onLocalChange()
    }

    suspend fun redeem(familyId: String, userId: String, reward: RewardEntity) {
        addPoints(familyId, userId, -reward.cost, reward.title)
        onLocalChange()
    }

    // ---------------- family dates ----------------

    fun observeDates(familyId: String) = db.familyDates().observeForFamily(familyId)

    suspend fun saveDate(d: FamilyDateEntity) {
        // Fill the other calendar so both are always shown (DAT-2).
        val filled = if (d.gregorian.isNotBlank() && d.hebrewDay == 0) {
            val g = ymd.parse(d.gregorian)
            if (g != null) {
                val h = Zmanim.toHebrew(g, d.afterSunset)
                d.copy(hebrewDay = h.day, hebrewMonth = h.month, hebrewYear = h.year)
            } else d
        } else if (d.gregorian.isBlank() && d.hebrewDay > 0 && d.hebrewYear > 0) {
            d.copy(gregorian = ymd.format(Zmanim.fromHebrew(d.hebrewDay, d.hebrewMonth, d.hebrewYear)))
        } else d
        db.familyDates().upsert(filled.copy(updatedAt = now(), dirty = true))
        onLocalChange()
    }

    suspend fun deleteDate(id: String) {
        val d = db.familyDates().get(id) ?: return
        db.familyDates().upsert(d.copy(deletedAt = now(), updatedAt = now(), dirty = true))
        onLocalChange()
    }

    private val ymd = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = tz }

    /** The next yearly occurrence on or after today, by the calendar the date follows. */
    /** 2.2: drive "BOTH" — the next Gregorian and the next Hebrew occurrence (one when they fall on the same day). */
    fun nextOccurrences(d: FamilyDateEntity, today: Calendar = cal()): List<DateOccurrence> {
        if (d.drive != "BOTH" || d.hebrewDay <= 0 || d.gregorian.isBlank()) return listOfNotNull(nextOccurrence(d, today))
        val g = nextOccurrence(d.copy(drive = "GREGORIAN"), today)
        val h = nextOccurrence(d.copy(drive = "HEBREW"), today)?.copy(hebrew = true)
        return listOfNotNull(g, h).distinctBy { it.daysAway }.map { it.copy(date = d) }
    }

    fun nextOccurrence(d: FamilyDateEntity, today: Calendar = cal()): DateOccurrence? {
        if (d.drive == "BOTH") return nextOccurrences(d, today).minByOrNull { it.daysAway }
        val start = (today.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val on: Date
        val years: Int?
        if (d.drive == "HEBREW" && d.hebrewDay > 0) {
            var y = Zmanim.currentHebrewYear()
            var candidate = Zmanim.fromHebrew(d.hebrewDay, d.hebrewMonth, y)
            if (candidate.time < start.timeInMillis) { y++; candidate = Zmanim.fromHebrew(d.hebrewDay, d.hebrewMonth, y) }
            on = candidate
            years = if (d.hebrewYear > 0) y - d.hebrewYear else null
        } else {
            val g = d.gregorian.takeIf { it.isNotBlank() }?.let { ymd.parse(it) } ?: return null
            val orig = cal(g.time)
            val k = (start.clone() as Calendar).apply {
                set(Calendar.MONTH, orig.get(Calendar.MONTH))
                set(Calendar.DAY_OF_MONTH, orig.get(Calendar.DAY_OF_MONTH).coerceAtMost(getActualMaximum(Calendar.DAY_OF_MONTH)))
            }
            if (k.timeInMillis < start.timeInMillis) k.add(Calendar.YEAR, 1)
            on = k.time
            years = k.get(Calendar.YEAR) - orig.get(Calendar.YEAR)
        }
        val days = ((on.time - start.timeInMillis) / 86_400_000L).toInt()
        return DateOccurrence(d, on, years?.takeIf { it >= 0 }, days)
    }

    suspend fun allDatesForReminders() = db.familyDates().allActive()

    /** Every yearly occurrence of a family date between two days (both calendars, DAT-2). */
    fun occurrencesBetween(d: FamilyDateEntity, from: java.time.LocalDate, to: java.time.LocalDate): List<Pair<java.time.LocalDate, Int?>> {
        if (d.drive == "BOTH") return (occurrencesBetween(d.copy(drive = "GREGORIAN"), from, to) + occurrencesBetween(d.copy(drive = "HEBREW"), from, to)).distinctBy { it.first }.sortedBy { it.first }
        val z = com.rimon.smartfamily.smart.JewishDays
        val out = mutableListOf<Pair<java.time.LocalDate, Int?>>()
        if (d.drive == "HEBREW" && d.hebrewDay > 0 && d.hebrewMonth > 0) {
            val y0 = com.kosherjava.zmanim.hebrewcalendar.JewishCalendar(z.noon(from)).jewishYear
            val y1 = com.kosherjava.zmanim.hebrewcalendar.JewishCalendar(z.noon(to)).jewishYear
            for (y in y0..y1) {
                if (d.hebrewYear > 0 && y < d.hebrewYear) continue
                val on = z.anniversary(d.hebrewDay, d.hebrewMonth, y, yahrzeit = d.type == "YAHRZEIT")
                if (!on.isBefore(from) && !on.isAfter(to)) out += on to (if (d.hebrewYear > 0) y - d.hebrewYear else null)
            }
        } else {
            val g = d.gregorian.takeIf { it.isNotBlank() }?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() } ?: return out
            for (y in from.year..to.year) {
                if (y < g.year) continue
                val on = runCatching { g.withYear(y) }.getOrNull() ?: java.time.LocalDate.of(y, 2, 28)
                if (!on.isBefore(from) && !on.isAfter(to)) out += on to (y - g.year)
            }
        }
        return out
    }

    // ---------------- events ----------------

    fun observeEvents(familyId: String) = db.events().observeForFamily(familyId)

    suspend fun saveEvent(e: EventEntity) {
        db.events().upsert(e.copy(updatedAt = now(), dirty = true))
        onLocalChange()
    }

    suspend fun deleteEvent(id: String) {
        val e = db.events().get(id) ?: return
        db.events().upsert(e.copy(deletedAt = now(), updatedAt = now(), dirty = true))
        onLocalChange()
    }

    /** Occurrences of the events between [from] and [to], with weekly/monthly/yearly repeats expanded. */
    fun expand(events: List<EventEntity>, from: Long, to: Long): List<EventOccurrence> {
        val out = mutableListOf<EventOccurrence>()
        events.forEach { e ->
            val length = (e.endAt - e.startAt).coerceAtLeast(0)
            if (e.recurrence == "YEARLY_HEBREW" || e.recurrence == "MONTHLY_HEBREW") {
                // Repeats by the Hebrew date of its first occurrence (e.g. every year on 15 Shevat).
                val z = com.rimon.smartfamily.smart.JewishDays
                val first = z.toLocal(e.startAt)
                val timeOfDay = e.startAt - z.startMs(first)
                val h = com.kosherjava.zmanim.hebrewcalendar.JewishCalendar(z.noon(first))
                val fromD = z.toLocal(from); val toD = z.toLocal(to)
                val yFrom = com.kosherjava.zmanim.hebrewcalendar.JewishCalendar(z.noon(fromD)).jewishYear
                val yTo = com.kosherjava.zmanim.hebrewcalendar.JewishCalendar(z.noon(toD)).jewishYear
                for (y in maxOf(yFrom, h.jewishYear)..yTo) {
                    val months = if (e.recurrence == "YEARLY_HEBREW") listOf(h.jewishMonth) else z.monthsOfYear(y)
                    months.forEach { m ->
                        val d = if (e.recurrence == "YEARLY_HEBREW") z.anniversary(h.jewishDayOfMonth, m, y) else z.toGregorian(h.jewishDayOfMonth, m, y)
                        val s = z.startMs(d) + timeOfDay
                        if (s >= e.startAt && s + length >= from && s <= to) out += EventOccurrence(e, Date(s), Date(s + length))
                    }
                }
                return@forEach
            }
            val field = when (e.recurrence) {
                "WEEKLY" -> Calendar.WEEK_OF_YEAR
                "MONTHLY" -> Calendar.MONTH
                "YEARLY" -> Calendar.YEAR
                else -> null
            }
            if (field == null) {
                if (e.endAt >= from && e.startAt <= to) out += EventOccurrence(e, Date(e.startAt), Date(e.endAt))
            } else {
                val k = cal(e.startAt)
                var guard = 0
                while (k.timeInMillis <= to && guard++ < 600) {
                    if (k.timeInMillis + length >= from) out += EventOccurrence(e, k.time, Date(k.timeInMillis + length))
                    k.add(field, 1)
                }
            }
        }
        return out.sortedBy { it.start }
    }

    suspend fun allEventsForReminders() = db.events().allActive()
    suspend fun choresDueBefore(until: Long) = db.chores().dueBefore(until)
}
