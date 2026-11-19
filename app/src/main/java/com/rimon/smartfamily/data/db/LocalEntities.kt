package com.rimon.smartfamily.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** 2.0 PRC-7: one price per product, store and day, kept on this phone only. */
@Entity(tableName = "price_history", primaryKeys = ["chainKey", "storeId", "code", "day"], indices = [Index("code")])
data class PriceHistoryEntity(val chainKey: String, val storeId: String, val code: String, val day: String, val price: Double)

/** PRC-7: alert when a product drops to or below a price. */
@Entity(tableName = "price_watch")
data class PriceWatchEntity(@PrimaryKey val code: String, val name: String, val target: Double, val lastAlerted: Long = 0L)

/** PRC-3: current promotions of a store (from the chains' PromoFull files). */
@Entity(tableName = "price_promos", primaryKeys = ["chainKey", "storeId", "code"])
data class PromoEntity(val chainKey: String, val storeId: String, val code: String, val description: String, val price: Double, val minQty: Double, val until: String)

/** CHT-3: a message written now and sent later by this phone (local until it is sent). */
@Entity(tableName = "scheduled_messages")
data class ScheduledMessageEntity(@PrimaryKey val id: String, val chatId: String, val text: String, val sendAt: Long)

@Dao
interface PriceExtrasDao {
    @Upsert suspend fun upsertHistory(items: List<PriceHistoryEntity>)
    @Query("SELECT * FROM price_history WHERE code = :code ORDER BY day")
    suspend fun history(code: String): List<PriceHistoryEntity>
    @Query("SELECT * FROM price_watch") fun observeWatch(): Flow<List<PriceWatchEntity>>
    @Query("SELECT * FROM price_watch") suspend fun watch(): List<PriceWatchEntity>
    @Upsert suspend fun upsertWatch(w: PriceWatchEntity)
    @Query("DELETE FROM price_watch WHERE code = :code") suspend fun unwatch(code: String)
    @Upsert suspend fun upsertPromos(items: List<PromoEntity>)
    @Query("DELETE FROM price_promos WHERE chainKey = :chainKey AND storeId = :storeId") suspend fun clearPromos(chainKey: String, storeId: String)
    @Query("SELECT * FROM price_promos WHERE chainKey = :chainKey AND storeId = :storeId AND code = :code LIMIT 1")
    suspend fun promo(chainKey: String, storeId: String, code: String): PromoEntity?
}
