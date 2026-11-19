package com.rimon.smartfamily.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rimon.smartfamily.R
import com.rimon.smartfamily.SmartFamilyApp
import com.rimon.smartfamily.data.db.MealProfileEntity
import com.rimon.smartfamily.data.db.PackedMealEntity
import com.rimon.smartfamily.data.db.RecipeEntity
import com.rimon.smartfamily.smart.AiNotConfiguredException
import com.rimon.smartfamily.smart.Ingredient
import com.rimon.smartfamily.smart.RecipeDraft
import com.rimon.smartfamily.smart.RecipeImporter
import com.rimon.smartfamily.smart.RecipeText
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

/** An AI meal idea (RCP-6) or a dish that completes a meal (RCP-8). */
data class Idea(val title: String, val why: String, val draft: RecipeDraft?)

class KitchenViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as SmartFamilyApp).container
    private val repo = c.kitchen
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()

    /** The last AI or import result, shown by the screen that asked for it. */
    private val _ideas = MutableStateFlow<List<Idea>>(emptyList())
    val ideas = _ideas.asStateFlow()
    private val _imported = MutableStateFlow<RecipeEntity?>(null)
    val imported = _imported.asStateFlow()

    private fun say(id: Int, arg: String? = null) { _messages.tryEmit(UiMessage.Res(id, arg)) }

    private fun launchSafe(block: suspend () -> Unit) = viewModelScope.launch {
        _busy.value = true
        try { block() }
        catch (e: AiNotConfiguredException) { say(R.string.ai_needed) }
        catch (e: Exception) { _messages.tryEmit(UiMessage.Text(e.message ?: e.javaClass.simpleName)) }
        finally { _busy.value = false }
    }

    private suspend fun uid() = c.settings.current().userId
    private fun lang(): String = getApplication<Application>().resources.configuration.locales[0].language.let { if (it == "iw" || it == "he") "Hebrew" else "English" }

    fun recipes(familyId: String) = repo.observeRecipes(familyId)
    fun recipe(id: String) = repo.observeRecipe(id)
    fun ratings(recipeId: String) = repo.observeRatings(recipeId)
    fun plan(familyId: String, from: String, to: String) = repo.observePlan(familyId, from, to)
    fun profiles(familyId: String) = repo.observeProfiles(familyId)
    fun packed(familyId: String, from: String, to: String) = repo.observePacked(familyId, from, to)
    fun members(familyId: String) = c.families.observeMembers(familyId)
    fun inventory(familyId: String) = c.shopping.observeInventory(familyId)
    val ymd get() = repo.ymd

    fun clearImported() { _imported.value = null }
    fun clearIdeas() { _ideas.value = emptyList() }

    fun newRecipe(familyId: String, userId: String) =
        RecipeEntity(id = UUID.randomUUID().toString(), familyId = familyId, title = "", createdBy = userId)

    fun save(r: RecipeEntity) = launchSafe { repo.saveRecipe(r) }
    fun delete(id: String) = launchSafe { repo.deleteRecipe(id) }
    fun toggleFavorite(r: RecipeEntity) = launchSafe { repo.saveRecipe(r.copy(favorite = !r.favorite)) }
    fun markCooked(id: String) = launchSafe { repo.markCooked(id) }
    fun rate(r: RecipeEntity, stars: Int) = launchSafe { repo.rate(r.id, r.familyId, uid(), stars) }

    fun addMissing(familyId: String, ingredients: List<Ingredient>, factor: Double) = launchSafe {
        val n = repo.addMissingToList(familyId, uid(), ingredients, factor, getApplication<Application>().getString(R.string.shop_default_list))
        say(R.string.shop_added, n.toString())
    }

    // ---------- imports ----------

    /** RCP-1: schema.org data first (free); AI reads the page only when there is none. */
    fun importUrl(familyId: String, url: String) = launchSafe {
        val html = RecipeImporter.fetchPage(url.trim())
        val draft = RecipeImporter.fromHtml(html, url.trim())
            ?: parseRecipe(c.gemini.ask(recipePrompt("Extract the recipe from this web page text.") + "\n\nPAGE:\n" + RecipeImporter.pageText(html)), url.trim())
            ?: throw IllegalStateException(getApplication<Application>().getString(R.string.rec_import_failed))
        _imported.value = repo.saveDraft(familyId, uid(), draft)
    }

    fun importPhoto(familyId: String, uri: Uri) = launchSafe {
        val text = c.gemini.askWithImage(getApplication(), uri, recipePrompt("Read the recipe in this photo (it may be handwritten in Hebrew)."))
        val draft = parseRecipe(text, "") ?: throw IllegalStateException(getApplication<Application>().getString(R.string.rec_import_failed))
        _imported.value = repo.saveDraft(familyId, uid(), draft)
    }

    fun saveIdea(familyId: String, idea: Idea) = launchSafe {
        val d = idea.draft ?: RecipeDraft(idea.title, notes = idea.why)
        _imported.value = repo.saveDraft(familyId, uid(), d)
    }

    private fun recipePrompt(task: String) =
        "$task Answer only JSON: {\"title\":string,\"servings\":number,\"prepMinutes\":number,\"cookMinutes\":number," +
            "\"kashrut\":\"meat\"|\"dairy\"|\"parve\",\"mealTypes\":string (comma separated from breakfast,lunch,dinner,shabbat,holiday,snack)," +
            "\"ingredients\":[{\"name\":string,\"quantity\":number,\"unit\":string}],\"steps\":[string]}. " +
            "Keep the language of the recipe. Quantity 0 when not given."

    private fun parseRecipe(text: String, source: String): RecipeDraft? {
        val o = runCatching { json.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: return null
        return draftFrom(o, source)
    }

    private fun draftFrom(o: JsonObject, source: String): RecipeDraft? {
        fun s(k: String) = o[k]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        val title = s("title").ifBlank { return null }
        val ings = (o["ingredients"] as? JsonArray)?.mapNotNull { e ->
            val io = e as? JsonObject ?: return@mapNotNull null
            val name = io["name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            if (name.isEmpty()) null else Ingredient(
                name, io["quantity"]?.jsonPrimitive?.doubleOrNull ?: 0.0, io["unit"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            )
        }.orEmpty()
        val steps = (o["steps"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull?.trim()?.takeIf { s -> s.isNotEmpty() } }.orEmpty()
        return RecipeDraft(
            title = title,
            servings = o["servings"]?.jsonPrimitive?.intOrNull ?: 4,
            prepMinutes = o["prepMinutes"]?.jsonPrimitive?.intOrNull ?: 0,
            cookMinutes = o["cookMinutes"]?.jsonPrimitive?.intOrNull ?: 0,
            kashrut = s("kashrut").takeIf { it in setOf("meat", "dairy", "parve") } ?: "parve",
            mealTypes = s("mealTypes"),
            ingredients = ings, steps = steps, source = source,
        )
    }

    // ---------- AI helpers ----------

    /** RCP-6: what to cook now, from the time of day, kashrut, the pantry and what expires soon. */
    fun suggest(familyId: String, mealType: String, kashrut: String, minutes: Int, extra: String, pantry: List<String>, expiring: List<String>) = launchSafe {
        val prompt = "Suggest 4 family dishes in ${lang()} for: meal=$mealType, kashrut=$kashrut (never mix meat and dairy), " +
            "ready within $minutes minutes. Prefer using what expires soon: ${expiring.joinToString()}. " +
            "Already at home: ${pantry.take(60).joinToString()}. $extra " +
            "Answer only JSON: {\"ideas\":[{\"title\":string,\"why\":string (one short line),\"servings\":number," +
            "\"prepMinutes\":number,\"cookMinutes\":number,\"kashrut\":\"meat\"|\"dairy\"|\"parve\",\"mealTypes\":string," +
            "\"ingredients\":[{\"name\":string,\"quantity\":number,\"unit\":string}],\"steps\":[string]}]}"
        _ideas.value = parseIdeas(c.gemini.ask(prompt))
    }

    /** RCP-8: sides, salad and dessert that keep the kashrut of the main dish. */
    fun completeMeal(r: RecipeEntity) = launchSafe {
        val prompt = "In ${lang()}, suggest 3 dishes (a side, a salad, a dessert) that complete a ${r.kashrut} meal whose main dish is \"${r.title}\". " +
            "They must keep kashrut (${r.kashrut}: no mixing meat and dairy). Answer only JSON: {\"ideas\":[{\"title\":string,\"why\":string}]}"
        _ideas.value = parseIdeas(c.gemini.ask(prompt))
    }

    /** RCP-7: replacements for an ingredient that keep the recipe's kashrut. Shown as ideas. */
    fun substitute(r: RecipeEntity, ingredient: String) = launchSafe {
        val prompt = "In ${lang()}, give 3 substitutes for \"$ingredient\" in the ${r.kashrut} recipe \"${r.title}\", keeping it ${r.kashrut}. " +
            "Answer only JSON: {\"ideas\":[{\"title\":string (the substitute and amount),\"why\":string (how the dish changes)}]}"
        _ideas.value = parseIdeas(c.gemini.ask(prompt))
    }

    private fun parseIdeas(text: String): List<Idea> {
        val root = runCatching { json.parseToJsonElement(text) }.getOrNull() ?: return emptyList()
        val arr = (root as? JsonObject)?.get("ideas")?.jsonArray ?: (root as? JsonArray) ?: return emptyList()
        return arr.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val title = o["title"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            if (title.isEmpty()) return@mapNotNull null
            val why = o["why"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            Idea(title, why, if (o["ingredients"] != null) draftFrom(o, "AI") else null)
        }.also { if (it.isEmpty()) say(R.string.shop_nothing_found) }
    }

    // ---------- meal plan ----------

    fun setPlan(familyId: String, date: String, slot: String, recipe: RecipeEntity?, title: String, servings: Int) =
        launchSafe { repo.setPlan(familyId, date, slot, recipe, title, servings) }
    fun clearPlan(id: String) = launchSafe { repo.clearPlan(id) }
    fun copyPreviousWeek(familyId: String, weekStart: Long) = launchSafe { repo.copyPreviousWeek(familyId, weekStart) }
    fun addWeekToList(familyId: String, from: String, to: String) = launchSafe {
        val ings = repo.plannedIngredients(familyId, from, to)
        val n = repo.addMissingToList(familyId, uid(), ings, 1.0, getApplication<Application>().getString(R.string.shop_default_list))
        say(R.string.shop_added, n.toString())
    }

    // ---------- packed meals ----------

    fun saveProfile(p: MealProfileEntity) = launchSafe { repo.saveProfile(p) }
    fun deleteProfile(id: String) = launchSafe { repo.deleteProfile(id) }
    fun savePacked(p: PackedMealEntity) = launchSafe { repo.savePacked(p) }
    fun newProfile(familyId: String) = MealProfileEntity(id = UUID.randomUUID().toString(), familyId = familyId, personName = "")

    fun planWeek(p: MealProfileEntity) = launchSafe {
        val n = repo.planWeek(p)
        say(R.string.pck_planned, n.toString())
    }

    /** Optional AI plan that follows the place's rules and the child's likes (PCK-1..3). */
    fun planWeekAi(p: MealProfileEntity) = launchSafe {
        val days = (0 until 7).map { i ->
            val k = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, i) }
            k to repo.ymd.format(k.time)
        }.filter { (k, _) -> p.weekdays and (1 shl (k.get(java.util.Calendar.DAY_OF_WEEK) - 1)) != 0 }.map { it.second }
        val prompt = "In ${lang()}, plan packed meals for ${p.personName} (${p.place}) for these dates: ${days.joinToString()}. " +
            "Kashrut: ${p.kashrut}. Rules: ${p.rules}. Likes: ${p.likes.replace('\n', ',')}. Dislikes: ${p.dislikes.replace('\n', ',')}. " +
            "No item more than ${p.maxRepeat} times a week; 2-4 items a day; food that keeps without a fridge if the place has none. " +
            "Answer only JSON: {\"days\":[{\"date\":\"yyyy-MM-dd\",\"items\":[string]}]}"
        val root = json.parseToJsonElement(c.gemini.ask(prompt)).jsonObject
        val map = root["days"]?.jsonArray?.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val d = o["date"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            d to (o["items"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()
        }?.toMap().orEmpty()
        say(R.string.pck_planned, repo.planWeek(p, aiDays = map).toString())
    }

    fun ingredientsOf(r: RecipeEntity) = RecipeText.ingredientsFromJson(r.ingredients)
    fun stepsOf(r: RecipeEntity) = RecipeText.stepsFromJson(r.steps)
}
