package com.rimon.smartfamily.sync

import android.content.Context
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.rimon.smartfamily.SmartFamilyApp
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

/**
 * Instant chat messages (2.1.2) through Firebase Cloud Messaging, free.
 * The server sends only "chat X has something new" — never the text. This phone then syncs the chat
 * tables, decrypts on the phone and shows the usual notification, so end-to-end encryption is kept.
 */
class PushService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        runBlocking { withTimeoutOrNull(15_000) { Push.register(applicationContext, token) } }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        if (message.data["type"] != "chat") return
        val app = applicationContext as SmartFamilyApp
        runBlocking {
            withTimeoutOrNull(18_000) {
                app.container.sync.syncTables(setOf("chats", "chat_keys", "chat_messages", "chat_reactions", "chat_reads"))
                runCatching { com.rimon.smartfamily.smart.ChatNotifier.afterSync(applicationContext, app.container) }
            }
        }
    }
}

object Push {
    /** True when this build includes the Firebase setup (google-services.json was present at build time). */
    fun available(context: Context): Boolean = runCatching { com.google.firebase.FirebaseApp.getInstance() }.isSuccess ||
        runCatching { com.google.firebase.FirebaseApp.initializeApp(context) != null }.getOrDefault(false)

    /** Publishes this phone's push address for my account (only when it changed). Safe to call often. */
    suspend fun register(context: Context, known: String? = null) {
        if (!available(context)) return
        val c = (context.applicationContext as SmartFamilyApp).container
        val s = c.settings.current()
        if (s.userId.isBlank() || !c.api.isSignedIn) return
        val token = known ?: runCatching { FirebaseMessaging.getInstance().token.await() }.getOrNull() ?: return
        val prefs = context.getSharedPreferences("sf_push", Context.MODE_PRIVATE)
        val stamp = "${s.userId}|$token"
        if (prefs.getString("sent", null) == stamp) return
        val row = buildJsonArray {
            add(buildJsonObject {
                put("id", JsonPrimitive(c.chatCrypto.deviceId))
                put("user_id", JsonPrimitive(s.userId))
                put("token", JsonPrimitive(token))
                put("deleted_at", kotlinx.serialization.json.JsonNull)
            })
        }
        runCatching { c.api.upsert("push_tokens", row.toString(), "id") }.onSuccess { prefs.edit().putString("sent", stamp).apply() }
    }
}
