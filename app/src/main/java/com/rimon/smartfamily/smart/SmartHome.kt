package com.rimon.smartfamily.smart

import com.rimon.smartfamily.security.KeystoreCrypto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.time.Instant
import java.util.concurrent.TimeUnit

/** A device found in a smart-home account, to link to an appliance. */
data class SmartDevice(val id: String, val name: String, val type: String, val brand: String)

/** Live state of a connected appliance (APL-18..20). */
data class LiveStatus(
    val state: String,                 // e.g. Run, Ready, Finished, Pause, run, stop
    val program: String = "",
    val remainingSeconds: Long? = null,
    val progressPercent: Int? = null,
    val door: String = "",
    val remoteStartAllowed: Boolean = false,
    val connected: Boolean = true,
)

private val http = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
private val json = Json { ignoreUnknownKeys = true }

/**
 * Bosch / Siemens Home Connect (free developer program), OAuth device flow: the user signs in
 * on Home Connect's own page and enters a short code; no password passes through this app.
 * Tokens are kept encrypted on this phone only.
 */
class HomeConnectClient(private val crypto: KeystoreCrypto, private val clientId: suspend () -> String) {
    private val base = "https://api.home-connect.com"
    private val accept = "application/vnd.bsh.sdk.v1+json"

    data class DeviceCode(val deviceCode: String, val userCode: String, val verificationUrl: String, val interval: Int)

    val isConnected: Boolean get() = crypto.get(TOKENS) != null

    suspend fun startLogin(): DeviceCode = withContext(Dispatchers.IO) {
        val id = clientId().ifBlank { throw IOException("Add the Home Connect Client ID first") }
        val body = FormBody.Builder().add("client_id", id).add("scope", "IdentifyAppliance Monitor Control Settings").build()
        val o = post("$base/security/oauth/device_authorization", body)
        DeviceCode(
            o.s("device_code"), o.s("user_code"),
            o["verification_uri_complete"]?.jsonPrimitive?.contentOrNull ?: o.s("verification_uri"),
            o["interval"]?.jsonPrimitive?.intOrNull ?: 5,
        )
    }

    /** Polls until the user approved on the Home Connect page; returns false while still waiting. */
    suspend fun pollLogin(code: DeviceCode): Boolean = withContext(Dispatchers.IO) {
        val body = FormBody.Builder()
            .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
            .add("device_code", code.deviceCode)
            .add("client_id", clientId())
            .build()
        val resp = http.newCall(Request.Builder().url("$base/security/oauth/token").post(body).build()).execute()
        resp.use {
            val text = it.body?.string().orEmpty()
            if (!it.isSuccessful) {
                if (text.contains("authorization_pending") || text.contains("slow_down")) return@withContext false
                throw IOException(errorOf(text) ?: "Home Connect HTTP ${it.code}")
            }
            saveTokens(json.parseToJsonElement(text).jsonObject)
            true
        }
    }

    fun disconnect() = crypto.put(TOKENS, null)

    private fun saveTokens(o: JsonObject) {
        val expires = System.currentTimeMillis() + (o["expires_in"]?.jsonPrimitive?.longOrNull ?: 3600L) * 1000
        val stored = buildJsonObject {
            put("access", o.s("access_token"))
            put("refresh", o["refresh_token"]?.jsonPrimitive?.contentOrNull ?: storedTokens()?.s("refresh").orEmpty())
            put("expires", expires)
        }
        crypto.put(TOKENS, stored.toString())
    }

    private fun storedTokens(): JsonObject? = crypto.get(TOKENS)?.let { runCatching { json.parseToJsonElement(it).jsonObject }.getOrNull() }

    private suspend fun token(): String {
        val t = storedTokens() ?: throw IOException("Home Connect is not connected")
        if (System.currentTimeMillis() < (t["expires"]?.jsonPrimitive?.longOrNull ?: 0L) - 60_000) return t.s("access")
        val body = FormBody.Builder().add("grant_type", "refresh_token").add("refresh_token", t.s("refresh")).add("client_id", clientId()).build()
        saveTokens(post("$base/security/oauth/token", body))
        return storedTokens()!!.s("access")
    }

    suspend fun devices(): List<SmartDevice> = withContext(Dispatchers.IO) {
        get("/api/homeappliances")["data"]?.jsonObject?.get("homeappliances")?.jsonArray?.map { e ->
            val o = e.jsonObject
            SmartDevice(o.s("haId"), o.s("name"), o.s("type"), o.s("brand"))
        }.orEmpty()
    }

    suspend fun status(haId: String): LiveStatus = withContext(Dispatchers.IO) {
        val st = runCatching { get("/api/homeappliances/$haId/status")["data"]?.jsonObject?.get("status")?.jsonArray }.getOrNull()
        val map = st?.associate { it.jsonObject.s("key") to it.jsonObject["value"]?.jsonPrimitive?.contentOrNull.orEmpty() }.orEmpty()
        val active = runCatching { get("/api/homeappliances/$haId/programs/active")["data"]?.jsonObject }.getOrNull()
        val opts = active?.get("options")?.jsonArray?.associate { it.jsonObject.s("key") to it.jsonObject["value"]?.jsonPrimitive }.orEmpty()
        LiveStatus(
            state = map["BSH.Common.Status.OperationState"].orEmpty().substringAfterLast('.'),
            program = active?.s("key")?.substringAfterLast('.').orEmpty(),
            remainingSeconds = opts["BSH.Common.Option.RemainingProgramTime"]?.longOrNull,
            progressPercent = opts["BSH.Common.Option.ProgramProgress"]?.intOrNull,
            door = map["BSH.Common.Status.DoorState"].orEmpty().substringAfterLast('.'),
            remoteStartAllowed = map["BSH.Common.Status.RemoteControlStartAllowed"] == "true",
            connected = st != null,
        )
    }

    /** Program keys the appliance offers, e.g. Dishcare.Dishwasher.Program.Eco50. */
    suspend fun programs(haId: String): List<String> = withContext(Dispatchers.IO) {
        get("/api/homeappliances/$haId/programs/available")["data"]?.jsonObject?.get("programs")?.jsonArray
            ?.map { it.jsonObject.s("key") }.orEmpty()
    }

    /** Starts a program; Home Connect refuses unless remote start is enabled on the appliance (APL-19). */
    suspend fun start(haId: String, programKey: String) = withContext(Dispatchers.IO) {
        val body = buildJsonObject { putJsonObject("data") { put("key", programKey) } }
        request("PUT", "/api/homeappliances/$haId/programs/active", body.toString())
    }

    private suspend fun get(path: String): JsonObject = json.parseToJsonElement(request("GET", path, null).ifBlank { "{}" }).jsonObject

    private suspend fun request(method: String, path: String, body: String?): String {
        val b = Request.Builder().url(base + path).header("Authorization", "Bearer ${token()}").header("Accept", accept)
        when (method) {
            "GET" -> b.get()
            else -> b.method(method, (body ?: "{}").toRequestBody(accept.toMediaType()))
        }
        http.newCall(b.build()).execute().use { r ->
            val text = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw IOException(errorOf(text) ?: "Home Connect HTTP ${r.code}")
            return text
        }
    }

    private fun post(url: String, body: FormBody): JsonObject =
        http.newCall(Request.Builder().url(url).post(body).build()).execute().use { r ->
            val text = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw IOException(errorOf(text) ?: "Home Connect HTTP ${r.code}")
            json.parseToJsonElement(text).jsonObject
        }

    private fun errorOf(text: String): String? = runCatching {
        val o = json.parseToJsonElement(text).jsonObject
        o["error"]?.let { e -> (e as? JsonObject)?.s("description") ?: e.jsonPrimitive.contentOrNull } ?: o["error_description"]?.jsonPrimitive?.contentOrNull
    }.getOrNull()

    companion object { private const val TOKENS = "home_connect_tokens" }
}

/** Samsung SmartThings with a personal access token (kept encrypted on this phone). Status only. */
class SmartThingsClient(private val crypto: KeystoreCrypto) {
    private val base = "https://api.smartthings.com/v1"

    fun setToken(t: String?) = crypto.put(TOKEN, t?.trim()?.ifEmpty { null })
    val isConnected: Boolean get() = crypto.get(TOKEN) != null

    suspend fun devices(): List<SmartDevice> = withContext(Dispatchers.IO) {
        get("/devices")["items"]?.jsonArray?.map { e ->
            val o = e.jsonObject
            SmartDevice(o.s("deviceId"), o["label"]?.jsonPrimitive?.contentOrNull ?: o.s("name"), o.s("ocfDeviceType"), o.s("manufacturerName"))
        }.orEmpty()
    }

    suspend fun status(deviceId: String): LiveStatus = withContext(Dispatchers.IO) {
        val main = get("/devices/$deviceId/status")["components"]?.jsonObject?.get("main")?.jsonObject ?: JsonObject(emptyMap())
        fun attr(cap: String, a: String) = main[cap]?.jsonObject?.get(a)?.jsonObject?.get("value")?.jsonPrimitive?.contentOrNull
        val capability = listOf("dryerOperatingState", "washerOperatingState", "dishwasherOperatingState").firstOrNull { main[it] != null }
        val state = capability?.let { attr(it, "machineState") } ?: attr("switch", "switch").orEmpty()
        val completion = capability?.let { attr(it, "completionTime") }
        val remaining = completion?.let { runCatching { (Instant.parse(it).toEpochMilli() - System.currentTimeMillis()) / 1000 }.getOrNull() }?.takeIf { it > 0 }
        val job = capability?.let { attr(it, it.replace("OperatingState", "JobState")) } ?: ""
        LiveStatus(state = state, program = job, remainingSeconds = remaining)
    }

    private fun get(path: String): JsonObject {
        val t = crypto.get(TOKEN) ?: throw IOException("SmartThings is not connected")
        val req = Request.Builder().url(base + path).header("Authorization", "Bearer $t").build()
        http.newCall(req).execute().use { r ->
            val text = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw IOException("SmartThings HTTP ${r.code}")
            return json.parseToJsonElement(text).jsonObject
        }
    }

    companion object { private const val TOKEN = "smartthings_token" }
}

private fun JsonObject.s(key: String): String = this[key]?.jsonPrimitive?.contentOrNull.orEmpty()
