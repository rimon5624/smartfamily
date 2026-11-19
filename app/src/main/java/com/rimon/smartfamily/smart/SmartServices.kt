package com.rimon.smartfamily.smart

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.rimon.smartfamily.model.ItemDraft
import com.rimon.smartfamily.model.ShopCategory
import com.rimon.smartfamily.security.KeystoreCrypto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

private val http = OkHttpClient.Builder()
    .connectTimeout(20, TimeUnit.SECONDS)
    .readTimeout(60, TimeUnit.SECONDS)
    .build()

private val json = Json { ignoreUnknownKeys = true }

/** Free, open product database: looks up a barcode (SHP-4). No key needed. */
object OpenFoodFacts {
    suspend fun lookup(barcode: String): ItemDraft? = withContext(Dispatchers.IO) {
        val url = "https://world.openfoodfacts.org/api/v2/product/$barcode.json" +
            "?fields=product_name,product_name_he,product_name_en,brands,quantity"
        val req = Request.Builder().url(url).header("User-Agent", "SmartFamily/0.2 (personal Android app)").build()
        runCatching {
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val root = json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
                if (root["status"]?.jsonPrimitive?.contentOrNull != "1") return@use null
                val p = root["product"]?.jsonObject ?: return@use null
                fun s(k: String) = p[k]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                val name = s("product_name_he").ifEmpty { s("product_name") }.ifEmpty { s("product_name_en") }
                if (name.isEmpty()) return@use null
                val size = s("quantity")
                ItemDraft(
                    name = if (size.isNotEmpty()) "$name $size" else name,
                    brand = s("brands").substringBefore(','),
                    barcode = barcode,
                )
            }
        }.getOrNull()
    }
}

class AiNotConfiguredException : IOException("AI is not set up: add a Gemini API key in Settings → AI services")

/**
 * Gemini vision for photos of products, shelves and handwritten lists (SHP-5, INV-2).
 * Uses the user's own Gemini API key (Google's free tier has rate limits, and free-tier input may
 * be used by Google to improve its products; the paid tier does not). The key stays encrypted on the
 * phone and is sent only to Google.
 */
class GeminiClient(private val crypto: KeystoreCrypto, private val settings: suspend () -> Pair<String, Boolean>) {

    fun apiKey(): String? = crypto.get(KEY)
    fun setApiKey(key: String?) = crypto.put(KEY, key?.trim()?.ifEmpty { null })
    val isConfigured: Boolean get() = !apiKey().isNullOrBlank()

    /** Returns items seen in the photo; [mode] selects the prompt. */
    suspend fun itemsFromPhoto(context: Context, uri: Uri, mode: PhotoMode): List<ItemDraft> {
        val jpeg = withContext(Dispatchers.IO) { loadScaledJpeg(context, uri, 1280) }
        return parseItems(ask(mode.prompt, listOf("image/jpeg" to jpeg)))
    }

    /** Sends a prompt with an image (JPEG) and returns the model's JSON text. */
    suspend fun askWithImage(context: Context, uri: Uri, prompt: String): String {
        val jpeg = withContext(Dispatchers.IO) { loadScaledJpeg(context, uri, 1600) }
        return ask(prompt, listOf("image/jpeg" to jpeg))
    }

    /** Sends a prompt with a PDF document (e.g. an appliance manual, up to ~20 MB). */
    suspend fun askWithPdf(pdf: ByteArray, prompt: String): String = ask(prompt, listOf("application/pdf" to pdf))

    /**
     * One Gemini call. The answer is requested as JSON; [attachments] are (mime type, bytes).
     * Nothing is sent while privacy mode is on.
     */
    suspend fun ask(prompt: String, attachments: List<Pair<String, ByteArray>> = emptyList()): String = withContext(Dispatchers.IO) {
        val (model, privacy) = settings()
        if (privacy) throw IOException("Privacy mode is on: AI features are off")
        val key = apiKey() ?: throw AiNotConfiguredException()
        val body = buildJsonObject {
            putJsonArray("contents") {
                addJsonObject {
                    putJsonArray("parts") {
                        addJsonObject { put("text", prompt) }
                        attachments.forEach { (mime, bytes) ->
                            addJsonObject {
                                putJsonObject("inline_data") {
                                    put("mime_type", mime)
                                    put("data", Base64.encodeToString(bytes, Base64.NO_WRAP))
                                }
                            }
                        }
                    }
                }
            }
            putJsonObject("generationConfig") {
                put("responseMimeType", "application/json")
                put("temperature", 0.2)
            }
        }
        val req = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/${model.ifBlank { DEFAULT_MODEL }}:generateContent")
            .header("x-goog-api-key", key)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val msg = runCatching {
                    json.parseToJsonElement(raw).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
                }.getOrNull()
                throw IOException(msg ?: "Gemini HTTP ${resp.code}")
            }
            json.parseToJsonElement(raw).jsonObject["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
                ?.get("content")?.jsonObject?.get("parts")?.jsonArray?.firstOrNull()?.jsonObject
                ?.get("text")?.jsonPrimitive?.contentOrNull.orEmpty()
        }
    }

    private fun parseItems(text: String): List<ItemDraft> {
        val el = runCatching { json.parseToJsonElement(text) }.getOrNull() ?: return emptyList()
        val arr: JsonArray = when (el) {
            is JsonArray -> el
            is JsonObject -> el["items"]?.jsonArray ?: return emptyList()
            else -> return emptyList()
        }
        return arr.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            fun s(k: String) = o[k]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            val name = s("name")
            if (name.isEmpty()) return@mapNotNull null
            val cat = s("category").let { c -> ShopCategory.entries.firstOrNull { it.key == c }?.key ?: ShopCategory.guess(name).key }
            ItemDraft(
                name = name,
                quantity = o["quantity"]?.jsonPrimitive?.doubleOrNull ?: 1.0,
                unit = s("unit").takeIf { it in setOf("", "pcs", "pack", "kg", "g", "l", "ml") } ?: "",
                brand = s("brand"),
                category = cat,
            )
        }
    }

    private fun loadScaledJpeg(context: Context, uri: Uri, maxSide: Int): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / sample > maxSide * 2 || bounds.outHeight / sample > maxSide * 2) sample *= 2
        val bmp = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: throw IOException("Could not read the photo")
        val scale = maxSide.toFloat() / maxOf(bmp.width, bmp.height)
        val out = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true) else bmp
        return ByteArrayOutputStream().use { os -> out.compress(Bitmap.CompressFormat.JPEG, 85, os); os.toByteArray() }
    }

    enum class PhotoMode(val prompt: String) {
        PRODUCTS(
            "You help a family build a shopping list. List the grocery or household products visible in this photo " +
                "(a product, an empty package, a handwritten or printed list). Answer only JSON: " +
                "{\"items\":[{\"name\":string,\"brand\":string,\"quantity\":number,\"unit\":\"\"|\"pcs\"|\"pack\"|\"kg\"|\"g\"|\"l\"|\"ml\"," +
                "\"category\":\"produce\"|\"bakery\"|\"dairy\"|\"meat_fish\"|\"dry\"|\"frozen\"|\"drinks\"|\"cleaning\"|\"personal\"|\"baby\"|\"other\"}]}. " +
                "Write names in the language written on the product or list (Hebrew if Hebrew). Include the size in the name when visible."
        ),
        SHELF(
            "List the food and household items visible in this photo of a fridge, freezer, pantry shelf or grocery haul, " +
                "with a quantity estimate. Answer only JSON in this shape: {\"items\":[{\"name\":string,\"brand\":string," +
                "\"quantity\":number,\"unit\":\"\"|\"pcs\"|\"pack\"|\"kg\"|\"g\"|\"l\"|\"ml\",\"category\":\"produce\"|\"bakery\"|" +
                "\"dairy\"|\"meat_fish\"|\"dry\"|\"frozen\"|\"drinks\"|\"cleaning\"|\"personal\"|\"baby\"|\"other\"}]}. " +
                "Use Hebrew names when the packages are in Hebrew."
        ),
    }

    companion object {
        private const val KEY = "gemini_api_key"
        const val DEFAULT_MODEL = "gemini-2.5-flash"
    }
}
