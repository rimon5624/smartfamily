package com.rimon.smartfamily.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rimon.smartfamily.R
import com.rimon.smartfamily.SmartFamilyApp
import com.rimon.smartfamily.data.db.PriceStoreEntity
import com.rimon.smartfamily.data.repo.StoreBasket
import com.rimon.smartfamily.smart.Chains
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PriceViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as SmartFamilyApp).container
    private val repo = c.prices
    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()
    private val _progress = MutableStateFlow<String?>(null)
    val progress = _progress.asStateFlow()
    private val _baskets = MutableStateFlow<List<StoreBasket>>(emptyList())
    val baskets = _baskets.asStateFlow()

    private fun launchSafe(block: suspend () -> Unit) = viewModelScope.launch {
        try { block() } catch (e: Exception) { _messages.tryEmit(explain(e)) } finally { _progress.value = null }
    }

    /** Plain-words reasons instead of raw network errors. */
    private fun explain(e: Throwable): UiMessage = when {
        e is kotlinx.coroutines.TimeoutCancellationException -> UiMessage.Res(R.string.prices_err_timeout)
        e is com.rimon.smartfamily.smart.PriceSourceException -> UiMessage.Res(when (e.why) {
            com.rimon.smartfamily.smart.PriceSourceException.Why.OVERNIGHT -> R.string.prices_err_overnight
            com.rimon.smartfamily.smart.PriceSourceException.Why.TIMEOUT -> R.string.prices_err_timeout
            else -> R.string.prices_err_nofiles
        })
        e is java.net.UnknownHostException || e is java.net.ConnectException || e is java.net.SocketTimeoutException -> UiMessage.Res(R.string.prices_err_network)
        else -> UiMessage.Text(e.message ?: e.javaClass.simpleName)
    }

    /** Source check: every chain, listing only. Key → null while running, "" ok-with-count text, or the reason. */
    val checks = MutableStateFlow<Map<String, Pair<Boolean, String>?>>(emptyMap())
    fun checkSources() = viewModelScope.launch {
        checks.value = Chains.all.associate { it.key to null }
        Chains.all.map { ch ->
            launch {
                val r = try {
                    val n = kotlinx.coroutines.withTimeout(40_000) { repo.check(ch) }
                    true to getApplication<Application>().getString(R.string.prices_check_ok, n)
                } catch (e: Exception) {
                    false to explain(e).let { m -> if (m is UiMessage.Res) getApplication<Application>().getString(m.id) else (m as UiMessage.Text).text }
                }
                checks.value = checks.value + (ch.key to r)
            }
        }
    }

    fun lists(familyId: String) = c.shopping.observeLists(familyId)
    fun stores(chainKey: String) = repo.stores(chainKey)
    val selected = repo.selected()

    fun loadStores(chainKey: String) = launchSafe {
        val chain = Chains.byKey(chainKey) ?: return@launchSafe
        _progress.value = chain.he
        val n = kotlinx.coroutines.withTimeout(60_000) { repo.loadStores(chain) }
        _messages.tryEmit(UiMessage.Res(R.string.prices_stores_loaded, n.toString()))
    }

    fun toggle(store: PriceStoreEntity) = launchSafe {
        repo.select(store, !store.selected)
        if (!store.selected && store.lastFetched == 0L) refreshOne(store)
    }

    private suspend fun refreshOne(s: PriceStoreEntity) {
        val chain = Chains.byKey(s.chainKey) ?: return
        _progress.value = "${chain.he} · ${s.name}"
        runCatching { repo.refresh(chain, s) }.onFailure {
            _messages.tryEmit(UiMessage.Res(R.string.prices_store_failed, "${chain.he} ${s.name}: ${it.message}"))
        }
    }

    /** Downloads today's prices for every selected store (free, a few MB per store). */
    fun refreshAll() = launchSafe {
        repo.selected().first().forEach { refreshOne(it) }
        _messages.tryEmit(UiMessage.Res(R.string.prices_updated))
        notifyAlerts()
    }

    val watches = repo.watches()
    fun watch(code: String, name: String, target: Double) = launchSafe { repo.watch(code, name, target); _messages.tryEmit(UiMessage.Res(R.string.prices_watching)) }
    fun unwatch(code: String) = launchSafe { repo.unwatch(code) }
    suspend fun history(code: String) = repo.history(code)

    /** PRC-7: a notification when a watched product reaches its target price. */
    private suspend fun notifyAlerts() {
        val ctx = getApplication<Application>()
        val due = repo.dueAlerts().filter { System.currentTimeMillis() - it.first.lastAlerted > 20 * 3_600_000L }
        if (due.isEmpty()) return
        val nm = ctx.getSystemService(android.app.NotificationManager::class.java)
        nm.createNotificationChannel(android.app.NotificationChannel("prices", ctx.getString(R.string.mod_prices), android.app.NotificationManager.IMPORTANCE_DEFAULT))
        due.forEach { (w, p) ->
            val chain = com.rimon.smartfamily.smart.Chains.byKey(p.chainKey)
            val n = androidx.core.app.NotificationCompat.Builder(ctx, "prices").setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(ctx.getString(R.string.prices_alert_title, w.name))
                .setContentText(ctx.getString(R.string.prices_alert_body, String.format("%.2f", p.price), chain?.he ?: p.chainKey)).setAutoCancel(true).build()
            runCatching { androidx.core.app.NotificationManagerCompat.from(ctx).notify(("price" + w.code).hashCode(), n) }
            repo.markAlerted(w)
        }
    }

    fun compare(listId: String) = launchSafe {
        _progress.value = ""
        val items = c.shopping.observeItems(listId).first().filter { !it.checked && it.status == "ACTIVE" }
        _baskets.value = repo.compare(items, repo.selected().first().filter { it.lastFetched > 0 }).sortedWith(
            compareByDescending<StoreBasket> { it.found }.thenBy { it.total }
        )
    }
}
