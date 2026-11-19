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
import java.util.concurrent.TimeUnit

/** INV-4: once a day, a single notification listing food that expires within 3 days. */
class ExpiryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val c = (applicationContext as SmartFamilyApp).container
        if (!c.settings.current().expiryAlerts) return Result.success()
        val endOfWindow = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 3)
            set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59)
        }.timeInMillis
        val items = c.db.inventory().expiringBefore(endOfWindow).sortedBy { it.expiresAt }
        if (items.isEmpty()) return Result.success()
        notify(applicationContext, items.map { it.name })
        return Result.success()
    }

    companion object {
        private const val CHANNEL = "expiry"
        private const val WORK = "sf_expiry_daily"

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<ExpiryWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, req)
        }

        private fun notify(context: Context, names: List<String>) {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(R.string.notif_channel_expiry), NotificationManager.IMPORTANCE_DEFAULT)
            )
            val open = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val text = names.take(6).joinToString(", ") + if (names.size > 6) "…" else ""
            val n = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.notif_expiry_title, names.size))
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            runCatching { NotificationManagerCompat.from(context).notify(1001, n) }
        }
    }
}
