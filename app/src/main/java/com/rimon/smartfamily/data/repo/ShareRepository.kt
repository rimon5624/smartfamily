package com.rimon.smartfamily.data.repo

import com.rimon.smartfamily.data.db.AppDatabase
import com.rimon.smartfamily.data.db.FamilyEntity
import com.rimon.smartfamily.data.db.FamilyShareEntity
import com.rimon.smartfamily.model.AppModule
import com.rimon.smartfamily.sync.SupabaseApi
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID

/** Something that can be shared on its own (a list, a recipe, a trip, a chat...). */
data class ShareItem(val id: String, val title: String)

/**
 * 0.9 sharing between families. An admin offers a module or one item, read-only or editable;
 * the other family's admin accepts or declines (server function decide_share).
 */
class ShareRepository(private val db: AppDatabase, private val api: SupabaseApi, private val onLocalChange: () -> Unit) {
    private fun now() = System.currentTimeMillis()

    fun involving(familyId: String) = db.familyShares().observeInvolving(familyId)

    /** Modules that can be shared (price comparison is per phone, so it is not on the list). */
    val shareable = AppModule.entries.filter { it.available && it != AppModule.PRICES }

    /** Items that can be shared on their own, per module; empty = whole module only. */
    suspend fun items(familyId: String, module: AppModule): List<ShareItem> = when (module) {
        AppModule.SHOPPING -> db.lists().observe(familyId, false).first().map { ShareItem(it.id, it.name) }
        AppModule.RECIPES -> db.recipes().observeForFamily(familyId).first().map { ShareItem(it.id, it.title) }
        AppModule.TRAVEL -> db.trips().observeForFamily(familyId).first().map { ShareItem(it.id, it.title) }
        AppModule.CALENDAR -> db.events().observeForFamily(familyId).first().map { ShareItem(it.id, it.title) } +
            db.familyDates().observeForFamily(familyId).first().map { ShareItem(it.id, it.title) }
        AppModule.CHORES -> db.chores().observeForFamily(familyId).first().map { ShareItem(it.id, it.title) }
        AppModule.POLLS -> db.polls().observeForFamily(familyId).first().map { ShareItem(it.id, it.question) }
        AppModule.APPLIANCES -> db.appliances().observeForFamily(familyId).first().map { ShareItem(it.id, it.name) }
        AppModule.PACKED_MEALS -> db.mealProfiles().observeForFamily(familyId).first().map { ShareItem(it.id, it.personName) }
        AppModule.HOME_BOOK -> db.homeEntrys().observeSection(familyId, "HOME").first().map { ShareItem(it.id, it.title) }
        AppModule.KNOWLEDGE -> db.homeEntrys().observeSection(familyId, "KNOWLEDGE").first().map { ShareItem(it.id, it.title) }
        AppModule.CHAT -> db.chats().observeForFamily(familyId).first().filter { it.kind != "FAMILY" }.map { ShareItem(it.id, it.title.ifBlank { it.kind }) }
        else -> emptyList()
    }

    /** The other family gives its link code (its family id); we look up only its name. */
    suspend fun familyName(code: String): String? {
        val raw = api.rpc("sf_family_name", buildJsonObject { put("fid", code.trim()) }).trim()
        return raw.trim('"').takeIf { it.isNotBlank() && it != "null" }
    }

    suspend fun offer(source: FamilyEntity, targetCode: String, module: AppModule, item: ShareItem?, canWrite: Boolean, userId: String) {
        val target = targetCode.trim()
        require(target != source.id) { "same family" }
        val name = familyName(target) ?: throw IllegalArgumentException("family not found")
        db.familyShares().upsert(
            FamilyShareEntity(
                id = UUID.randomUUID().toString(), familyId = source.id, targetFamilyId = target, sourceName = source.name, targetName = name,
                module = module.key, itemId = item?.id.orEmpty(), itemTitle = item?.title.orEmpty(),
                canWrite = canWrite || module == AppModule.CHAT, status = "PENDING", createdBy = userId,
            )
        )
        onLocalChange()
    }

    suspend fun decide(share: FamilyShareEntity, accept: Boolean) {
        api.rpc("decide_share", buildJsonObject { put("p_id", share.id); put("p_accept", JsonPrimitive(accept)) })
        db.familyShares().upsert(share.copy(status = if (accept) "ACCEPTED" else "DECLINED", dirty = false))
        onLocalChange()
    }

    suspend fun revoke(share: FamilyShareEntity) {
        db.familyShares().upsert(share.copy(deletedAt = now(), updatedAt = now(), dirty = true))
        onLocalChange()
    }
}
