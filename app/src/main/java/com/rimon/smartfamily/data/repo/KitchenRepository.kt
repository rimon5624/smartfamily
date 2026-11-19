package com.rimon.smartfamily.data.repo

import com.rimon.smartfamily.data.db.AppDatabase
import com.rimon.smartfamily.data.db.MealPlanEntryEntity
import com.rimon.smartfamily.data.db.MealProfileEntity
import com.rimon.smartfamily.data.db.PackedMealEntity
import com.rimon.smartfamily.data.db.RecipeEntity
import com.rimon.smartfamily.data.db.RecipeRatingEntity
import com.rimon.smartfamily.model.ItemDraft
import com.rimon.smartfamily.smart.Ingredient
import com.rimon.smartfamily.smart.RecipeDraft
import com.rimon.smartfamily.smart.RecipeText
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID

class KitchenRepository(
    private val db: AppDatabase,
    private val shopping: ShoppingRepository,
    private val onLocalChange: () -> Unit,
) {
    private fun now() = System.currentTimeMillis()
    private fun uuid() = UUID.randomUUID().toString()
    val ymd = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    // ---------- recipes ----------

    fun observeRecipes(familyId: String) = db.recipes().observeSorted(familyId)
    fun observeRecipe(id: String) = db.recipes().observeOne(id)
    fun observeRatings(recipeId: String) = db.recipeRatings().observeForRecipe(recipeId)

    suspend fun saveRecipe(r: RecipeEntity) {
        db.recipes().upsert(r.copy(updatedAt = now(), dirty = true))
        onLocalChange()
    }

    suspend fun saveDraft(familyId: String, userId: String, d: RecipeDraft): RecipeEntity {
        val r = RecipeEntity(
            id = uuid(), familyId = familyId, title = d.title, servings = d.servings, prepMinutes = d.prepMinutes,
            cookMinutes = d.cookMinutes, ingredients = RecipeText.ingredientsToJson(d.ingredients),
            steps = RecipeText.stepsToJson(d.steps), kashrut = d.kashrut, mealTypes = d.mealTypes,
            source = d.source, notes = d.notes, createdBy = userId,
        )
        saveRecipe(r)
        return r
    }

    suspend fun deleteRecipe(id: String) {
        val r = db.recipes().get(id) ?: return
        db.recipes().upsert(r.copy(deletedAt = now(), updatedAt = now(), dirty = true))
        onLocalChange()
    }

    suspend fun markCooked(id: String) {
        val r = db.recipes().get(id) ?: return
        db.recipes().upsert(r.copy(timesCooked = r.timesCooked + 1, lastCooked = now(), updatedAt = now(), dirty = true))
        onLocalChange()
    }

    suspend fun rate(recipeId: String, familyId: String, userId: String, stars: Int) {
        val existing = db.recipeRatings().find(recipeId, userId)
        db.recipeRatings().upsert(
            (existing ?: RecipeRatingEntity(id = uuid(), familyId = familyId, recipeId = recipeId, userId = userId))
                .copy(stars = stars.coerceIn(1, 5), updatedAt = now(), dirty = true)
        )
        onLocalChange()
    }

    /**
     * RCP-11 / SHP-6: ingredients that are not in the home inventory go to the family's shopping
     * list, scaled by [factor]. Returns how many were added.
     */
    suspend fun addMissingToList(familyId: String, userId: String, ingredients: List<Ingredient>, factor: Double, defaultListName: String): Int {
        val inv = db.inventory().all().filter { it.familyId == familyId && it.deletedAt == null && it.quantity > 0 }
        val missing = ingredients.filter { ing ->
            inv.none { it.name.contains(ing.name, ignoreCase = true) || ing.name.contains(it.name, ignoreCase = true) }
        }
        if (missing.isEmpty()) return 0
        shopping.ensureDefaultList(familyId, defaultListName, userId)
        val list = db.lists().all().filter { it.familyId == familyId && !it.isTemplate && it.deletedAt == null }.minByOrNull { it.createdAt } ?: return 0
        return shopping.addItems(
            list.id,
            missing.map { ItemDraft(it.name, if (it.quantity > 0) (it.quantity * factor) else 1.0, it.unit) },
            userId,
        )
    }

    // ---------- meal plan ----------

    fun observePlan(familyId: String, from: String, to: String) = db.mealPlanEntrys().observeRange(familyId, from, to)

    suspend fun setPlan(familyId: String, date: String, slot: String, recipe: RecipeEntity?, title: String, servings: Int) {
        val existing = db.mealPlanEntrys().range(familyId, date, date).firstOrNull { it.slot == slot }
        val entry = (existing ?: MealPlanEntryEntity(id = uuid(), familyId = familyId, date = date, slot = slot))
            .copy(recipeId = recipe?.id, title = recipe?.title ?: title, servings = servings, deletedAt = null, updatedAt = now(), dirty = true)
        db.mealPlanEntrys().upsert(entry)
        onLocalChange()
    }

    suspend fun clearPlan(id: String) {
        val e = db.mealPlanEntrys().get(id) ?: return
        db.mealPlanEntrys().upsert(e.copy(deletedAt = now(), updatedAt = now(), dirty = true))
        onLocalChange()
    }

    /** Copies last week's plan onto this week (RCP-10). */
    suspend fun copyPreviousWeek(familyId: String, weekStart: Long) {
        val prevStart = weekStart - 7 * 86_400_000L
        val prev = db.mealPlanEntrys().range(familyId, ymd.format(prevStart), ymd.format(weekStart - 86_400_000L))
        prev.forEach { e ->
            val d = ymd.parse(e.date) ?: return@forEach
            val newDate = ymd.format(d.time + 7 * 86_400_000L)
            val existing = db.mealPlanEntrys().range(familyId, newDate, newDate).firstOrNull { it.slot == e.slot }
            if (existing == null) {
                db.mealPlanEntrys().upsert(e.copy(id = uuid(), date = newDate, updatedAt = now(), dirty = true))
            }
        }
        onLocalChange()
    }

    /** All ingredients of the planned recipes in a date range, scaled by each entry's servings. */
    suspend fun plannedIngredients(familyId: String, from: String, to: String): List<Ingredient> {
        val entries = db.mealPlanEntrys().range(familyId, from, to)
        return entries.flatMap { e ->
            val r = e.recipeId?.let { db.recipes().get(it) } ?: return@flatMap emptyList()
            val f = e.servings.toDouble() / r.servings.coerceAtLeast(1)
            RecipeText.ingredientsFromJson(r.ingredients).map { it.copy(quantity = it.quantity * f) }
        }.groupBy { it.name.lowercase() to it.unit }.map { (_, list) -> list.first().copy(quantity = list.sumOf { it.quantity }) }
    }

    // ---------- packed meals ----------

    fun observeProfiles(familyId: String) = db.mealProfiles().observeForFamily(familyId)
    fun observePacked(familyId: String, from: String, to: String) = db.packedMeals().observeRange(familyId, from, to)

    suspend fun saveProfile(p: MealProfileEntity) {
        db.mealProfiles().upsert(p.copy(updatedAt = now(), dirty = true))
        onLocalChange()
    }

    suspend fun deleteProfile(id: String) {
        val p = db.mealProfiles().get(id) ?: return
        db.mealProfiles().upsert(p.copy(deletedAt = now(), updatedAt = now(), dirty = true))
        onLocalChange()
    }

    suspend fun savePacked(p: PackedMealEntity) {
        db.packedMeals().upsert(p.copy(updatedAt = now(), dirty = true))
        onLocalChange()
    }

    /**
     * PCK-3 / PCK-7: plans the next 7 days for a profile from its list of liked foods, without
     * repeating an item more than [MealProfileEntity.maxRepeat] times, and avoiding food that
     * often came back uneaten. Works offline; the AI plan is optional.
     */
    suspend fun planWeek(profile: MealProfileEntity, perDay: Int = 3, aiDays: Map<String, List<String>>? = null): Int {
        val likes = profile.likes.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (likes.isEmpty() && aiDays.isNullOrEmpty()) return 0
        val today = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 12) }
        val from = ymd.format(today.time)
        val history = db.packedMeals().forProfile(profile.id, ymd.format(today.timeInMillis - 28 * 86_400_000L), ymd.format(today.timeInMillis + 7 * 86_400_000L))
        val uneaten = history.filter { it.status == "UNEATEN" }.flatMap { it.items.lines() }.groupingBy { it.trim() }.eachCount()
        val pool = likes.filter { (uneaten[it] ?: 0) < 2 }.ifEmpty { likes }
        val used = mutableMapOf<String, Int>()
        var made = 0
        var cursor = 0
        for (i in 0 until 7) {
            val day = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, i) }
            val bit = 1 shl (day.get(Calendar.DAY_OF_WEEK) - 1)
            if (profile.weekdays and bit == 0) continue
            val date = ymd.format(day.time)
            if (history.any { it.date == date && it.status != "PLANNED" }) continue
            val items = aiDays?.get(date) ?: run {
                val picked = mutableListOf<String>()
                var guard = 0
                while (picked.size < perDay.coerceAtMost(pool.size) && guard++ < pool.size * 3) {
                    val item = pool[cursor++ % pool.size]
                    if (item !in picked && (used[item] ?: 0) < profile.maxRepeat.coerceAtLeast(1)) {
                        picked += item
                        used[item] = (used[item] ?: 0) + 1
                    }
                }
                picked
            }
            if (items.isEmpty()) continue
            val existing = history.firstOrNull { it.date == date }
            db.packedMeals().upsert(
                (existing ?: PackedMealEntity(id = uuid(), familyId = profile.familyId, profileId = profile.id, date = date))
                    .copy(items = items.joinToString("\n"), status = "PLANNED", updatedAt = now(), dirty = true)
            )
            made++
        }
        if (made > 0) onLocalChange()
        return made.also { if (from.isEmpty()) Unit }
    }

    suspend fun packedOn(date: String) = db.packedMeals().onDate(date)
    suspend fun profile(id: String) = db.mealProfiles().get(id)
}
