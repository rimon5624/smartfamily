package com.rimon.smartfamily.data.repo

import androidx.room.withTransaction
import com.rimon.smartfamily.data.db.AppDatabase
import com.rimon.smartfamily.data.db.FamilyEntity
import com.rimon.smartfamily.data.db.InviteEntity
import com.rimon.smartfamily.data.db.MemberEntity
import com.rimon.smartfamily.data.db.ModuleSettingEntity
import com.rimon.smartfamily.data.db.ProfileEntity
import com.rimon.smartfamily.data.prefs.SettingsStore
import com.rimon.smartfamily.model.AppModule
import com.rimon.smartfamily.model.MemberStatus
import com.rimon.smartfamily.model.Role
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.security.SecureRandom
import java.util.UUID

/** Effective state of one module for the current user in one family. */
data class ModuleState(
    val module: AppModule,
    val familyEnabled: Boolean,
    val userEnabled: Boolean,
) {
    /** TOG-1: the user level can only narrow the family level. */
    val effective: Boolean get() = familyEnabled && userEnabled
}

class FamilyRepository(
    private val db: AppDatabase,
    private val settings: SettingsStore,
    private val onLocalChange: () -> Unit,
) {
    private fun now() = System.currentTimeMillis()

    suspend fun ensureLocalUser(): String {
        val current = settings.current().userId
        if (current.isNotBlank()) return current
        val id = UUID.randomUUID().toString()
        settings.setUserId(id)
        return id
    }

    fun observeProfile(userId: String) = db.profiles().observe(userId)

    suspend fun setDisplayName(name: String) {
        val uid = ensureLocalUser()
        val existing = db.profiles().get(uid)
        db.profiles().upsert(
            (existing ?: ProfileEntity(id = uid, displayName = name))
                .copy(displayName = name, updatedAt = now(), dirty = true)
        )
        onLocalChange()
    }

    fun observeFamilies(userId: String): Flow<List<FamilyEntity>> = db.families().observeForUser(userId)

    fun observeFamily(id: String) = db.families().observe(id)

    suspend fun createFamily(name: String, enabledModules: Set<AppModule>? = null): FamilyEntity {
        val uid = ensureLocalUser()
        val displayName = db.profiles().get(uid)?.displayName ?: ""
        val family = FamilyEntity(id = UUID.randomUUID().toString(), name = name.trim(), ownerId = uid)
        db.withTransaction {
            db.families().upsert(family)
            db.members().upsert(
                MemberEntity(
                    id = UUID.randomUUID().toString(),
                    familyId = family.id,
                    userId = uid,
                    displayName = displayName,
                    role = Role.OWNER.name,
                )
            )
            val chosen = enabledModules ?: AppModule.entries.filter { it.defaultOn }.toSet()
            AppModule.entries.forEach { m ->
                db.moduleSettings().upsert(
                    ModuleSettingEntity(
                        id = ModuleSettingEntity.idFor(family.id, "", m.key),
                        familyId = family.id,
                        userId = "",
                        module = m.key,
                        enabled = m in chosen,
                    )
                )
            }
        }
        if (settings.current().activeFamilyId.isBlank()) settings.setActiveFamily(family.id)
        onLocalChange()
        return family
    }

    suspend fun renameFamily(id: String, name: String) {
        val f = db.families().get(id) ?: return
        db.families().upsert(f.copy(name = name.trim(), updatedAt = now(), dirty = true))
        onLocalChange()
    }

    suspend fun setKashrut(id: String, kashrut: String) {
        val f = db.families().get(id) ?: return
        db.families().upsert(f.copy(kashrut = kashrut, updatedAt = now(), dirty = true))
        onLocalChange()
    }

    /** Owner only; a soft delete so the deletion reaches every member's phone. */
    suspend fun deleteFamily(id: String) {
        val f = db.families().get(id) ?: return
        db.families().upsert(f.copy(deletedAt = now(), updatedAt = now(), dirty = true))
        if (settings.current().activeFamilyId == id) settings.setActiveFamily("")
        onLocalChange()
    }

    fun observeMembers(familyId: String) = db.members().observeForFamily(familyId)

    fun observeMyMembership(familyId: String, userId: String) = db.members().observeMembership(familyId, userId)

    suspend fun setRole(memberId: String, role: Role) {
        val m = db.members().get(memberId) ?: return
        if (m.role == Role.OWNER.name) return // ownership transfer is a separate, explicit action
        db.members().upsert(m.copy(role = role.name, updatedAt = now(), dirty = true))
        onLocalChange()
    }

    suspend fun approveMember(memberId: String) {
        val m = db.members().get(memberId) ?: return
        db.members().upsert(m.copy(status = MemberStatus.ACTIVE.name, updatedAt = now(), dirty = true))
        onLocalChange()
    }

    suspend fun removeMember(memberId: String) {
        val m = db.members().get(memberId) ?: return
        if (m.role == Role.OWNER.name) return
        db.members().upsert(m.copy(deletedAt = now(), updatedAt = now(), dirty = true))
        onLocalChange()
    }

    /** Adds a child profile managed by a parent, without an email (FAM-7). */
    suspend fun addManagedChild(familyId: String, name: String) {
        db.members().upsert(
            MemberEntity(
                id = UUID.randomUUID().toString(),
                familyId = familyId,
                userId = "child-" + UUID.randomUUID().toString(),
                displayName = name.trim(),
                role = Role.CHILD.name,
            )
        )
        onLocalChange()
    }

    fun observeInvites(familyId: String) = db.invites().observeActive(familyId, now())

    /** FAM-1: single-use code, valid for 48 hours. */
    suspend fun createInvite(familyId: String, role: Role = Role.MEMBER): InviteEntity {
        val uid = ensureLocalUser()
        val invite = InviteEntity(
            code = newInviteCode(),
            familyId = familyId,
            createdBy = uid,
            role = role.name,
            expiresAt = now() + 48L * 60 * 60 * 1000,
        )
        db.invites().upsert(invite)
        onLocalChange()
        return invite
    }

    suspend fun revokeInvite(invite: InviteEntity) {
        db.invites().upsert(invite.copy(deletedAt = now(), updatedAt = now(), dirty = true))
        onLocalChange()
    }

    fun observeModules(familyId: String, userId: String): Flow<List<ModuleState>> =
        db.moduleSettings().observeForFamily(familyId).map { rows ->
            AppModule.entries.map { m ->
                val fam = rows.firstOrNull { it.userId == "" && it.module == m.key }?.enabled ?: m.defaultOn
                val mine = rows.firstOrNull { it.userId == userId && it.module == m.key }?.enabled ?: true
                ModuleState(m, fam, mine)
            }
        }

    suspend fun setModule(familyId: String, module: AppModule, enabled: Boolean, userLevelFor: String?) {
        val uid = userLevelFor ?: ""
        db.moduleSettings().upsert(
            ModuleSettingEntity(
                id = ModuleSettingEntity.idFor(familyId, uid, module.key),
                familyId = familyId,
                userId = uid,
                module = module.key,
                enabled = enabled,
                updatedAt = now(),
                dirty = true,
            )
        )
        onLocalChange()
    }

    /**
     * When a local-only user signs in for the first time, every row that used the temporary
     * local id is moved to the Supabase account id, so nothing is lost.
     */
    suspend fun adoptAccountId(newId: String) {
        val old = settings.current().userId
        if (old.isBlank() || old == newId) {
            settings.setUserId(newId)
            return
        }
        db.withTransaction {
            if (db.profiles().get(newId) == null) db.profiles().reassign(old, newId)
            db.families().reassignOwner(old, newId)
            db.members().reassign(old, newId)
            db.invites().reassign(old, newId)
            db.listItems().reassign(old, newId)
            db.purchases().reassign(old, newId)
            db.inventory().reassign(old, newId)
        }
        settings.setUserId(newId)
        onLocalChange()
    }

    fun observeActiveFamily(userId: String): Flow<FamilyEntity?> =
        combine(observeFamilies(userId), settings.settings) { list, s ->
            list.firstOrNull { it.id == s.activeFamilyId } ?: list.firstOrNull()
        }

    suspend fun setActiveFamily(id: String) = settings.setActiveFamily(id)

    private fun newInviteCode(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // no 0/O/1/I
        val rnd = SecureRandom()
        return (1..8).map { alphabet[rnd.nextInt(alphabet.length)] }.joinToString("")
    }
}
