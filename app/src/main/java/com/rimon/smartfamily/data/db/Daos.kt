package com.rimon.smartfamily.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles WHERE id = :id")
    fun observe(id: String): Flow<ProfileEntity?>

    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun get(id: String): ProfileEntity?

    @Upsert suspend fun upsert(profile: ProfileEntity)
    @Upsert suspend fun upsertAll(items: List<ProfileEntity>)

    @Query("SELECT * FROM profiles") suspend fun all(): List<ProfileEntity>
    @Query("SELECT * FROM profiles WHERE dirty = 1") suspend fun dirty(): List<ProfileEntity>
    @Query("UPDATE profiles SET dirty = 0 WHERE id IN (:ids)") suspend fun markClean(ids: List<String>)
    @Query("DELETE FROM profiles") suspend fun clear()
    @Query("UPDATE profiles SET id = :newId WHERE id = :oldId") suspend fun reassign(oldId: String, newId: String)
}

@Dao
interface FamilyDao {
    @Query(
        """SELECT f.* FROM families f
           WHERE f.deletedAt IS NULL AND (
             f.id IN (SELECT familyId FROM family_members WHERE userId = :userId AND deletedAt IS NULL AND status = 'ACTIVE')
             OR f.id IN (SELECT s.familyId FROM family_shares s WHERE s.status = 'ACCEPTED' AND s.deletedAt IS NULL
                         AND s.targetFamilyId IN (SELECT familyId FROM family_members WHERE userId = :userId AND deletedAt IS NULL AND status = 'ACTIVE')))
           ORDER BY f.createdAt"""
    )
    fun observeForUser(userId: String): Flow<List<FamilyEntity>>

    @Query("SELECT * FROM families WHERE id = :id")
    fun observe(id: String): Flow<FamilyEntity?>

    @Query("SELECT * FROM families WHERE id = :id") suspend fun get(id: String): FamilyEntity?
    @Upsert suspend fun upsert(family: FamilyEntity)
    @Upsert suspend fun upsertAll(items: List<FamilyEntity>)
    @Query("SELECT * FROM families") suspend fun all(): List<FamilyEntity>
    @Query("SELECT * FROM families WHERE dirty = 1") suspend fun dirty(): List<FamilyEntity>
    @Query("UPDATE families SET dirty = 0 WHERE id IN (:ids)") suspend fun markClean(ids: List<String>)
    @Query("DELETE FROM families") suspend fun clear()
    @Query("UPDATE families SET ownerId = :newId WHERE ownerId = :oldId") suspend fun reassignOwner(oldId: String, newId: String)
}

@Dao
interface MemberDao {
    @Query("SELECT * FROM family_members WHERE familyId = :familyId AND deletedAt IS NULL ORDER BY joinedAt")
    fun observeForFamily(familyId: String): Flow<List<MemberEntity>>

    @Query("SELECT * FROM family_members WHERE familyId = :familyId AND userId = :userId")
    suspend fun find(familyId: String, userId: String): MemberEntity?

    @Query("SELECT * FROM family_members WHERE familyId = :familyId AND userId = :userId")
    fun observeMembership(familyId: String, userId: String): Flow<MemberEntity?>

    @Query("SELECT * FROM family_members WHERE id = :id") suspend fun get(id: String): MemberEntity?
    @Upsert suspend fun upsert(member: MemberEntity)
    @Upsert suspend fun upsertAll(items: List<MemberEntity>)
    @Query("SELECT * FROM family_members") suspend fun all(): List<MemberEntity>
    @Query("SELECT * FROM family_members WHERE dirty = 1") suspend fun dirty(): List<MemberEntity>
    @Query("UPDATE family_members SET dirty = 0 WHERE id IN (:ids)") suspend fun markClean(ids: List<String>)
    @Query("DELETE FROM family_members") suspend fun clear()
    @Query("UPDATE family_members SET userId = :newId WHERE userId = :oldId") suspend fun reassign(oldId: String, newId: String)
}

@Dao
interface InviteDao {
    @Query("SELECT * FROM invites WHERE familyId = :familyId AND deletedAt IS NULL AND usedBy IS NULL AND expiresAt > :now ORDER BY expiresAt DESC")
    fun observeActive(familyId: String, now: Long): Flow<List<InviteEntity>>

    @Upsert suspend fun upsert(invite: InviteEntity)
    @Upsert suspend fun upsertAll(items: List<InviteEntity>)
    @Query("SELECT * FROM invites") suspend fun all(): List<InviteEntity>
    @Query("SELECT * FROM invites WHERE dirty = 1") suspend fun dirty(): List<InviteEntity>
    @Query("UPDATE invites SET dirty = 0 WHERE code IN (:ids)") suspend fun markClean(ids: List<String>)
    @Query("DELETE FROM invites") suspend fun clear()
    @Query("UPDATE invites SET createdBy = :newId WHERE createdBy = :oldId") suspend fun reassign(oldId: String, newId: String)
}

@Dao
interface ModuleSettingDao {
    @Query("SELECT * FROM module_settings WHERE familyId = :familyId")
    fun observeForFamily(familyId: String): Flow<List<ModuleSettingEntity>>

    @Upsert suspend fun upsert(setting: ModuleSettingEntity)
    @Upsert suspend fun upsertAll(items: List<ModuleSettingEntity>)
    @Query("SELECT * FROM module_settings") suspend fun all(): List<ModuleSettingEntity>
    @Query("SELECT * FROM module_settings WHERE dirty = 1") suspend fun dirty(): List<ModuleSettingEntity>
    @Query("UPDATE module_settings SET dirty = 0 WHERE id IN (:ids)") suspend fun markClean(ids: List<String>)
    @Query("DELETE FROM module_settings") suspend fun clear()
}
