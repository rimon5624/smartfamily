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
import com.rimon.smartfamily.AppContainer
import com.rimon.smartfamily.MainActivity
import com.rimon.smartfamily.R

/**
 * After each sync: shares chat keys with members' new devices and shows a notification for
 * new messages (decrypted on the phone, CHT-5). Messages arrive within seconds while a chat is
 * open, and with the background sync (about every 15 minutes) otherwise.
 */
object ChatNotifier {
    @Volatile var openChatId: String? = null
    private const val CHANNEL = "chat"

    suspend fun afterSync(context: Context, c: AppContainer) {
        val s = c.settings.current()
        if (s.userId.isBlank()) return
        runCatching {
            c.db.chats().allActive().filter { s.userId in it.members.split(',') }.forEach { c.chat.shareKeys(it) }
        }
        // CHT-3 scheduled messages written on this phone go out when their time comes
        runCatching {
            c.db.scheduledMessages().due(System.currentTimeMillis()).forEach { sm ->
                val chat = c.chat.chat(sm.chatId)
                if (chat != null && c.chat.hasKey(chat)) {
                    c.chat.send(chat, s.userId, com.rimon.smartfamily.data.repo.MessagePayload(text = sm.text), "text")
                    c.db.scheduledMessages().delete(sm.id)
                }
            }
        }
        val prefs = context.getSharedPreferences("sf_chat", Context.MODE_PRIVATE)
        val since = prefs.getLong("last_notified", System.currentTimeMillis() - 60 * 60 * 1000)
        val incoming = c.chat.incomingSince(since, s.userId).filter { it.chatId != openChatId }
        if (incoming.isEmpty()) return
        prefs.edit().putLong("last_notified", incoming.maxOf { it.sentAt }).apply()
        if (Zmanim.isHolyTime(Cities.byKey(s.zmanimCity))) return
        val names = c.db.members().all().associate { it.userId to it.displayName }
        val lines = incoming.takeLast(6).map { m ->
            val p = c.chat.decrypt(m)
            val text = when {
                p == null -> context.getString(R.string.chat_encrypted)
                m.kind == "voice" -> "🎤 " + context.getString(R.string.chat_voice)
                m.kind == "image" -> "📷 " + context.getString(R.string.chat_photo)
                m.kind == "file" -> "📎 " + p.mediaName
                m.kind == "card" -> "🃏 " + p.cardTitle
                m.kind == "location" -> "📍 " + p.text
                else -> p.text
            }
            "${names[m.senderId] ?: "?"}: $text"
        }
        show(context, lines, incoming.size)
    }

    private fun show(context: Context, lines: List<String>, count: Int) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.chat_title), NotificationManager.IMPORTANCE_HIGH))
        val open = PendingIntent.getActivity(context, 3, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val style = NotificationCompat.InboxStyle().also { st -> lines.forEach { st.addLine(it) } }
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (count == 1) lines.first().substringBefore(':') else context.getString(R.string.chat_new_messages, count))
            .setContentText(lines.last())
            .setStyle(style)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(3001, n) }
    }
}
