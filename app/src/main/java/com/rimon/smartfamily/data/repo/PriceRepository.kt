package com.rimon.smartfamily.data.repo

import com.rimon.smartfamily.data.db.AppDatabase
import com.rimon.smartfamily.data.db.ListItemEntity
import com.rimon.smartfamily.data.db.PriceItemEntity
import com.rimon.smartfamily.data.db.PriceStoreEntity
import com.rimon.smartfamily.smart.Chain
import com.rimon.smartfamily.smart.PriceFetcher

/** One shopping-list line priced at one store. */
data class PricedLine(val item: ListItemEntity, val match: PriceItemEntity?, val exact: Boolean) {
    val total get() = (match?.price ?: 0.0) * item.quantity.coerceAtLeast(1.0)
}

/** The whole list at one store. */
data class StoreBasket(val store: PriceStoreEntity, val lines: List<PricedLine>) {
    val found get() = lines.count { it.match != null }
    val total get() = lines.sumOf { it.total }
}

class PriceRepository(private val db: AppDatabase) {
    private val fetcher = PriceFetcher()
    private val dao get() = db.prices()

    fun stores(chainKey: String) = dao.observeStores(chainKey)
    fun selected() = dao.observeSelected()
    suspend fun hasStores(chainKey: String) = dao.storeCount(chainKey) > 0

    suspend fun loadStores(chain: Chain): Int {
        val list = fetcher.stores(chain)
        dao.upsertStores(list.map { PriceStoreEntity(chain.key, it.id, it.name, it.city, it.address) })
        return list.size
    }

    suspend fun select(store: PriceStoreEntity, on: Boolean) = dao.setSelected(store.chainKey, store.storeId, on)

    /** Downloads one store's full price file and replaces its cached prices. */
    suspend fun refresh(chain: Chain, store: PriceStoreEntity): Int {
        val batch = ArrayList<PriceItemEntity>(1000)
        var count = 0
        dao.clearStore(store.chainKey, store.storeId)
        fetcher.prices(chain, store.storeId) { r ->
            batch += PriceItemEntity(store.chainKey, store.storeId, r.code, r.name, normalize(r.name), r.price, r.unitPrice, r.unit, r.manufacturer, r.updated)
            count++
            if (batch.size >= 1000) { val b = batch.toList(); batch.clear(); kotlinx.coroutines.runBlocking { dao.upsertItems(b) } }
        }
        if (batch.isNotEmpty()) dao.upsertItems(batch)
        dao.markFetched(store.chainKey, store.storeId, System.currentTimeMillis(), count)
        return count
    }

    /** Barcode first; otherwise the closest name, preferring the brand the family asked for. */
    suspend fun compare(items: List<ListItemEntity>, stores: List<PriceStoreEntity>): List<StoreBasket> = stores.map { s ->
        StoreBasket(s, items.map { i ->
            val code = i.barcode.trimStart('0')
            val exact = if (code.isNotEmpty()) dao.byCode(code).firstOrNull { it.chainKey == s.chainKey && it.storeId == s.storeId } else null
            if (exact != null) PricedLine(i, exact, true) else PricedLine(i, bestByName(s, i), false)
        })
    }

    private suspend fun bestByName(s: PriceStoreEntity, i: ListItemEntity): PriceItemEntity? {
        val words = normalize(i.name + " " + i.brand).split(' ').filter { it.length >= 2 }
        if (words.isEmpty()) return null
        val first = words.maxBy { it.length }
        val candidates = dao.byToken(s.chainKey, s.storeId, first)
        return candidates.map { c ->
            val cw = c.nameNorm.split(' ').toSet()
            val hits = words.count { w -> cw.any { it.startsWith(w) } }
            val brandHit = i.brand.isNotBlank() && c.nameNorm.contains(normalize(i.brand))
            c to (hits * 10 + (if (brandHit) 5 else 0) - c.nameNorm.length / 20.0 - (if (c.price <= 0) 100 else 0))
        }.filter { it.second >= 10 }.maxByOrNull { it.second }?.first
    }

    companion object {
        fun normalize(s: String) = s.lowercase()
            .replace(Regex("[\"'׳״`.,()\\-/*+]"), " ")
            .replace(Regex("\\s+"), " ").trim()
    }
}
