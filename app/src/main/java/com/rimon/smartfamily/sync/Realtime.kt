package com.rimon.smartfamily.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * Live chat (2.2): while the app is on screen, a Supabase Realtime socket tells this phone the moment a chat row
 * changes. Only "something changed in table X" is used — the rows themselves stay encrypted and are fetched by
 * the normal sync, so end-to-end encryption is unchanged. Free tier: 200 concurrent connections.
 */
class Realtime(private val api: SupabaseApi) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    @Volatile private var socket: WebSocket? = null
    private val _changes = MutableSharedFlow<String>(extraBufferCapacity = 32)
    /** Emits the table name of every change the server reports. */
    val changes: SharedFlow<String> = _changes

    private val tables = listOf("chat_messages", "chat_reactions", "chat_reads", "chats", "chat_keys")

    /** Starts (or keeps) the socket; reconnects with back-off until [stop]. */
    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            var backoff = 2_000L
            while (isActive) {
                val closed = Channel<Unit>(1)
                runCatching {
                    if (!api.isSignedIn) { delay(10_000); return@runCatching }
                    val (url, key, token) = api.realtimeCredentials()
                    val ws = url.trimEnd('/').replaceFirst("https://", "wss://").replaceFirst("http://", "ws://") +
                        "/realtime/v1/websocket?apikey=$key&vsn=1.0.0"
                    socket = api.socketClient.newWebSocket(Request.Builder().url(ws).build(), object : WebSocketListener() {
                        override fun onOpen(webSocket: WebSocket, response: Response) {
                            val changes = tables.joinToString(",") { "{\"event\":\"*\",\"schema\":\"public\",\"table\":\"$it\"}" }
                            webSocket.send("{\"topic\":\"realtime:sf-chat\",\"event\":\"phx_join\",\"ref\":\"1\",\"payload\":{\"config\":{\"postgres_changes\":[$changes]},\"access_token\":\"$token\"}}")
                            backoff = 2_000L
                        }
                        override fun onMessage(webSocket: WebSocket, text: String) {
                            val o = runCatching { api.json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
                            if (o["event"]?.jsonPrimitive?.contentOrNull != "postgres_changes") return
                            val table = ((o["payload"] as? JsonObject)?.get("data") as? JsonObject)?.get("table")?.jsonPrimitive?.contentOrNull
                            _changes.tryEmit(table ?: "chat_messages")
                        }
                        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { closed.trySend(Unit) }
                        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { closed.trySend(Unit) }
                    })
                    // heartbeat every 25 s; refresh the token every 30 min by reconnecting
                    var n = 2; val started = System.currentTimeMillis()
                    while (isActive && closed.tryReceive().isFailure) {
                        delay(25_000)
                        socket?.send("{\"topic\":\"phoenix\",\"event\":\"heartbeat\",\"ref\":\"${n++}\",\"payload\":{}}")
                        if (System.currentTimeMillis() - started > 30 * 60_000) { socket?.close(1000, "refresh"); break }
                    }
                }
                socket = null
                if (!isActive) break
                delay(backoff); backoff = (backoff * 2).coerceAtMost(60_000)
            }
        }
    }

    fun stop() {
        job?.cancel(); job = null
        socket?.close(1000, "background"); socket = null
    }
}
