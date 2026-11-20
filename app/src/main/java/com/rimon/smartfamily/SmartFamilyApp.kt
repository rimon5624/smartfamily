package com.rimon.smartfamily

import android.app.Application
import android.content.Context
import com.rimon.smartfamily.backup.BackupManager
import com.rimon.smartfamily.data.db.AppDatabase
import com.rimon.smartfamily.data.prefs.SettingsStore
import com.rimon.smartfamily.data.repo.FamilyRepository
import com.rimon.smartfamily.data.repo.ApplianceRepository
import com.rimon.smartfamily.data.repo.ChatRepository
import com.rimon.smartfamily.security.ChatCrypto
import com.rimon.smartfamily.data.repo.HomeLifeRepository
import com.rimon.smartfamily.smart.HomeConnectClient
import com.rimon.smartfamily.smart.SmartThingsClient
import com.rimon.smartfamily.data.repo.KitchenRepository
import com.rimon.smartfamily.data.repo.ShoppingRepository
import com.rimon.smartfamily.smart.ReminderWorker
import com.rimon.smartfamily.smart.ExpiryWorker
import com.rimon.smartfamily.smart.GeminiClient
import com.rimon.smartfamily.security.KeystoreCrypto
import com.rimon.smartfamily.sync.SupabaseApi
import com.rimon.smartfamily.sync.SyncEngine
import com.rimon.smartfamily.sync.SyncScheduler
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.debounce

/** Manual dependency container: one instance of each service for the whole app. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val crypto = KeystoreCrypto(appContext)
    val settings = SettingsStore(appContext)
    val db: AppDatabase = AppDatabase.open(appContext, crypto.databasePassphrase())
    val api = SupabaseApi(crypto) { settings.current().let { it.supabaseUrl to it.supabaseAnonKey } }
    val sync = SyncEngine(db, api, settings)
    val families = FamilyRepository(db, settings) { SyncScheduler.requestSoon(appContext) }
    val backup = BackupManager(appContext, db) { chatCrypto }
    val shopping = ShoppingRepository(db) { SyncScheduler.requestSoon(appContext) }
    val homeLife = HomeLifeRepository(db) { SyncScheduler.requestSoon(appContext) }
    val kitchen = KitchenRepository(db, shopping) { SyncScheduler.requestSoon(appContext) }
    val appliances = ApplianceRepository(db, homeLife) { SyncScheduler.requestSoon(appContext) }
    val homeConnect = HomeConnectClient(crypto) { settings.current().homeConnectClientId }
    val smartThings = SmartThingsClient(crypto)
    val extras = com.rimon.smartfamily.data.repo.ExtrasRepository(db) { SyncScheduler.requestSoon(appContext) }
    val prices = com.rimon.smartfamily.data.repo.PriceRepository(db)
    val shares = com.rimon.smartfamily.data.repo.ShareRepository(db, api) { SyncScheduler.requestSoon(appContext) }
    val chatCrypto = ChatCrypto(crypto)
    val chat = ChatRepository(db, chatCrypto, api) { SyncScheduler.requestSoon(appContext) }
    init { sync.afterPull = { me -> chat.maintain(me); runCatching { com.rimon.smartfamily.sync.Push.register(appContext) }; runCatching { gemini.warm() } } }
    val realtime = com.rimon.smartfamily.sync.Realtime(api)
    val gemini = GeminiClient(crypto, { settings.current().let { it.geminiModel to it.privacyMode } }, familyKey = {
        runCatching { db.homeEntrys().byKind(settings.current().activeFamilyId, "AI_KEY")?.body?.let { chat.decryptNote(it) } }.getOrNull()?.trim()?.ifBlank { null }
    })
}

class SmartFamilyApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        container = AppContainer(this)
        SyncScheduler.schedulePeriodic(this)
        ExpiryWorker.schedule(this)
        ReminderWorker.schedule(this)
        startLive()
    }

    /** 2.2 live chat: a realtime socket only while the app is on screen; each change pulls the chat tables. */
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    private fun startLive() {
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
        androidx.lifecycle.ProcessLifecycleOwner.get().lifecycle.addObserver(object : androidx.lifecycle.DefaultLifecycleObserver {
            override fun onStart(owner: androidx.lifecycle.LifecycleOwner) {
                scope.launch { if (container.settings.current().supabaseConfigured) container.realtime.start() }
            }
            override fun onStop(owner: androidx.lifecycle.LifecycleOwner) { container.realtime.stop() }
        })
        scope.launch {
            container.realtime.changes.debounce(250).collect {
                container.sync.syncTables(setOf("chats", "chat_keys", "chat_messages", "chat_reactions", "chat_reads"))
                runCatching { com.rimon.smartfamily.smart.ChatNotifier.afterSync(this@SmartFamilyApp, container) }
            }
        }
    }
}
