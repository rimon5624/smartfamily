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
        try { block() } catch (e: Exception) { _messages.tryEmit(UiMessage.Text(e.message ?: e.javaClass.simpleName)) } finally { _progress.value = null }
    }

    fun lists(familyId: String) = c.shopping.observeLists(familyId)
    fun stores(chainKey: String) = repo.stores(chainKey)
    val selected = repo.selected()

    fun loadStores(chainKey: String) = launchSafe {
        val chain = Chains.byKey(chainKey) ?: return@launchSafe
        _progress.value = chain.he
        val n = repo.loadStores(chain)
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
    }

    fun compare(listId: String) = launchSafe {
        _progress.value = ""
        val items = c.shopping.observeItems(listId).first().filter { !it.checked && it.status == "ACTIVE" }
        _baskets.value = repo.compare(items, repo.selected().first().filter { it.lastFetched > 0 }).sortedWith(
            compareByDescending<StoreBasket> { it.found }.thenBy { it.total }
        )
    }
}
