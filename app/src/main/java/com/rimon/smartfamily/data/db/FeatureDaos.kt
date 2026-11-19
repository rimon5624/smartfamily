package com.rimon.smartfamily.data.db

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Feature DAOs: the standard methods come from the generated *Base interfaces. */

@Dao
interface ChoreDao : ChoreDaoBase {
    @Query("SELECT * FROM chores WHERE familyId = :familyId AND deletedAt IS NULL AND active = 1 ORDER BY nextDue")
    fun observeActive(familyId: String): Flow<List<ChoreEntity>>

    @Query("SELECT * FROM chores WHERE deletedAt IS NULL AND active = 1 AND nextDue > 0 AND nextDue <= :until")
    suspend fun dueBefore(until: Long): List<ChoreEntity>

    @Query("SELECT * FROM chores WHERE familyId = :familyId AND deletedAt IS NULL AND source = :source LIMIT 1")
    suspend fun findBySource(familyId: String, source: String): ChoreEntity?
}

@Dao
interface ChoreCompletionDao : ChoreCompletionDaoBase {
    @Query("SELECT * FROM chore_completions WHERE familyId = :familyId AND deletedAt IS NULL AND doneAt >= :since ORDER BY doneAt DESC")
    fun observeSince(familyId: String, since: Long): Flow<List<ChoreCompletionEntity>>

    @Query("SELECT * FROM chore_completions WHERE familyId = :familyId AND deletedAt IS NULL AND approved = 0 ORDER BY doneAt")
    fun observePending(familyId: String): Flow<List<ChoreCompletionEntity>>
}

data class PointsBalance(val userId: String, val total: Int)

@Dao
interface PointsEntryDao : PointsEntryDaoBase {
    @Query("SELECT userId, SUM(delta) AS total FROM points_entries WHERE familyId = :familyId AND deletedAt IS NULL GROUP BY userId")
    fun observeBalances(familyId: String): Flow<List<PointsBalance>>
}

@Dao
interface RewardDao : RewardDaoBase

@Dao
interface FamilyDateDao : FamilyDateDaoBase {
    @Query("SELECT * FROM family_dates WHERE deletedAt IS NULL")
    suspend fun allActive(): List<FamilyDateEntity>
}

@Dao
interface EventDao : EventDaoBase {
    @Query("SELECT * FROM events WHERE deletedAt IS NULL")
    suspend fun allActive(): List<EventEntity>
}

// ---------- 0.4 ----------

@Dao
interface RecipeDao : RecipeDaoBase {
    @Query("SELECT * FROM recipes WHERE familyId = :familyId AND deletedAt IS NULL ORDER BY favorite DESC, timesCooked DESC, title")
    fun observeSorted(familyId: String): Flow<List<RecipeEntity>>
}

@Dao
interface RecipeRatingDao : RecipeRatingDaoBase {
    @Query("SELECT * FROM recipe_ratings WHERE recipeId = :recipeId AND deletedAt IS NULL")
    fun observeForRecipe(recipeId: String): Flow<List<RecipeRatingEntity>>

    @Query("SELECT * FROM recipe_ratings WHERE recipeId = :recipeId AND userId = :userId AND deletedAt IS NULL LIMIT 1")
    suspend fun find(recipeId: String, userId: String): RecipeRatingEntity?
}

@Dao
interface MealPlanEntryDao : MealPlanEntryDaoBase {
    @Query("SELECT * FROM meal_plan_entries WHERE familyId = :familyId AND deletedAt IS NULL AND date >= :from AND date <= :to ORDER BY date")
    fun observeRange(familyId: String, from: String, to: String): Flow<List<MealPlanEntryEntity>>

    @Query("SELECT * FROM meal_plan_entries WHERE familyId = :familyId AND deletedAt IS NULL AND date >= :from AND date <= :to")
    suspend fun range(familyId: String, from: String, to: String): List<MealPlanEntryEntity>
}

@Dao
interface MealProfileDao : MealProfileDaoBase

@Dao
interface PackedMealDao : PackedMealDaoBase {
    @Query("SELECT * FROM packed_meals WHERE familyId = :familyId AND deletedAt IS NULL AND date >= :from AND date <= :to ORDER BY date")
    fun observeRange(familyId: String, from: String, to: String): Flow<List<PackedMealEntity>>

    @Query("SELECT * FROM packed_meals WHERE profileId = :profileId AND deletedAt IS NULL AND date >= :from AND date <= :to")
    suspend fun forProfile(profileId: String, from: String, to: String): List<PackedMealEntity>

    @Query("SELECT * FROM packed_meals WHERE deletedAt IS NULL AND date = :date")
    suspend fun onDate(date: String): List<PackedMealEntity>
}

// ---------- 0.5 ----------

@Dao
interface ApplianceDao : ApplianceDaoBase {
    @Query("SELECT * FROM appliances WHERE familyId = :familyId AND deletedAt IS NULL ORDER BY type, name")
    fun observeSorted(familyId: String): Flow<List<ApplianceEntity>>
}

@Dao
interface LaundryPresetDao : LaundryPresetDaoBase {
    @Query("SELECT * FROM laundry_presets WHERE applianceId = :applianceId AND deletedAt IS NULL ORDER BY name")
    fun observeFor(applianceId: String): Flow<List<LaundryPresetEntity>>
}
