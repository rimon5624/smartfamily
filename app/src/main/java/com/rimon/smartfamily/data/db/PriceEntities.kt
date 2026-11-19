package com.rimon.smartfamily.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Price comparison (0.8): local to this phone, never synced. The data comes from the public
 * price files every large Israeli chain must publish (Food Law price transparency).
 */
@Entity(tableName = "price_stores", primaryKeys = ["chainKey", "storeId"])
data class PriceStoreEntity(
    val chainKey: String,
    val storeId: String,
    val name: String,
    val city: String = "",
    val address: String = "",
    val selected: Boolean = false,
    val lastFetched: Long = 0L,
    val itemCount: Int = 0,
)

@Entity(
    tableName = "price_items",
    primaryKeys = ["chainKey", "storeId", "code"],
    indices = [Index("code"), Index("nameNorm")],
)
data class PriceItemEntity(
    val chainKey: String,
    val storeId: String,
    val code: String,
    val name: String,
    val nameNorm: String,
    val price: Double,
    val unitPrice: Double = 0.0,
    val unit: String = "",
    val manufacturer: String = "",
    val updated: String = "",
)

@Dao
interface PriceDao {
    @Query("SELECT * FROM price_stores WHERE chainKey = :chainKey ORDER BY city, name")
    fun observeStores(chainKey: String): Flow<List<PriceStoreEntity>>

    @Query("SELECT * FROM price_stores WHERE selected = 1 ORDER BY chainKey, name")
    fun observeSelected(): Flow<List<PriceStoreEntity>>

    @Query("SELECT * FROM price_stores WHERE selected = 1")
    suspend fun selected(): List<PriceStoreEntity>

    @Query("SELECT COUNT(*) FROM price_stores WHERE chainKey = :chainKey")
    suspend fun storeCount(chainKey: String): Int

    @Upsert suspend fun upsertStores(items: List<PriceStoreEntity>)

    @Query("UPDATE price_stores SET selected = :selected WHERE chainKey = :chainKey AND storeId = :storeId")
    suspend fun setSelected(chainKey: String, storeId: String, selected: Boolean)

    @Query("UPDATE price_stores SET lastFetched = :at, itemCount = :count WHERE chainKey = :chainKey AND storeId = :storeId")
    suspend fun markFetched(chainKey: String, storeId: String, at: Long, count: Int)

    @Upsert suspend fun upsertItems(items: List<PriceItemEntity>)

    @Query("DELETE FROM price_items WHERE chainKey = :chainKey AND storeId = :storeId")
    suspend fun clearStore(chainKey: String, storeId: String)

    @Query("SELECT * FROM price_items WHERE code = :code")
    suspend fun byCode(code: String): List<PriceItemEntity>

    @Query("SELECT * FROM price_items WHERE chainKey = :chainKey AND storeId = :storeId AND nameNorm LIKE '%' || :token || '%' LIMIT 400")
    suspend fun byToken(chainKey: String, storeId: String, token: String): List<PriceItemEntity>
}
