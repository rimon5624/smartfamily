package com.rimon.smartfamily.data.repo

import com.rimon.smartfamily.data.db.AppDatabase
import com.rimon.smartfamily.data.db.ListItemEntity
import com.rimon.smartfamily.data.db.PriceItemEntity
import com.rimon.smartfamily.data.db.PriceStoreEntity
import com.rimon.smartfamily.smart.Chain
import com.rimon.smartfamily.smart.PriceFetcher

/** One shopping-list line priced at one store. */
data class PricedLine(val item: ListItemEntity, val match: PriceItemEntity?, val exact: Boolean, val promo: com.rimon.smartfamily.data.db.PromoEntity? = null) {
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
    private val extras get() = db.priceExtras()

    fun watches() = extras.observeWatch()
    suspend fun watch(code: String, name: String, target: Double) = extras.upsertWatch(com.rimon.smartfamily.data.db.PriceWatchEntity(code, name, target))
    suspend fun unwatch(code: String) = extras.unwatch(code)
    suspend fun history(code: String) = extras.history(code)
    suspend fun promo(chainKey: String, storeId: String, code: String) = extras.promo(chainKey, storeId, code)

    /** Watched products now at or below their target price somewhere (PRC-7). */
    suspend fun dueAlerts(): List<Pair<com.rimon.smartfamily.data.db.PriceWatchEntity, PriceItemEntity>> = extras.watch().mapNotNull { w ->
        dao.byCode(w.code).filter { it.price > 0 && it.price <= w.target }.minByOrNull { it.price }?.let { w to it }
    }
    suspend fun markAlerted(w: com.rimon.smartfamily.data.db.PriceWatchEntity) = extras.upsertWatch(w.copy(lastAlerted = System.currentTimeMillis()))

    fun stores(chainKey: String) = dao.observeStores(chainKey)
    fun selected() = dao.observeSelected()
    suspend fun hasStores(chainKey: String) = dao.storeCount(chainKey) > 0

    suspend fun check(chain: Chain) = fetcher.check(chain)

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
        // promotions (best effort; not every chain publishes them the same way)
        runCatching {
            extras.clearPromos(store.chainKey, store.storeId)
            val promos = ArrayList<com.rimon.smartfamily.data.db.PromoEntity>()
            fetcher.promos(chain, store.storeId) { p -> p.codes.take(200).forEach { promos += com.rimon.smartfamily.data.db.PromoEntity(store.chainKey, store.storeId, it, p.description, p.price, p.minQty, p.until) } }
            promos.chunked(1000).forEach { kotlinx.coroutines.runBlocking { extras.upsertPromos(it) } }
        }
        // PRC-7: today's price of watched products goes into their history
        val day = java.time.LocalDate.now().toString()
        val watched = extras.watch()
        val hist = watched.flatMap { w -> dao.byCode(w.code).filter { it.chainKey == store.chainKey && it.storeId == store.storeId } }
            .map { com.rimon.smartfamily.data.db.PriceHistoryEntity(it.chainKey, it.storeId, it.code, day, it.price) }
        if (hist.isNotEmpty()) extras.upsertHistory(hist)
        return count
    }

    /** Barcode first; otherwise the closest name, preferring the brand the family asked for. */
    suspend fun compare(items: List<ListItemEntity>, stores: List<PriceStoreEntity>): List<StoreBasket> = stores.map { s ->
        StoreBasket(s, items.map { i ->
            val code = i.barcode.trimStart('0')
            val exact = if (code.isNotEmpty()) dao.byCode(code).firstOrNull { it.chainKey == s.chainKey && it.storeId == s.storeId } else null
            val m = exact ?: bestByName(s, i)
            PricedLine(i, m, exact != null, m?.let { extras.promo(s.chainKey, s.storeId, it.code) })
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
