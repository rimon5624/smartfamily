package com.rimon.smartfamily

import android.app.Application
import android.content.Context
import com.rimon.smartfamily.backup.BackupManager
import com.rimon.smartfamily.data.db.AppDatabase
import com.rimon.smartfamily.data.prefs.SettingsStore
import com.rimon.smartfamily.data.repo.FamilyRepository
import com.rimon.smartfamily.data.repo.ShoppingRepository
import com.rimon.smartfamily.smart.ExpiryWorker
import com.rimon.smartfamily.smart.GeminiClient
import com.rimon.smartfamily.security.KeystoreCrypto
import com.rimon.smartfamily.sync.SupabaseApi
import com.rimon.smartfamily.sync.SyncEngine
import com.rimon.smartfamily.sync.SyncScheduler

/** Manual dependency container: one instance of each service for the whole app. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val crypto = KeystoreCrypto(appContext)
    val settings = SettingsStore(appContext)
    val db: AppDatabase = AppDatabase.open(appContext, crypto.databasePassphrase())
    val api = SupabaseApi(crypto) { settings.current().let { it.supabaseUrl to it.supabaseAnonKey } }
    val sync = SyncEngine(db, api, settings)
    val families = FamilyRepository(db, settings) { SyncScheduler.requestSoon(appContext) }
    val backup = BackupManager(appContext, db)
    val shopping = ShoppingRepository(db) { SyncScheduler.requestSoon(appContext) }
    val gemini = GeminiClient(crypto) { settings.current().let { it.geminiModel to it.privacyMode } }
}

class SmartFamilyApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        SyncScheduler.schedulePeriodic(this)
        ExpiryWorker.schedule(this)
    }
}
