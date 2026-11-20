package com.rimon.smartfamily.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.rimon.smartfamily.R
import com.rimon.smartfamily.SmartFamilyApp
import com.rimon.smartfamily.data.db.InventoryItemEntity
import com.rimon.smartfamily.data.db.ListItemEntity
import com.rimon.smartfamily.data.repo.LowStock
import com.rimon.smartfamily.model.ItemDraft
import com.rimon.smartfamily.smart.AiNotConfiguredException
import com.rimon.smartfamily.smart.GeminiClient
import com.rimon.smartfamily.smart.OpenFoodFacts
import com.rimon.smartfamily.smart.SpeechParser
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ShoppingViewModel(app: Application) : AndroidViewModel(app) {

    private val c = (app as SmartFamilyApp).container
    private val repo = c.shopping

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()

    val aiConfigured: Boolean get() = c.gemini.isConfigured

    private fun say(id: Int, arg: String? = null) { _messages.tryEmit(UiMessage.Res(id, arg)) }

    private fun launchSafe(block: suspend () -> Unit) = viewModelScope.launch {
        _busy.value = true
        try {
            block()
        } catch (e: AiNotConfiguredException) {
            say(R.string.ai_needed)
        } catch (e: Exception) {
            _messages.tryEmit(UiMessage.Text(e.message ?: e.javaClass.simpleName))
        } finally {
            _busy.value = false
        }
    }

    private suspend fun uid() = c.settings.current().userId

    // ---- observation ----
    fun lists(familyId: String) = repo.observeLists(familyId, false)
    fun templates(familyId: String) = repo.observeLists(familyId, true)
    fun counts(familyId: String) = repo.observeCounts(familyId)
    fun list(id: String) = repo.observeList(id)
    fun items(listId: String) = repo.observeItems(listId)
    fun frequent(familyId: String) = repo.observeFrequent(familyId)
    fun lowStock(familyId: String) = repo.observeLowStock(familyId)
    fun inventory(familyId: String) = repo.observeInventory(familyId)
    fun members(familyId: String) = c.families.observeMembers(familyId)
    fun recipes(familyId: String) = c.kitchen.observeRecipes(familyId)

    /** INV-3: read the expiry date from a photo of the package (Gemini free tier). */
    fun expiryFromPhoto(item: InventoryItemEntity, uri: Uri) = launchSafe {
        val t = c.gemini.askWithImage(getApplication(), uri, "Read the expiry / best-before date printed on this package. Answer only yyyy-MM-dd, or NONE.").trim()
        val d = Regex("(\\d{4})-(\\d{2})-(\\d{2})").find(t)?.value?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }
            ?: return@launchSafe say(R.string.inv_no_date)
        repo.updateInventory(item.copy(expiresAt = com.rimon.smartfamily.smart.JewishDays.startMs(d) + 12 * 3_600_000L))
        say(R.string.inv_date_read, "${d.dayOfMonth}/${d.monthValue}/${d.year}")
    }

    /** Keeps an open list close to real time (SHP-9): a quick two-table sync every 8 seconds. */
    suspend fun liveSync() {
        while (true) {
            c.sync.syncTables(setOf("shopping_lists", "list_items"))
            delay(8_000)
        }
    }

    // ---- lists ----
    fun ensureDefaultList(familyId: String, name: String) = viewModelScope.launch {
        repo.ensureDefaultList(familyId, name, uid())
    }
    fun createList(familyId: String, name: String) = launchSafe { repo.createList(familyId, name, uid()) }
    fun copyListTo(listId: String, familyId: String, familyName: String) = launchSafe {
        repo.copyToFamily(listId, familyId, uid())
        _messages.tryEmit(UiMessage.Res(R.string.copied_to_family, familyName))
    }
    fun renameList(id: String, name: String) = launchSafe { repo.renameList(id, name) }
    fun deleteList(id: String) = launchSafe { repo.deleteList(id) }
    fun setShopping(id: String, on: Boolean) = launchSafe { repo.setShopping(id, if (on) uid() else null) }
    fun saveAsTemplate(id: String, name: String) = launchSafe { repo.saveAsTemplate(id, name, uid()) }
    fun applyTemplate(templateId: String, listId: String) = launchSafe {
        say(R.string.shop_added, repo.applyTemplate(templateId, listId, uid()).toString())
    }

    // ---- items ----
    fun addText(listId: String, text: String, asRequest: Boolean) = launchSafe {
        addDrafts(listId, SpeechParser.parse(text), asRequest)
    }

    private suspend fun addDrafts(listId: String, drafts: List<ItemDraft>, asRequest: Boolean) {
        if (drafts.isEmpty()) { say(R.string.shop_nothing_found); return }
        val n = repo.addItems(listId, drafts, uid(), asRequest)
        if (asRequest) say(R.string.shop_request_sent) else say(R.string.shop_added, n.toString())
    }

    fun scanToList(listId: String, asRequest: Boolean) = launchSafe {
        val draft = scanBarcode() ?: return@launchSafe
        addDrafts(listId, listOf(draft), asRequest)
    }

    fun photoToList(listId: String, uri: Uri, asRequest: Boolean) = launchSafe {
        val items = c.gemini.itemsFromPhoto(getApplication(), uri, GeminiClient.PhotoMode.PRODUCTS)
        addDrafts(listId, items, asRequest)
    }

    fun toggle(itemId: String) = launchSafe { repo.toggle(itemId, uid()) }
    fun approve(itemId: String) = launchSafe { repo.approve(itemId) }
    fun update(item: ListItemEntity) = launchSafe { repo.updateItem(item) }
    fun deleteItem(itemId: String) = launchSafe { repo.deleteItem(itemId) }

    fun finish(listId: String, toInventory: Boolean) = launchSafe {
        say(R.string.shop_finished, repo.finishShopping(listId, uid(), toInventory).toString())
    }

    fun addLowStock(listId: String, low: List<LowStock>) = launchSafe {
        say(R.string.shop_added, repo.addLowStockToList(listId, low, uid()).toString())
    }

    // ---- inventory ----
    fun addInventoryText(familyId: String, text: String, location: String?) = launchSafe {
        val drafts = SpeechParser.parse(text)
        if (drafts.isEmpty()) say(R.string.shop_nothing_found)
        else say(R.string.shop_added, repo.addInventory(familyId, drafts, location, uid()).toString())
    }

    fun scanToInventory(familyId: String, location: String?) = launchSafe {
        val d = scanBarcode() ?: return@launchSafe
        say(R.string.shop_added, repo.addInventory(familyId, listOf(d), location, uid()).toString())
    }

    fun photoToInventory(familyId: String, uri: Uri, location: String?) = launchSafe {
        val items = c.gemini.itemsFromPhoto(getApplication(), uri, GeminiClient.PhotoMode.SHELF)
        if (items.isEmpty()) say(R.string.shop_nothing_found)
        else say(R.string.shop_added, repo.addInventory(familyId, items, location, uid()).toString())
    }

    fun updateInventory(item: InventoryItemEntity) = launchSafe { repo.updateInventory(item) }
    fun useInventory(id: String) = launchSafe { repo.useInventory(id) }
    fun deleteInventory(id: String) = launchSafe { repo.deleteInventory(id) }

    /** Google's code scanner UI; then the free Open Food Facts database (skipped in privacy mode). */
    private suspend fun scanBarcode(): ItemDraft? {
        val code = runCatching { GmsBarcodeScanning.getClient(getApplication()).startScan().await() }
            .getOrNull()?.rawValue ?: return null
        val found = if (c.settings.current().privacyMode) null else OpenFoodFacts.lookup(code)
        if (found == null) {
            say(R.string.shop_barcode_unknown, code)
            return null
        }
        return found
    }

    // ---- AI settings ----
    fun saveAi(key: String, model: String) {
        c.gemini.setApiKey(key) // synchronous, so the screen can show the masked key at once
        launchSafe {
            c.settings.setGeminiModel(model)
            say(R.string.ai_saved)
        }
    }
    fun removeAiKey() = c.gemini.setApiKey(null)
    fun saveModel(model: String) = viewModelScope.launch { c.settings.setGeminiModel(model) }

    // ---- 2.2 AI connection wizard: live model list, model test, sharing the key with the family ----
    private val _models = MutableStateFlow<List<com.rimon.smartfamily.smart.GeminiClient.ModelInfo>>(emptyList())
    val models = _models.asStateFlow()
    private val _modelsError = MutableStateFlow<String?>(null)
    val modelsError = _modelsError.asStateFlow()
    private val _checks = MutableStateFlow<Map<String, com.rimon.smartfamily.smart.GeminiClient.Check>>(emptyMap())
    val checks = _checks.asStateFlow()
    private val _testing = MutableStateFlow<String?>(null)
    val testing = _testing.asStateFlow()
    val usesFamilyKey: Boolean get() = c.gemini.usesFamilyKey

    fun loadModels(key: String? = null) = viewModelScope.launch {
        _modelsError.value = null
        runCatching { c.gemini.listModels(key?.trim()?.ifBlank { null }) }
            .onSuccess { _models.value = it }
            .onFailure { e -> _modelsError.value = if (e is java.net.UnknownHostException) "offline" else (e.message ?: "error") }
    }
    fun testModel(model: String, key: String? = null) = viewModelScope.launch {
        _testing.value = model
        val r = runCatching { c.gemini.testModel(model, key?.trim()?.ifBlank { null }) }.getOrElse { com.rimon.smartfamily.smart.GeminiClient.Check.Other(it.message ?: "error") }
        _checks.value = _checks.value + (model to r)
        _testing.value = null
    }
    /** Shares this phone's key with the family, encrypted with the family key (like the chat); null turns sharing off. */
    fun shareKeyWithFamily(familyId: String, on: Boolean) = launchSafe {
        val existing = c.db.homeEntrys().byKind(familyId, "AI_KEY")
        if (!on) { existing?.let { c.extras.deleteHomeEntry(it) }; say(R.string.ai_share_off); return@launchSafe }
        val key = c.gemini.apiKey() ?: return@launchSafe say(R.string.ai_needed)
        val enc = c.chat.encryptForFamily(familyId, key) ?: return@launchSafe say(R.string.secret_needs_chat)
        c.extras.saveHomeEntry((existing ?: com.rimon.smartfamily.data.db.HomeEntryEntity(id = java.util.UUID.randomUUID().toString(), familyId = familyId,
            section = "SYSTEM", kind = "AI_KEY", title = "Gemini", createdBy = uid())).copy(body = enc))
        say(R.string.ai_share_on)
    }
    fun familySharesKey(familyId: String) = c.db.homeEntrys().observeSection(familyId, "SYSTEM")
    fun currentKeyOrEmpty(): String = c.gemini.apiKey().orEmpty()
    fun maskedKey(): String = c.gemini.apiKey()?.let { if (it.length > 8) it.take(4) + "…" + it.takeLast(4) else "…" } ?: ""
}
