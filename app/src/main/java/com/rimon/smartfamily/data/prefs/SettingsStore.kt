package com.rimon.smartfamily.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val language: String = "",
    val onboardingDone: Boolean = false,
    val userId: String = "",
    val activeFamilyId: String = "",
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val privacyMode: Boolean = false,
    val supabaseUrl: String = "",
    val supabaseAnonKey: String = "",
    val accountEmail: String = "",
    val lastSyncAt: Long = 0,
    val lastSyncError: String = "",
    val geminiModel: String = "",
    /** Home screen module order chosen by the user (comma-separated keys); empty = default. */
    val moduleOrder: String = "",
    val expiryAlerts: Boolean = true,
    val zmanimCity: String = "jerusalem",
    val choreReminders: Boolean = true,
    val homeConnectClientId: String = "",
    // 1.0
    val appLock: Boolean = false,
    val lockMinutes: Int = 1,
    val secureScreens: Boolean = false,
    /** AUTO = on for child accounts; ON / OFF force it on this phone. */
    val kidMode: String = "AUTO",
    val lastSeenVersion: String = "",
    val updateChecks: Boolean = true,
    // calendar (2.0)
    val calHebFirst: Boolean = true,
    val calBasis: String = "GREG",
    val calView: String = "MONTH",
    val calShabbatRow: Boolean = true,
    /** Home cards: greeting FULL/COMPACT/HIDE; Shabbat AUTO (big only on Friday and holiday eves)/COMPACT/FULL/HIDE. */
    val homeGreeting: String = "COMPACT",
    val homeShabbat: String = "AUTO",
    /** Favourite places for zmanim (keys, comma separated) — quick switching when travelling. */
    val favCities: String = "",
    val repairedV201: Boolean = false,
    /** Bottom bar order (csv of NavItems) and where the family chat lives: ICON on every screen or a bar TAB. */
    val navOrder: String = "",
    val chatPlacement: String = "ICON",
) {
    val supabaseConfigured: Boolean get() = supabaseUrl.isNotBlank() && supabaseAnonKey.isNotBlank()
}

class SettingsStore(private val context: Context) {

    private object K {
        val language = stringPreferencesKey("language")
        val onboardingDone = booleanPreferencesKey("onboarding_done")
        val userId = stringPreferencesKey("user_id")
        val activeFamilyId = stringPreferencesKey("active_family_id")
        val themeMode = stringPreferencesKey("theme_mode")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val privacyMode = booleanPreferencesKey("privacy_mode")
        val supabaseUrl = stringPreferencesKey("supabase_url")
        val supabaseAnonKey = stringPreferencesKey("supabase_anon_key")
        val accountEmail = stringPreferencesKey("account_email")
        val lastSyncAt = longPreferencesKey("last_sync_at")
        val lastSyncError = stringPreferencesKey("last_sync_error")
        val geminiModel = stringPreferencesKey("gemini_model")
        val moduleOrder = stringPreferencesKey("module_order")
        val expiryAlerts = booleanPreferencesKey("expiry_alerts")
        val zmanimCity = stringPreferencesKey("zmanim_city")
        val choreReminders = booleanPreferencesKey("chore_reminders")
        val homeConnectClientId = stringPreferencesKey("home_connect_client_id")
        val appLock = booleanPreferencesKey("app_lock")
        val lockMinutes = androidx.datastore.preferences.core.intPreferencesKey("lock_minutes")
        val secureScreens = booleanPreferencesKey("secure_screens")
        val kidMode = stringPreferencesKey("kid_mode")
        val lastSeenVersion = stringPreferencesKey("last_seen_version")
        val updateChecks = booleanPreferencesKey("update_checks")
        val calHebFirst = booleanPreferencesKey("cal_heb_first")
        val calBasis = stringPreferencesKey("cal_basis")
        val calView = stringPreferencesKey("cal_view")
        val calShabbatRow = booleanPreferencesKey("cal_shabbat_row")
        val homeGreeting = stringPreferencesKey("home_greeting")
        val homeShabbat = stringPreferencesKey("home_shabbat")
        val favCities = stringPreferencesKey("fav_cities")
        val repairedV201 = booleanPreferencesKey("repaired_v201")
        val navOrder = stringPreferencesKey("nav_order")
        val chatPlacement = stringPreferencesKey("chat_placement")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p -> p.toSettings() }

    suspend fun current(): AppSettings = settings.first()

    private fun Preferences.toSettings() = AppSettings(
        language = this[K.language] ?: "",
        onboardingDone = this[K.onboardingDone] ?: false,
        userId = this[K.userId] ?: "",
        activeFamilyId = this[K.activeFamilyId] ?: "",
        themeMode = runCatching { ThemeMode.valueOf(this[K.themeMode] ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM),
        dynamicColor = this[K.dynamicColor] ?: false,
        privacyMode = this[K.privacyMode] ?: false,
        supabaseUrl = this[K.supabaseUrl]?.takeIf { it.isNotBlank() } ?: com.rimon.smartfamily.BuildConfig.DEFAULT_SUPABASE_URL,
        supabaseAnonKey = this[K.supabaseAnonKey]?.takeIf { it.isNotBlank() } ?: com.rimon.smartfamily.BuildConfig.DEFAULT_SUPABASE_ANON_KEY,
        accountEmail = this[K.accountEmail] ?: "",
        lastSyncAt = this[K.lastSyncAt] ?: 0,
        lastSyncError = this[K.lastSyncError] ?: "",
        geminiModel = this[K.geminiModel] ?: "",
        moduleOrder = this[K.moduleOrder] ?: "",
        expiryAlerts = this[K.expiryAlerts] ?: true,
        zmanimCity = this[K.zmanimCity] ?: "jerusalem",
        choreReminders = this[K.choreReminders] ?: true,
        homeConnectClientId = this[K.homeConnectClientId] ?: "",
        appLock = this[K.appLock] ?: false,
        lockMinutes = this[K.lockMinutes] ?: 1,
        secureScreens = this[K.secureScreens] ?: false,
        kidMode = this[K.kidMode] ?: "AUTO",
        lastSeenVersion = this[K.lastSeenVersion] ?: "",
        updateChecks = this[K.updateChecks] ?: true,
        calHebFirst = this[K.calHebFirst] ?: true,
        calBasis = this[K.calBasis] ?: "GREG",
        calView = this[K.calView] ?: "MONTH",
        calShabbatRow = this[K.calShabbatRow] ?: true,
        homeGreeting = this[K.homeGreeting] ?: "COMPACT",
        homeShabbat = this[K.homeShabbat] ?: "AUTO",
        favCities = this[K.favCities] ?: "",
        repairedV201 = this[K.repairedV201] ?: false,
        navOrder = this[K.navOrder] ?: "",
        chatPlacement = this[K.chatPlacement] ?: "ICON",
    )

    suspend fun setNav(order: String? = null, chat: String? = null) = context.dataStore.edit {
        order?.let { v -> it[K.navOrder] = v }; chat?.let { v -> it[K.chatPlacement] = v }
    }

    suspend fun setRepairedV201() = context.dataStore.edit { it[K.repairedV201] = true }

    suspend fun setFavCities(v: String) = context.dataStore.edit { it[K.favCities] = v }

    suspend fun setHomeCards(greeting: String? = null, shabbat: String? = null) = context.dataStore.edit {
        greeting?.let { v -> it[K.homeGreeting] = v }; shabbat?.let { v -> it[K.homeShabbat] = v }
    }

    suspend fun setCalendar(hebFirst: Boolean? = null, basis: String? = null, view: String? = null, shabbatRow: Boolean? = null) = context.dataStore.edit {
        hebFirst?.let { v -> it[K.calHebFirst] = v }; basis?.let { v -> it[K.calBasis] = v }
        view?.let { v -> it[K.calView] = v }; shabbatRow?.let { v -> it[K.calShabbatRow] = v }
    }

    suspend fun setAppLock(v: Boolean) = context.dataStore.edit { it[K.appLock] = v }
    suspend fun setLockMinutes(v: Int) = context.dataStore.edit { it[K.lockMinutes] = v }
    suspend fun setSecureScreens(v: Boolean) = context.dataStore.edit { it[K.secureScreens] = v }
    suspend fun setKidMode(v: String) = context.dataStore.edit { it[K.kidMode] = v }
    suspend fun setLastSeenVersion(v: String) = context.dataStore.edit { it[K.lastSeenVersion] = v }
    suspend fun setUpdateChecks(v: Boolean) = context.dataStore.edit { it[K.updateChecks] = v }

    suspend fun setLanguage(v: String) = context.dataStore.edit { it[K.language] = v }
    suspend fun setOnboardingDone(v: Boolean) = context.dataStore.edit { it[K.onboardingDone] = v }
    suspend fun setUserId(v: String) = context.dataStore.edit { it[K.userId] = v }
    suspend fun setActiveFamily(v: String) = context.dataStore.edit { it[K.activeFamilyId] = v }
    suspend fun setThemeMode(v: ThemeMode) = context.dataStore.edit { it[K.themeMode] = v.name }
    suspend fun setDynamicColor(v: Boolean) = context.dataStore.edit { it[K.dynamicColor] = v }
    suspend fun setPrivacyMode(v: Boolean) = context.dataStore.edit { it[K.privacyMode] = v }
    suspend fun setGeminiModel(v: String) = context.dataStore.edit { it[K.geminiModel] = v.trim() }
    suspend fun setModuleOrder(v: String) = context.dataStore.edit { it[K.moduleOrder] = v }
    suspend fun setExpiryAlerts(v: Boolean) = context.dataStore.edit { it[K.expiryAlerts] = v }
    suspend fun setZmanimCity(v: String) = context.dataStore.edit { it[K.zmanimCity] = v }
    suspend fun setChoreReminders(v: Boolean) = context.dataStore.edit { it[K.choreReminders] = v }
    suspend fun setHomeConnectClientId(v: String) = context.dataStore.edit { it[K.homeConnectClientId] = v.trim() }
    suspend fun setAccountEmail(v: String) = context.dataStore.edit { it[K.accountEmail] = v }

    suspend fun setSupabase(url: String, anonKey: String) = context.dataStore.edit {
        it[K.supabaseUrl] = url.trim().trimEnd('/')
        it[K.supabaseAnonKey] = anonKey.trim()
    }

    suspend fun setSyncResult(at: Long, error: String) = context.dataStore.edit {
        if (error.isEmpty()) it[K.lastSyncAt] = at
        it[K.lastSyncError] = error
    }

    suspend fun lastPull(table: String): Long =
        context.dataStore.data.first()[longPreferencesKey("pull_$table")] ?: 0L

    suspend fun setLastPull(table: String, value: Long) =
        context.dataStore.edit { it[longPreferencesKey("pull_$table")] = value }

    suspend fun resetPulls(tables: List<String>) = context.dataStore.edit { p ->
        tables.forEach { p.remove(longPreferencesKey("pull_$it")) }
    }
}
