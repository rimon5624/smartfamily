package com.rimon.smartfamily.sync

import com.rimon.smartfamily.security.KeystoreCrypto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class SupabaseException(val code: Int, message: String) : IOException(message) {
    /** 4xx other than auth problems: the server refused this row (e.g. row-level security). */
    val isRejection: Boolean get() = code in 400..499 && code != 401
}

@Serializable
data class AuthUser(val id: String, val email: String? = null)

@Serializable
data class Session(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: Long = 3600,
    val user: AuthUser? = null,
    val expiresAt: Long = 0,
)

/**
 * Minimal Supabase client over plain HTTPS: Auth (GoTrue) and the REST API (PostgREST).
 * Only the project URL and the public anon key live on the phone; row-level security on the
 * server decides what each user may read or write.
 */
class SupabaseApi(
    private val crypto: KeystoreCrypto,
    private val config: suspend () -> Pair<String, String>,
) {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = true
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .build()

    private val jsonType = "application/json; charset=utf-8".toMediaType()

    // ---- session storage (encrypted with the Keystore) ----

    fun session(): Session? = crypto.get(SESSION)?.let { runCatching { json.decodeFromString<Session>(it) }.getOrNull() }

    private fun saveSession(s: Session?) {
        val stamped = s?.copy(expiresAt = System.currentTimeMillis() + s.expiresIn * 1000)
        crypto.put(SESSION, stamped?.let { json.encodeToString(Session.serializer(), it) })
    }

    val isSignedIn: Boolean get() = session() != null

    // ---- auth ----

    suspend fun signUp(email: String, password: String): Session? {
        val body = buildJsonObject { put("email", email); put("password", password) }
        val text = call("POST", "/auth/v1/signup", body.toString(), auth = false)
        // When email confirmation is on, Supabase returns the user without a session.
        val s = runCatching { json.decodeFromString<Session>(text) }.getOrNull()
        saveSession(s)
        return s
    }

    suspend fun signIn(email: String, password: String): Session {
        val body = buildJsonObject { put("email", email); put("password", password) }
        val text = call("POST", "/auth/v1/token?grant_type=password", body.toString(), auth = false)
        val s = json.decodeFromString<Session>(text)
        saveSession(s)
        return s
    }

    suspend fun signOut() {
        runCatching { call("POST", "/auth/v1/logout", "{}") }
        saveSession(null)
    }

    private suspend fun accessToken(): String {
        val s = session() ?: throw SupabaseException(401, "Not signed in")
        if (System.currentTimeMillis() < s.expiresAt - 60_000) return s.accessToken
        val body = buildJsonObject { put("refresh_token", s.refreshToken) }
        val text = call("POST", "/auth/v1/token?grant_type=refresh_token", body.toString(), auth = false)
        val fresh = json.decodeFromString<Session>(text)
        saveSession(fresh)
        return fresh.accessToken
    }

    // ---- REST ----

    suspend fun upsert(table: String, rowsJson: String, onConflict: String) {
        call(
            "POST", "/rest/v1/$table?on_conflict=$onConflict", rowsJson,
            extraHeaders = mapOf("Prefer" to "resolution=merge-duplicates,return=minimal"),
        )
    }

    /** Rows changed on the server after [since] (server milliseconds), oldest first. */
    suspend fun changedSince(table: String, since: Long, limit: Int = 500): String =
        call("GET", "/rest/v1/$table?select=*&updated_at=gt.$since&order=updated_at.asc&limit=$limit", null)

    suspend fun rpc(function: String, args: JsonElement): String =
        call("POST", "/rest/v1/rpc/$function", args.toString())

    private suspend fun call(
        method: String,
        path: String,
        body: String?,
        auth: Boolean = true,
        extraHeaders: Map<String, String> = emptyMap(),
    ): String = withContext(Dispatchers.IO) {
        val (url, anonKey) = config()
        if (url.isBlank() || anonKey.isBlank()) throw SupabaseException(0, "Supabase is not connected")
        if (!url.startsWith("https://")) throw SupabaseException(0, "The Supabase URL must start with https://")
        val builder = Request.Builder()
            .url(url + path)
            .header("apikey", anonKey)
            .header("Accept", "application/json")
        // New-style keys (sb_publishable_...) are not JWTs: send them only as apikey.
        if (auth) builder.header("Authorization", "Bearer ${accessToken()}")
        else if (!anonKey.startsWith("sb_")) builder.header("Authorization", "Bearer $anonKey")
        extraHeaders.forEach { (k, v) -> builder.header(k, v) }
        val reqBody = body?.toRequestBody(jsonType)
        when (method) {
            "GET" -> builder.get()
            "POST" -> builder.post(reqBody ?: "{}".toRequestBody(jsonType))
            "PATCH" -> builder.patch(reqBody ?: "{}".toRequestBody(jsonType))
            else -> throw IllegalArgumentException(method)
        }
        http.newCall(builder.build()).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw SupabaseException(resp.code, errorMessage(text, resp.code))
            text
        }
    }

    private fun errorMessage(text: String, code: Int): String = runCatching {
        val obj = json.parseToJsonElement(text) as kotlinx.serialization.json.JsonObject
        listOf("msg", "message", "error_description", "error")
            .firstNotNullOfOrNull { k -> obj[k]?.toString()?.trim('"') }
    }.getOrNull() ?: "HTTP $code"

    companion object {
        private const val SESSION = "supabase_session"
    }
}
