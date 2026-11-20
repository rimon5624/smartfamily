package com.rimon.smartfamily.smart

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import com.rimon.smartfamily.MainActivity
import com.rimon.smartfamily.R
import com.rimon.smartfamily.SmartFamilyApp
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Every 15 minutes: chore reminders, family-date reminders (at 9:00) and event reminders.
 * TOG-4: nothing is shown between candle lighting and havdalah; held reminders appear after.
 */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val c = (applicationContext as SmartFamilyApp).container
        val s = c.settings.current()
        runCatching { com.rimon.smartfamily.widget.Widgets.refresh(applicationContext) }
        if (!s.choreReminders) return Result.success()
        val city = Cities.byKey(s.zmanimCity)
        if (runCatching { Zmanim.isHolyTime(city) }.getOrDefault(false)) return Result.success()
        val sent = applicationContext.getSharedPreferences("sf_reminders", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val seen = sent.getStringSet("keys", emptySet())!!.toMutableSet()
        val fresh = mutableListOf<Pair<String, String>>() // key to text

        // Chores due now (or overdue and never announced).
        c.homeLife.choresDueBefore(now).forEach { ch ->
            if (ch.assignee.isNotEmpty() && ch.assignee != s.userId && !ch.assignee.startsWith("child-")) return@forEach
            val key = "chore:${ch.id}:${ch.nextDue}"
            if (key !in seen) fresh += key to ch.title
        }

        // Family dates: on each reminder day, from 9:00.
        val hour = Calendar.getInstance(TimeZone.getTimeZone("Asia/Jerusalem")).get(Calendar.HOUR_OF_DAY)
        if (hour >= 9) {
            c.homeLife.allDatesForReminders().forEach { d ->
                val occ = c.homeLife.nextOccurrence(d) ?: return@forEach
                val days = d.remindDays.split(',').mapNotNull { it.trim().toIntOrNull() }
                if (occ.daysAway in days) {
                    val key = "date:${d.id}:${occ.on.time}:${occ.daysAway}"
                    if (key !in seen) {
                        val text = when (occ.daysAway) {
                            0 -> applicationContext.getString(R.string.rem_date_today, d.title)
                            1 -> applicationContext.getString(R.string.rem_date_tomorrow, d.title)
                            else -> applicationContext.getString(R.string.rem_date_in, d.title, occ.daysAway)
                        }
                        fresh += key to text
                    }
                }
            }
        }

        // Events: remindMinutes before the start.
        val events = c.homeLife.allEventsForReminders()
        c.homeLife.expand(events, now, now + 2 * 86_400_000L).forEach { o ->
            val at = o.start.time - o.event.remindMinutes * 60_000L
            if (o.event.remindMinutes >= 0 && now >= at && now < o.start.time) {
                val key = "event:${o.event.id}:${o.start.time}"
                if (key !in seen) fresh += key to o.event.title
            }
        }

        // 2.2: a SmartThings personal token lives 24 hours — remind 3 hours before it stops
        c.smartThings.tokenExpiresAt?.let { exp ->
            if (now in (exp - 3 * 3_600_000L) until exp) {
                val key = "st:$exp"
                if (key !in seen) fresh += key to applicationContext.getString(R.string.rem_st_expiring)
            }
        }

        // PCK-4: the evening before, what to pack tomorrow.
        if (hour >= 20) {
            val tomorrow = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(now + 86_400_000L))
            c.kitchen.packedOn(tomorrow).filter { it.status == "PLANNED" && it.items.isNotBlank() }.forEach { p ->
                val key = "packed:${p.id}:$tomorrow"
                if (key !in seen) {
                    val who = c.kitchen.profile(p.profileId)?.personName.orEmpty()
                    fresh += key to applicationContext.getString(R.string.rem_packed, who, p.items.lines().joinToString(", "))
                }
            }
        }

        // 2.0: bills and subscriptions the day before (and roll the next date forward once it passed)
        val today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Jerusalem"))
        if (hour >= 9) runCatching {
            c.db.families().all().filter { it.deletedAt == null }.forEach { fam ->
                c.db.recurringPayments().observeForFamily(fam.id).first().filter { it.active }.forEach { p ->
                    val next = runCatching { java.time.LocalDate.parse(p.nextDate) }.getOrNull() ?: return@forEach
                    if (next.isBefore(today)) {
                        val rolled = generateSequence(next) { d -> when (p.period) { "YEAR" -> d.plusYears(1); "BIMONTH" -> d.plusMonths(2); else -> d.plusMonths(1) } }.first { !it.isBefore(today) }
                        c.db.recurringPayments().upsert(p.copy(nextDate = rolled.toString(), updatedAt = now, dirty = true))
                    } else if (!next.isAfter(today.plusDays(1))) {
                        val key = "bill:${p.id}:${p.nextDate}"
                        if (key !in seen) fresh += key to applicationContext.getString(R.string.rem_bill, p.title, String.format("%.0f", p.amount))
                    }
                }
                // warranties and kit items expiring within 30 days (HBK-1, emergency kit)
                c.extras.expiring(30).filter { it.familyId == fam.id }.forEach { e ->
                    val key = "warranty:${e.id}:${e.expiresAt}"
                    if (key !in seen) fresh += key to applicationContext.getString(R.string.rem_warranty, e.title)
                }
                c.db.kitItems().observeForFamily(fam.id).first().filter { it.expiresAt.isNotBlank() }.forEach { k ->
                    val d = runCatching { java.time.LocalDate.parse(k.expiresAt) }.getOrNull() ?: return@forEach
                    if (!d.isAfter(today.plusDays(14))) {
                        val key = "kit:${k.id}:${k.expiresAt}"
                        if (key !in seen) fresh += key to applicationContext.getString(R.string.rem_kit, k.title)
                    }
                }
            }
        }

        // 2.0: a weekly summary on Saturday night after havdalah
        val dow = today.dayOfWeek
        if (dow == java.time.DayOfWeek.SATURDAY && hour >= 20) runCatching {
            val key = "weekly:$today"
            if (key !in seen) {
                val weekAgo = now - 7 * 86_400_000L
                val fam = c.db.families().all().firstOrNull { it.id == s.activeFamilyId } ?: c.db.families().all().firstOrNull()
                if (fam != null) {
                    val done = c.db.choreCompletions().observeForFamily(fam.id).first().count { it.doneAt >= weekAgo && it.approved }
                    val spent = c.db.budgetEntrys().observeForFamily(fam.id).first().filter { it.kind == "EXPENSE" && it.date >= weekAgo && !it.monthly }.sumOf { it.amount }
                    val nextWeek = c.homeLife.expand(events.filter { it.familyId == fam.id }, now, now + 7 * 86_400_000L).size
                    fresh += key to applicationContext.getString(R.string.rem_weekly, done, String.format("%.0f", spent), nextWeek)
                }
            }
        }

        if (fresh.isNotEmpty()) {
            show(applicationContext, fresh.map { it.second })
            seen += fresh.map { it.first }
        }
        // Keep the memory small: drop keys older than about two weeks (by count).
        val trimmed = if (seen.size > 400) seen.toList().takeLast(300).toSet() else seen
        sent.edit().putStringSet("keys", trimmed).apply()
        return Result.success()
    }

    companion object {
        private const val CHANNEL = "reminders"
        private const val WORK = "sf_reminders"

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<ReminderWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, req)
        }

        private fun show(context: Context, lines: List<String>) {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.notif_channel_reminders), NotificationManager.IMPORTANCE_DEFAULT))
            val open = PendingIntent.getActivity(context, 1, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val style = NotificationCompat.InboxStyle().also { st -> lines.take(7).forEach { st.addLine(it) } }
            val n = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(if (lines.size == 1) lines.first() else context.getString(R.string.rem_title, lines.size))
                .setContentText(lines.joinToString(" · "))
                .setStyle(style)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            runCatching { NotificationManagerCompat.from(context).notify((System.currentTimeMillis() % 100000).toInt() + 2000, n) }
        }
    }
}
