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
