package com.rimon.smartfamily.data.repo

import com.rimon.smartfamily.data.db.AppDatabase
import com.rimon.smartfamily.data.db.ApplianceEntity
import com.rimon.smartfamily.data.db.ChoreEntity
import com.rimon.smartfamily.data.db.LaundryPresetEntity
import com.rimon.smartfamily.smart.ApplianceProfile
import com.rimon.smartfamily.smart.ZmanimCity
import java.util.UUID

class ApplianceRepository(
    private val db: AppDatabase,
    private val homeLife: HomeLifeRepository,
    private val onLocalChange: () -> Unit,
) {
    private fun now() = System.currentTimeMillis()

    fun observeAll(familyId: String) = db.appliances().observeSorted(familyId)
    fun observeOne(id: String) = db.appliances().observeOne(id)
    fun observePresets(applianceId: String) = db.laundryPresets().observeFor(applianceId)

    suspend fun save(a: ApplianceEntity) {
        db.appliances().upsert(a.copy(updatedAt = now(), dirty = true))
        onLocalChange()
    }

    suspend fun delete(id: String) {
        val a = db.appliances().get(id) ?: return
        db.appliances().upsert(a.copy(deletedAt = now(), updatedAt = now(), dirty = true))
        onLocalChange()
    }

    suspend fun setProfile(id: String, profile: ApplianceProfile, source: String) {
        val a = db.appliances().get(id) ?: return
        db.appliances().upsert(
            a.copy(
                profile = ApplianceProfile.toJson(profile), profileSource = source,
                brand = a.brand.ifBlank { profile.brand }, model = a.model.ifBlank { profile.model },
                updatedAt = now(), dirty = true,
            )
        )
        onLocalChange()
    }

    suspend fun savePreset(p: LaundryPresetEntity) {
        db.laundryPresets().upsert(p.copy(updatedAt = now(), dirty = true))
        onLocalChange()
    }

    suspend fun deletePreset(id: String) {
        val p = db.laundryPresets().get(id) ?: return
        db.laundryPresets().upsert(p.copy(deletedAt = now(), updatedAt = now(), dirty = true))
        onLocalChange()
    }

    /** APL-17: each maintenance task becomes a recurring chore, once. Returns how many were added. */
    suspend fun addMaintenanceChores(a: ApplianceEntity, profile: ApplianceProfile, userId: String, city: ZmanimCity): Int {
        var n = 0
        profile.maintenance.forEachIndexed { i, m ->
            val source = "appliance:${a.id}:$i"
            if (db.chores().findBySource(a.familyId, source) != null) return@forEachIndexed
            val chore = ChoreEntity(
                id = UUID.randomUUID().toString(), familyId = a.familyId, title = "${a.name}: ${m.task}",
                category = "maintenance", schedule = Schedule.EVERY_N_DAYS.name, intervalDays = m.intervalDays.coerceIn(1, 3650),
                estimatedMinutes = 15, checklist = m.howTo, source = source, createdBy = userId,
            )
            homeLife.saveChore(chore, city)
            n++
        }
        return n
    }
}
