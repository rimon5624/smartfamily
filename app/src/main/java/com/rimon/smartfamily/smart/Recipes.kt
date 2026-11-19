package com.rimon.smartfamily.smart

import com.rimon.smartfamily.model.qty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

@Serializable
data class Ingredient(val name: String, val quantity: Double = 0.0, val unit: String = "", val note: String = "")

/** A recipe as parsed from a web page, a photo or an AI answer, before it is saved. */
data class RecipeDraft(
    val title: String,
    val servings: Int = 4,
    val prepMinutes: Int = 0,
    val cookMinutes: Int = 0,
    val ingredients: List<Ingredient> = emptyList(),
    val steps: List<String> = emptyList(),
    val kashrut: String = "parve",
    val mealTypes: String = "",
    val source: String = "",
    val notes: String = "",
)

object RecipeText {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun ingredientsFromJson(s: String): List<Ingredient> =
        runCatching { json.decodeFromString(ListSerializer(Ingredient.serializer()), s) }.getOrDefault(emptyList())

    fun ingredientsToJson(list: List<Ingredient>): String = json.encodeToString(ListSerializer(Ingredient.serializer()), list)

    fun stepsFromJson(s: String): List<String> =
        runCatching { json.decodeFromString(ListSerializer(String.serializer()), s) }.getOrDefault(emptyList())

    fun stepsToJson(list: List<String>): String = json.encodeToString(ListSerializer(String.serializer()), list)

    /** "2 cups flour" / "3 ביצים" / "מלח" -> ingredient lines, one per line, using the speech parser. */
    fun parseIngredientLines(text: String): List<Ingredient> = text.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { line ->
        val d = SpeechParser.parse(line.replace(",", " ")).firstOrNull()
        val hasNumber = line.trim().firstOrNull()?.isDigit() == true || (d != null && d.quantity != 1.0) || (d?.unit?.isNotEmpty() == true)
        if (d == null) Ingredient(line) else Ingredient(d.name, if (hasNumber) d.quantity else 0.0, d.unit)
    }

    fun ingredientLine(i: Ingredient, factor: Double = 1.0): String {
        val q = i.quantity * factor
        val qtyText = if (q <= 0.0) "" else niceQty(q) + " "
        val unit = if (i.unit.isNotEmpty()) i.unit + " " else ""
        return (qtyText + unit + i.name + if (i.note.isNotBlank()) " (${i.note})" else "").trim()
    }

    /** Rounds scaled quantities sensibly: whole eggs stay whole, small amounts keep a quarter step. */
    fun niceQty(q: Double): String = when {
        q >= 10 -> q.roundToInt().toString()
        else -> ((q * 4).roundToInt() / 4.0).let { if (it == 0.0) 0.25 else it }.qty()
    }

    /** Timers found in a step ("bake 40 minutes", "לבשל שעה וחצי"), in minutes (CK: RCP-14). */
    fun timersIn(step: String): List<Int> {
        val out = mutableListOf<Int>()
        val num = Regex("(\\d+(?:[.,]\\d+)?)\\s*(?:-\\s*\\d+\\s*)?(דקות|דק'|דקה|minutes?|mins?|שעות|שעה|hours?|hrs?)", RegexOption.IGNORE_CASE)
        num.findAll(step).forEach { m ->
            val v = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return@forEach
            val unit = m.groupValues[2].lowercase()
            out += if (unit.startsWith("ש") || unit.startsWith("h")) (v * 60).roundToInt() else v.roundToInt()
        }
        if (out.isEmpty()) {
            when {
                step.contains("שעה וחצי") -> out += 90
                step.contains("חצי שעה") -> out += 30
                step.contains("רבע שעה") -> out += 15
                Regex("\\bשעתיים\\b").containsMatchIn(step) -> out += 120
                Regex("\\bשעה\\b").containsMatchIn(step) -> out += 60
            }
        }
        return out.filter { it in 1..24 * 60 }.distinct()
    }
}

/** RCP-1: imports a recipe from a web page using its schema.org data (free, no AI). */
object RecipeImporter {
    private val http = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).followRedirects(true).build()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun fetchPage(url: String): String = withContext(Dispatchers.IO) {
        if (!url.startsWith("http")) throw IOException("Not a web address")
        val req = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0 (Android) SmartFamily").build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            r.body?.string().orEmpty()
        }
    }

    fun fromHtml(html: String, url: String): RecipeDraft? {
        val scripts = Regex("<script[^>]*application/ld\\+json[^>]*>(.*?)</script>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
            .findAll(html).map { it.groupValues[1].trim() }
        for (s in scripts) {
            val el = runCatching { json.parseToJsonElement(s) }.getOrNull() ?: continue
            findRecipe(el)?.let { return toDraft(it, url) }
        }
        return null
    }

    private fun findRecipe(el: JsonElement): JsonObject? = when (el) {
        is JsonArray -> el.firstNotNullOfOrNull { findRecipe(it) }
        is JsonObject -> {
            val type = el["@type"]
            val isRecipe = when (type) {
                is JsonPrimitive -> type.contentOrNull == "Recipe"
                is JsonArray -> type.any { (it as? JsonPrimitive)?.contentOrNull == "Recipe" }
                else -> false
            }
            if (isRecipe) el else el["@graph"]?.let { findRecipe(it) }
        }
        else -> null
    }

    private fun str(e: JsonElement?): String = when (e) {
        is JsonPrimitive -> e.contentOrNull.orEmpty()
        is JsonArray -> e.firstOrNull()?.let { str(it) }.orEmpty()
        is JsonObject -> str(e["text"] ?: e["name"])
        else -> ""
    }

    private fun minutes(iso: String): Int {
        val m = Regex("PT(?:(\\d+)H)?(?:(\\d+)M)?").find(iso) ?: return 0
        return (m.groupValues[1].toIntOrNull() ?: 0) * 60 + (m.groupValues[2].toIntOrNull() ?: 0)
    }

    private fun stripHtml(s: String) = s.replace(Regex("<[^>]+>"), " ").replace("&nbsp;", " ").replace("&amp;", "&").replace(Regex("\\s+"), " ").trim()

    private fun toDraft(o: JsonObject, url: String): RecipeDraft {
        val ingredients = (o["recipeIngredient"] as? JsonArray)?.map { stripHtml(str(it)) }?.filter { it.isNotBlank() }.orEmpty()
        val steps = mutableListOf<String>()
        fun collect(e: JsonElement?) {
            when (e) {
                is JsonArray -> e.forEach { collect(it) }
                is JsonObject -> if (e["itemListElement"] != null) collect(e["itemListElement"]) else steps += stripHtml(str(e["text"] ?: e["name"]))
                is JsonPrimitive -> e.contentOrNull?.split('\n')?.map { stripHtml(it) }?.filter { it.isNotBlank() }?.let { steps += it }
                else -> {}
            }
        }
        collect(o["recipeInstructions"])
        val yield = str(o["recipeYield"]).filter { it.isDigit() }.take(3).toIntOrNull() ?: 4
        return RecipeDraft(
            title = stripHtml(str(o["name"])).ifBlank { "Recipe" },
            servings = yield.coerceIn(1, 50),
            prepMinutes = minutes(str(o["prepTime"])),
            cookMinutes = minutes(str(o["cookTime"])),
            ingredients = RecipeText.parseIngredientLines(ingredients.joinToString("\n")),
            steps = steps.filter { it.isNotBlank() },
            source = url,
        )
    }

    /** Readable text of a page, for AI extraction when the page has no recipe data. */
    fun pageText(html: String): String = stripHtml(
        html.replace(Regex("<(script|style)[^>]*>.*?</\\1>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), " ")
    ).take(12_000)
}
