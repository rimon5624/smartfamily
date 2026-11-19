package com.rimon.smartfamily.data.repo

import androidx.room.withTransaction
import com.rimon.smartfamily.data.db.AppDatabase
import com.rimon.smartfamily.data.db.InventoryItemEntity
import com.rimon.smartfamily.data.db.ListItemEntity
import com.rimon.smartfamily.data.db.PurchaseEntity
import com.rimon.smartfamily.data.db.ShoppingListEntity
import com.rimon.smartfamily.model.ItemDraft
import com.rimon.smartfamily.model.ShopCategory
import com.rimon.smartfamily.model.StorageLocation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.util.UUID

/** An inventory item running below its minimum and not yet on any open list (INV-5, SHP-7). */
data class LowStock(val item: InventoryItemEntity, val missing: Double)

class ShoppingRepository(
    private val db: AppDatabase,
    private val onLocalChange: () -> Unit,
) {
    private fun now() = System.currentTimeMillis()
    private fun uuid() = UUID.randomUUID().toString()

    // ---------- lists ----------

    fun observeLists(familyId: String, templates: Boolean = false) = db.lists().observe(familyId, templates)
    fun observeList(id: String) = db.lists().observeOne(id)
    fun observeCounts(familyId: String) = db.listItems().observeCounts(familyId)
    fun observeItems(listId: String) = db.listItems().observeForList(listId)
    fun observeFrequent(familyId: String) = db.purchases().observeFrequent(familyId)

    suspend fun createList(familyId: String, name: String, userId: String, template: Boolean = false): ShoppingListEntity {
        val list = ShoppingListEntity(id = uuid(), familyId = familyId, name = name.trim(), isTemplate = template, createdBy = userId)
        db.lists().upsert(list)
        onLocalChange()
        return list
    }

    suspend fun ensureDefaultList(familyId: String, name: String, userId: String) {
        if (db.lists().all().none { it.familyId == familyId && !it.isTemplate && it.deletedAt == null }) {
            createList(familyId, name, userId)
        }
    }

    suspend fun renameList(id: String, name: String) = editList(id) { it.copy(name = name.trim()) }
    suspend fun deleteList(id: String) = editList(id) { it.copy(deletedAt = now()) }

    /** SHP-10: mark that this user is in the store now (or clear it). */
    suspend fun setShopping(id: String, userId: String?) =
        editList(id) { it.copy(shopperId = userId, shoppingSince = userId?.let { now() }) }

    private suspend fun editList(id: String, change: (ShoppingListEntity) -> ShoppingListEntity) {
        val l = db.lists().get(id) ?: return
        db.lists().upsert(change(l).copy(updatedAt = now(), dirty = true))
        onLocalChange()
    }

    // ---------- items ----------

    /**
     * Adds drafts to a list, merging with an open item of the same name (SHP-6): quantities add up
     * when the units match. Children's items wait for a parent's approval (SHP-8).
     */
    suspend fun addItems(listId: String, drafts: List<ItemDraft>, userId: String, asRequest: Boolean = false): Int {
        val list = db.lists().get(listId) ?: return 0
        var added = 0
        db.withTransaction {
            val existing = db.listItems().forList(listId).filter { !it.checked }.toMutableList()
            drafts.filter { it.name.isNotBlank() }.forEach { d ->
                val same = existing.firstOrNull { it.name.equals(d.name.trim(), ignoreCase = true) && it.unit == d.unit }
                if (same != null && !asRequest) {
                    val merged = same.copy(quantity = same.quantity + d.quantity, updatedAt = now(), dirty = true)
                    db.listItems().upsert(merged)
                    existing[existing.indexOf(same)] = merged
                } else {
                    val item = ListItemEntity(
                        id = uuid(), listId = listId, familyId = list.familyId, name = d.name.trim(),
                        quantity = d.quantity, unit = d.unit, category = d.category, brand = d.brand,
                        anyBrand = d.brand.isBlank(), barcode = d.barcode,
                        status = if (asRequest) "REQUESTED" else "ACTIVE", addedBy = userId,
                    )
                    db.listItems().upsert(item)
                    existing += item
                }
                added++
            }
        }
        onLocalChange()
        return added
    }

    suspend fun updateItem(item: ListItemEntity) {
        db.listItems().upsert(item.copy(updatedAt = now(), dirty = true))
        onLocalChange()
    }

    suspend fun toggle(itemId: String, userId: String) {
        val i = db.listItems().get(itemId) ?: return
        val on = !i.checked
        db.listItems().upsert(
            i.copy(checked = on, checkedBy = if (on) userId else null, checkedAt = if (on) now() else null, updatedAt = now(), dirty = true)
        )
        onLocalChange()
    }

    suspend fun approve(itemId: String) {
        val i = db.listItems().get(itemId) ?: return
        db.listItems().upsert(i.copy(status = "ACTIVE", updatedAt = now(), dirty = true))
        onLocalChange()
    }

    suspend fun deleteItem(itemId: String) {
        val i = db.listItems().get(itemId) ?: return
        db.listItems().upsert(i.copy(deletedAt = now(), updatedAt = now(), dirty = true))
        onLocalChange()
    }

    /** Copies a template (e.g. "Shabbat basics") into a list (SHP-13). */
    suspend fun applyTemplate(templateId: String, listId: String, userId: String): Int {
        val drafts = db.listItems().forList(templateId).filter { it.status == "ACTIVE" }.map {
            ItemDraft(it.name, it.quantity, it.unit, it.brand, it.category, it.barcode)
        }
        return addItems(listId, drafts, userId)
    }

    suspend fun saveAsTemplate(listId: String, name: String, userId: String) {
        val src = db.lists().get(listId) ?: return
        val t = createList(src.familyId, name, userId, template = true)
        val drafts = db.listItems().forList(listId).filter { it.status == "ACTIVE" }.map {
            ItemDraft(it.name, it.quantity, it.unit, it.brand, it.category, it.barcode)
        }
        addItems(t.id, drafts, userId)
    }

    /**
     * Ends a shopping trip (SHP-15): ticked items go to purchase history and, when the inventory
     * module is on, into the pantry; recurring items come back unticked, the rest leave the list.
     */
    suspend fun finishShopping(listId: String, userId: String, toInventory: Boolean): Int {
        val list = db.lists().get(listId) ?: return 0
        val bought = db.listItems().forList(listId).filter { it.checked && it.status == "ACTIVE" }
        db.withTransaction {
            db.purchases().upsertAll(bought.map {
                PurchaseEntity(
                    id = uuid(), familyId = list.familyId, name = it.name, category = it.category,
                    quantity = it.quantity, unit = it.unit, barcode = it.barcode, boughtBy = userId,
                )
            })
            bought.forEach { item ->
                db.listItems().upsert(
                    if (item.recurring) item.copy(checked = false, checkedBy = null, checkedAt = null, updatedAt = now(), dirty = true)
                    else item.copy(deletedAt = now(), updatedAt = now(), dirty = true)
                )
                if (toInventory) addToInventory(list.familyId, item, userId)
            }
            db.lists().upsert(list.copy(shopperId = null, shoppingSince = null, updatedAt = now(), dirty = true))
        }
        onLocalChange()
        return bought.size
    }

    private suspend fun addToInventory(familyId: String, item: ListItemEntity, userId: String) {
        val existing = db.inventory().findByName(familyId, item.name)
        if (existing != null && existing.unit == item.unit) {
            db.inventory().upsert(existing.copy(quantity = existing.quantity + item.quantity, updatedAt = now(), dirty = true))
        } else {
            db.inventory().upsert(
                InventoryItemEntity(
                    id = uuid(), familyId = familyId, name = item.name, quantity = item.quantity, unit = item.unit,
                    location = StorageLocation.forCategory(ShopCategory.fromKey(item.category)).key,
                    category = item.category, barcode = item.barcode, kashrut = item.kashrut, addedBy = userId,
                )
            )
        }
    }

    // ---------- inventory ----------

    fun observeInventory(familyId: String) = db.inventory().observe(familyId)

    fun observeLowStock(familyId: String): Flow<List<LowStock>> =
        combine(db.inventory().observe(familyId), db.listItems().observeOpenInFamily(familyId)) { inv, open ->
            val onList = open.map { it.name.lowercase() }.toSet()
            inv.filter { it.minQuantity > 0 && it.quantity < it.minQuantity && it.name.lowercase() !in onList }
                .map { LowStock(it, it.minQuantity - it.quantity) }
        }

    suspend fun addInventory(familyId: String, drafts: List<ItemDraft>, location: String?, userId: String): Int {
        var n = 0
        db.withTransaction {
            drafts.filter { it.name.isNotBlank() }.forEach { d ->
                val cat = ShopCategory.fromKey(d.category)
                db.inventory().upsert(
                    InventoryItemEntity(
                        id = uuid(), familyId = familyId, name = d.name.trim(), quantity = d.quantity, unit = d.unit,
                        location = location ?: StorageLocation.forCategory(cat).key, category = cat.key,
                        barcode = d.barcode, expiresAt = d.expiresAt, addedBy = userId,
                    )
                )
                n++
            }
        }
        onLocalChange()
        return n
    }

    suspend fun updateInventory(item: InventoryItemEntity) {
        db.inventory().upsert(item.copy(updatedAt = now(), dirty = true))
        onLocalChange()
    }

    /** INV-6: "used it" lowers the quantity by [amount]; an empty item stays so its minimum still works. */
    suspend fun useInventory(id: String, amount: Double = 1.0) {
        val i = db.inventory().get(id) ?: return
        db.inventory().upsert(i.copy(quantity = (i.quantity - amount).coerceAtLeast(0.0), updatedAt = now(), dirty = true))
        onLocalChange()
    }

    suspend fun deleteInventory(id: String) {
        val i = db.inventory().get(id) ?: return
        db.inventory().upsert(i.copy(deletedAt = now(), updatedAt = now(), dirty = true))
        onLocalChange()
    }

    suspend fun addLowStockToList(listId: String, low: List<LowStock>, userId: String) =
        addItems(
            listId,
            low.map { ItemDraft(it.item.name, it.missing.coerceAtLeast(1.0), it.item.unit, category = it.item.category, barcode = it.item.barcode) },
            userId,
        )
}
