package com.rimon.smartfamily.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
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

// ---------- 0.6 ----------

@Dao
interface UserKeyDao : UserKeyDaoBase {
    @Query("SELECT * FROM user_keys WHERE familyId = :familyId AND userId IN (:userIds) AND deletedAt IS NULL")
    suspend fun forUsers(familyId: String, userIds: List<String>): List<UserKeyEntity>

    @Query("SELECT * FROM user_keys WHERE familyId = :familyId AND deletedAt IS NULL")
    fun observeFamily(familyId: String): Flow<List<UserKeyEntity>>

    /** 0.9: device keys of these people in any family visible to me (linked families included). */
    @Query("SELECT * FROM user_keys WHERE userId IN (:userIds) AND deletedAt IS NULL")
    suspend fun forUsersAny(userIds: List<String>): List<UserKeyEntity>

    @Query("SELECT * FROM user_keys WHERE deletedAt IS NULL")
    fun observeAll(): Flow<List<UserKeyEntity>>
}

@Dao
interface ChatDao : ChatDaoBase {
    @Query("SELECT * FROM chats WHERE familyId = :familyId AND deletedAt IS NULL ORDER BY lastMessageAt DESC")
    fun observeSorted(familyId: String): Flow<List<ChatEntity>>

    /** The family chat; if two phones ever created one each, everyone settles on the same (oldest id) one. */
    @Query("SELECT * FROM chats WHERE familyId = :familyId AND kind = 'FAMILY' AND deletedAt IS NULL ORDER BY id LIMIT 1")
    suspend fun familyChat(familyId: String): ChatEntity?

    @Query("SELECT * FROM chats WHERE deletedAt IS NULL")
    suspend fun allActive(): List<ChatEntity>

    /** 0.9: every chat I am in, in my families and in linked families. */
    @Query("SELECT * FROM chats WHERE deletedAt IS NULL AND (',' || members || ',') LIKE '%,' || :userId || ',%' ORDER BY lastMessageAt DESC")
    fun observeMine(userId: String): Flow<List<ChatEntity>>
}

@Dao
interface ChatKeyDao : ChatKeyDaoBase {
    @Query("SELECT * FROM chat_keys WHERE chatId = :chatId AND deviceId = :deviceId AND deletedAt IS NULL")
    suspend fun forDevice(chatId: String, deviceId: String): List<ChatKeyEntity>

    @Query("SELECT * FROM chat_keys WHERE chatId = :chatId AND epoch = :epoch AND deletedAt IS NULL")
    suspend fun forEpoch(chatId: String, epoch: Int): List<ChatKeyEntity>
}

@Dao
interface ChatMessageDao : ChatMessageDaoBase {
    @Query("SELECT * FROM chat_messages WHERE chatId = :chatId AND deletedAt IS NULL ORDER BY sentAt")
    fun observeChat(chatId: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE chatId = :chatId AND deletedAt IS NULL ORDER BY sentAt DESC LIMIT 1")
    fun observeLast(chatId: String): Flow<ChatMessageEntity?>

    @Query("SELECT * FROM chat_messages WHERE deletedAt IS NULL AND sentAt > :since AND senderId != :me ORDER BY sentAt")
    suspend fun incomingSince(since: Long, me: String): List<ChatMessageEntity>
}

@Dao
interface ChatReactionDao : ChatReactionDaoBase {
    @Query("SELECT * FROM chat_reactions WHERE chatId = :chatId AND deletedAt IS NULL")
    fun observeChat(chatId: String): Flow<List<ChatReactionEntity>>
}

@Dao
interface ChatReadDao : ChatReadDaoBase {
    @Query("SELECT * FROM chat_reads WHERE chatId = :chatId AND deletedAt IS NULL")
    fun observeChat(chatId: String): Flow<List<ChatReadEntity>>

    @Query("SELECT * FROM chat_reads WHERE familyId = :familyId AND userId = :userId AND deletedAt IS NULL")
    fun observeMine(familyId: String, userId: String): Flow<List<ChatReadEntity>>
}

// ---------- 0.7 ----------

@Dao
interface TripDao : TripDaoBase {
    @Query("SELECT * FROM trips WHERE familyId = :familyId AND deletedAt IS NULL ORDER BY startDate DESC")
    fun observeSorted(familyId: String): Flow<List<TripEntity>>
}

@Dao
interface TripItemDao : TripItemDaoBase {
    @Query("SELECT * FROM trip_items WHERE tripId = :tripId AND deletedAt IS NULL ORDER BY category, sort, title")
    fun observeTrip(tripId: String): Flow<List<TripItemEntity>>

    @Query("SELECT * FROM trip_items WHERE tripId = :tripId AND deletedAt IS NULL")
    suspend fun forTrip(tripId: String): List<TripItemEntity>
}

@Dao
interface BudgetEntryDao : BudgetEntryDaoBase {
    @Query("SELECT * FROM budget_entries WHERE familyId = :familyId AND deletedAt IS NULL AND (monthly = 1 OR (date >= :from AND date < :to)) ORDER BY date DESC")
    fun observeMonth(familyId: String, from: Long, to: Long): Flow<List<BudgetEntryEntity>>
}

@Dao
interface BudgetLimitDao : BudgetLimitDaoBase

@Dao
interface HomeEntryDao : HomeEntryDaoBase {
    @Query("SELECT * FROM home_entries WHERE familyId = :familyId AND section = :section AND deletedAt IS NULL ORDER BY pinned DESC, title")
    fun observeSection(familyId: String, section: String): Flow<List<HomeEntryEntity>>

    @Query("SELECT * FROM home_entries WHERE deletedAt IS NULL AND expiresAt IS NOT NULL AND expiresAt BETWEEN :from AND :to")
    suspend fun expiring(from: Long, to: Long): List<HomeEntryEntity>
}

@Dao
interface PollDao : PollDaoBase {
    @Query("SELECT * FROM polls WHERE familyId = :familyId AND deletedAt IS NULL ORDER BY closed, updatedAt DESC")
    fun observeSorted(familyId: String): Flow<List<PollEntity>>
}

@Dao
interface PollVoteDao : PollVoteDaoBase

// ---------- 0.9 ----------

@Dao
interface PeopleDao {
    /** Everyone visible on this phone: my families and families linked to them by a share. */
    @Query("SELECT * FROM family_members WHERE deletedAt IS NULL AND status = 'ACTIVE'")
    fun observeAllActive(): Flow<List<MemberEntity>>
}

@Dao
interface FamilyShareDao : FamilyShareDaoBase {
    /** Offers this family made, and offers made to it. */
    @Query("SELECT * FROM family_shares WHERE (familyId = :familyId OR targetFamilyId = :familyId) AND deletedAt IS NULL ORDER BY updatedAt DESC")
    fun observeInvolving(familyId: String): Flow<List<FamilyShareEntity>>

    /** Accepted shares from [sourceFamilyId] to any family the user belongs to. */
    @Query(
        """SELECT * FROM family_shares WHERE familyId = :sourceFamilyId AND status = 'ACCEPTED' AND deletedAt IS NULL
           AND targetFamilyId IN (SELECT familyId FROM family_members WHERE userId = :userId AND status = 'ACTIVE' AND deletedAt IS NULL)"""
    )
    fun observeSharedToMe(sourceFamilyId: String, userId: String): Flow<List<FamilyShareEntity>>
}

// ---------- 2.0: one search across the app ----------

data class SearchHit(val kind: String, val id: String, val title: String, val sub: String)

@Dao
interface SearchDao {
    @Query("""
        SELECT 'item' AS kind, listId AS id, name AS title, '' AS sub FROM list_items WHERE familyId = :f AND deletedAt IS NULL AND name LIKE '%' || :q || '%'
        UNION ALL SELECT 'recipe', id, title, '' FROM recipes WHERE familyId = :f AND deletedAt IS NULL AND (title LIKE '%' || :q || '%' OR ingredients LIKE '%' || :q || '%')
        UNION ALL SELECT 'home', id, title, section FROM home_entries WHERE familyId = :f AND deletedAt IS NULL AND (title LIKE '%' || :q || '%' OR body LIKE '%' || :q || '%' OR phone LIKE '%' || :q || '%' OR tags LIKE '%' || :q || '%')
        UNION ALL SELECT 'event', id, title, location FROM events WHERE familyId = :f AND deletedAt IS NULL AND (title LIKE '%' || :q || '%' OR location LIKE '%' || :q || '%')
        UNION ALL SELECT 'date', id, title, personName FROM family_dates WHERE familyId = :f AND deletedAt IS NULL AND (title LIKE '%' || :q || '%' OR personName LIKE '%' || :q || '%')
        UNION ALL SELECT 'trip', id, title, destination FROM trips WHERE familyId = :f AND deletedAt IS NULL AND (title LIKE '%' || :q || '%' OR destination LIKE '%' || :q || '%')
        UNION ALL SELECT 'poll', id, question, '' FROM polls WHERE familyId = :f AND deletedAt IS NULL AND question LIKE '%' || :q || '%'
        UNION ALL SELECT 'chore', id, title, '' FROM chores WHERE familyId = :f AND deletedAt IS NULL AND title LIKE '%' || :q || '%'
        UNION ALL SELECT 'pantry', id, name, location FROM inventory_items WHERE familyId = :f AND deletedAt IS NULL AND name LIKE '%' || :q || '%'
        UNION ALL SELECT 'appliance', id, name, brand FROM appliances WHERE familyId = :f AND deletedAt IS NULL AND (name LIKE '%' || :q || '%' OR brand LIKE '%' || :q || '%' OR model LIKE '%' || :q || '%')
        LIMIT 80
    """)
    suspend fun search(f: String, q: String): List<SearchHit>
}

// ---------- 2.0 ----------

@Dao
interface RecurringPaymentDao : RecurringPaymentDaoBase

@Dao
interface PocketEntryDao : PocketEntryDaoBase

@Dao
interface GoalDao : GoalDaoBase

@Dao
interface SplitExpenseDao : SplitExpenseDaoBase

@Dao
interface GrowthEntryDao : GrowthEntryDaoBase

@Dao
interface RepairDao : RepairDaoBase

@Dao
interface MeterReadingDao : MeterReadingDaoBase

@Dao
interface KitItemDao : KitItemDaoBase

@Dao
interface RoutineStepDao : RoutineStepDaoBase

@Dao
interface RoutineCheckDao : RoutineCheckDaoBase

@Dao
interface TreePersonDao : TreePersonDaoBase

@Dao
interface ScheduledMessageDao {
    @Query("SELECT * FROM scheduled_messages WHERE sendAt <= :now")
    suspend fun due(now: Long): List<ScheduledMessageEntity>
    @Query("SELECT * FROM scheduled_messages WHERE chatId = :chatId ORDER BY sendAt")
    fun observeChat(chatId: String): Flow<List<ScheduledMessageEntity>>
    @Upsert suspend fun upsert(m: ScheduledMessageEntity)
    @Query("DELETE FROM scheduled_messages WHERE id = :id") suspend fun delete(id: String)
}
