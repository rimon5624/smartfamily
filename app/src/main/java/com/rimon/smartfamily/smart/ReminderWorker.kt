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
