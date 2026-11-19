package com.rimon.smartfamily.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ShoppingListDao {
    @Query("SELECT * FROM shopping_lists WHERE familyId = :familyId AND deletedAt IS NULL AND isTemplate = :templates ORDER BY createdAt")
    fun observe(familyId: String, templates: Boolean): Flow<List<ShoppingListEntity>>

    @Query("SELECT * FROM shopping_lists WHERE id = :id")
    fun observeOne(id: String): Flow<ShoppingListEntity?>

    @Query("SELECT * FROM shopping_lists WHERE id = :id") suspend fun get(id: String): ShoppingListEntity?
    @Upsert suspend fun upsert(item: ShoppingListEntity)
    @Upsert suspend fun upsertAll(items: List<ShoppingListEntity>)
    @Query("SELECT * FROM shopping_lists") suspend fun all(): List<ShoppingListEntity>
    @Query("SELECT * FROM shopping_lists WHERE dirty = 1") suspend fun dirty(): List<ShoppingListEntity>
    @Query("UPDATE shopping_lists SET dirty = 0 WHERE id IN (:ids)") suspend fun markClean(ids: List<String>)
    @Query("DELETE FROM shopping_lists") suspend fun clear()
}

/** A list with its open item count, for the lists overview. */
data class ListCount(val listId: String, val open: Int, val done: Int)

@Dao
interface ListItemDao {
    @Query("SELECT * FROM list_items WHERE listId = :listId AND deletedAt IS NULL ORDER BY checked, createdAt")
    fun observeForList(listId: String): Flow<List<ListItemEntity>>

    @Query(
        """SELECT listId, SUM(CASE WHEN checked = 0 THEN 1 ELSE 0 END) AS open,
                  SUM(CASE WHEN checked = 1 THEN 1 ELSE 0 END) AS done
           FROM list_items WHERE familyId = :familyId AND deletedAt IS NULL AND status = 'ACTIVE' GROUP BY listId"""
    )
    fun observeCounts(familyId: String): Flow<List<ListCount>>

    @Query("SELECT * FROM list_items WHERE listId = :listId AND deletedAt IS NULL")
    suspend fun forList(listId: String): List<ListItemEntity>

    /** Open items on any non-template list of the family, to avoid suggesting duplicates. */
    @Query(
        """SELECT i.* FROM list_items i JOIN shopping_lists l ON l.id = i.listId
           WHERE i.familyId = :familyId AND i.deletedAt IS NULL AND i.checked = 0 AND l.isTemplate = 0 AND l.deletedAt IS NULL"""
    )
    fun observeOpenInFamily(familyId: String): Flow<List<ListItemEntity>>

    @Query("SELECT * FROM list_items WHERE id = :id") suspend fun get(id: String): ListItemEntity?
    @Upsert suspend fun upsert(item: ListItemEntity)
    @Upsert suspend fun upsertAll(items: List<ListItemEntity>)
    @Query("SELECT * FROM list_items") suspend fun all(): List<ListItemEntity>
    @Query("SELECT * FROM list_items WHERE dirty = 1") suspend fun dirty(): List<ListItemEntity>
    @Query("UPDATE list_items SET dirty = 0 WHERE id IN (:ids)") suspend fun markClean(ids: List<String>)
    @Query("DELETE FROM list_items") suspend fun clear()
    @Query("UPDATE list_items SET addedBy = :newId WHERE addedBy = :oldId") suspend fun reassign(oldId: String, newId: String)
}

/** Frequently bought product names, for autocomplete and quick add. */
data class FrequentItem(val name: String, val category: String, val unit: String, val times: Int, val last: Long)

@Dao
interface PurchaseDao {
    @Query(
        """SELECT name, MAX(category) AS category, MAX(unit) AS unit, COUNT(*) AS times, MAX(boughtAt) AS last
           FROM purchase_history WHERE familyId = :familyId AND deletedAt IS NULL
           GROUP BY name ORDER BY times DESC, last DESC LIMIT :limit"""
    )
    fun observeFrequent(familyId: String, limit: Int = 300): Flow<List<FrequentItem>>

    @Upsert suspend fun upsertAll(items: List<PurchaseEntity>)
    @Query("SELECT * FROM purchase_history") suspend fun all(): List<PurchaseEntity>
    @Query("SELECT * FROM purchase_history WHERE dirty = 1") suspend fun dirty(): List<PurchaseEntity>
    @Query("UPDATE purchase_history SET dirty = 0 WHERE id IN (:ids)") suspend fun markClean(ids: List<String>)
    @Query("DELETE FROM purchase_history") suspend fun clear()
    @Query("UPDATE purchase_history SET boughtBy = :newId WHERE boughtBy = :oldId") suspend fun reassign(oldId: String, newId: String)
}

@Dao
interface InventoryDao {
    @Query("SELECT * FROM inventory_items WHERE familyId = :familyId AND deletedAt IS NULL ORDER BY CASE WHEN expiresAt IS NULL THEN 1 ELSE 0 END, expiresAt, name")
    fun observe(familyId: String): Flow<List<InventoryItemEntity>>

    @Query("SELECT * FROM inventory_items WHERE deletedAt IS NULL AND expiresAt IS NOT NULL AND expiresAt <= :until AND quantity > 0")
    suspend fun expiringBefore(until: Long): List<InventoryItemEntity>

    @Query("SELECT * FROM inventory_items WHERE familyId = :familyId AND deletedAt IS NULL AND name = :name LIMIT 1")
    suspend fun findByName(familyId: String, name: String): InventoryItemEntity?

    @Query("SELECT * FROM inventory_items WHERE id = :id") suspend fun get(id: String): InventoryItemEntity?
    @Upsert suspend fun upsert(item: InventoryItemEntity)
    @Upsert suspend fun upsertAll(items: List<InventoryItemEntity>)
    @Query("SELECT * FROM inventory_items") suspend fun all(): List<InventoryItemEntity>
    @Query("SELECT * FROM inventory_items WHERE dirty = 1") suspend fun dirty(): List<InventoryItemEntity>
    @Query("UPDATE inventory_items SET dirty = 0 WHERE id IN (:ids)") suspend fun markClean(ids: List<String>)
    @Query("DELETE FROM inventory_items") suspend fun clear()
    @Query("UPDATE inventory_items SET addedBy = :newId WHERE addedBy = :oldId") suspend fun reassign(oldId: String, newId: String)
}
