package com.rimon.smartfamily.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/** A shopping list, or a reusable template when [isTemplate] is true (SHP-1, SHP-13). */
@Serializable
@Entity(tableName = "shopping_lists", indices = [Index("familyId")])
data class ShoppingListEntity(
    @PrimaryKey val id: String,
    @SerialName("family_id") val familyId: String,
    val name: String,
    val kind: String = "supermarket",
    @SerialName("is_template") val isTemplate: Boolean = false,
    /** SHP-10: who is in the store right now, and since when. */
    @SerialName("shopper_id") val shopperId: String? = null,
    @SerialName("shopping_since") val shoppingSince: Long? = null,
    @SerialName("created_by") val createdBy: String,
    @SerialName("created_at") val createdAt: Long = System.currentTimeMillis(),
    @SerialName("updated_at") val updatedAt: Long = System.currentTimeMillis(),
    @SerialName("deleted_at") val deletedAt: Long? = null,
    @Transient val dirty: Boolean = true,
)

/** SHP-12: one line of a list. status REQUESTED = added by a child, waiting for a parent (SHP-8). */
@Serializable
@Entity(tableName = "list_items", indices = [Index("listId"), Index("familyId")])
data class ListItemEntity(
    @PrimaryKey val id: String,
    @SerialName("list_id") val listId: String,
    @SerialName("family_id") val familyId: String,
    val name: String,
    val quantity: Double = 1.0,
    val unit: String = "",
    val category: String = "other",
    val brand: String = "",
    @SerialName("any_brand") val anyBrand: Boolean = true,
    val note: String = "",
    val kashrut: String = "",
    val barcode: String = "",
    val status: String = "ACTIVE",
    val recurring: Boolean = false,
    val checked: Boolean = false,
    @SerialName("checked_by") val checkedBy: String? = null,
    @SerialName("checked_at") val checkedAt: Long? = null,
    @SerialName("added_by") val addedBy: String,
    @SerialName("created_at") val createdAt: Long = System.currentTimeMillis(),
    @SerialName("updated_at") val updatedAt: Long = System.currentTimeMillis(),
    @SerialName("deleted_at") val deletedAt: Long? = null,
    @Transient val dirty: Boolean = true,
)

/** SHP-16: what was bought, when and by whom; feeds autocomplete and frequent items. */
@Serializable
@Entity(tableName = "purchase_history", indices = [Index("familyId"), Index("name")])
data class PurchaseEntity(
    @PrimaryKey val id: String,
    @SerialName("family_id") val familyId: String,
    val name: String,
    val category: String = "other",
    val quantity: Double = 1.0,
    val unit: String = "",
    val barcode: String = "",
    @SerialName("bought_at") val boughtAt: Long = System.currentTimeMillis(),
    @SerialName("bought_by") val boughtBy: String,
    @SerialName("updated_at") val updatedAt: Long = System.currentTimeMillis(),
    @SerialName("deleted_at") val deletedAt: Long? = null,
    @Transient val dirty: Boolean = true,
)

/** INV-1..INV-7: an item in the fridge, freezer, pantry or another storage place. */
@Serializable
@Entity(tableName = "inventory_items", indices = [Index("familyId")])
data class InventoryItemEntity(
    @PrimaryKey val id: String,
    @SerialName("family_id") val familyId: String,
    val name: String,
    val location: String = "pantry",
    val quantity: Double = 1.0,
    val unit: String = "",
    @SerialName("min_quantity") val minQuantity: Double = 0.0,
    @SerialName("expires_at") val expiresAt: Long? = null,
    @SerialName("opened_at") val openedAt: Long? = null,
    val kashrut: String = "",
    val category: String = "other",
    val barcode: String = "",
    @SerialName("added_by") val addedBy: String,
    @SerialName("added_at") val addedAt: Long = System.currentTimeMillis(),
    @SerialName("updated_at") val updatedAt: Long = System.currentTimeMillis(),
    @SerialName("deleted_at") val deletedAt: Long? = null,
    @Transient val dirty: Boolean = true,
)
