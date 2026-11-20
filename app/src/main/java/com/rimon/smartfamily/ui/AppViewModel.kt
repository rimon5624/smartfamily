package com.rimon.smartfamily.ui

import kotlinx.coroutines.flow.combine
import android.app.Application
import android.net.Uri
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rimon.smartfamily.SmartFamilyApp
import com.rimon.smartfamily.backup.BackupPayload
import com.rimon.smartfamily.data.db.FamilyEntity
import com.rimon.smartfamily.data.db.InviteEntity
import com.rimon.smartfamily.data.db.MemberEntity
import com.rimon.smartfamily.data.prefs.AppSettings
import com.rimon.smartfamily.data.prefs.ThemeMode
import com.rimon.smartfamily.data.repo.ModuleState
import com.rimon.smartfamily.model.AppModule
import com.rimon.smartfamily.model.Role
import com.rimon.smartfamily.sync.SyncScheduler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One-off messages for snackbars: either a string resource or free text. */
sealed interface UiMessage {
    data class Res(val id: Int, val arg: String? = null) : UiMessage
    data class Text(val text: String) : UiMessage
}

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val c = (app as SmartFamilyApp).container
    private val repo = c.families

    val settings: StateFlow<AppSettings?> =
        c.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val userIdFlow: Flow<String> = c.settings.settings.map { it.userId }

    val families: StateFlow<List<FamilyEntity>> = userIdFlow
        .flatMapLatest { uid -> if (uid.isBlank()) flowOf(emptyList()) else repo.observeFamilies(uid) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val activeFamily: StateFlow<FamilyEntity?> = userIdFlow
        .flatMapLatest { uid -> if (uid.isBlank()) flowOf(null) else repo.observeActiveFamily(uid) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val displayName: StateFlow<String> = userIdFlow
        .flatMapLatest { uid -> if (uid.isBlank()) flowOf(null) else repo.observeProfile(uid) }
        .map { it?.displayName ?: "" }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    val isSignedIn: Boolean get() = c.api.isSignedIn

    init {
        viewModelScope.launch {
            repo.ensureLocalUser()
            c.sync.syncNow()
        }
    }

    fun members(familyId: String) = repo.observeMembers(familyId)
    fun invites(familyId: String) = repo.observeInvites(familyId)
    fun family(familyId: String) = repo.observeFamily(familyId)
    fun modules(familyId: String, userId: String): Flow<List<ModuleState>> = repo.observeModules(familyId, userId)
    fun myMembership(familyId: String, userId: String): Flow<MemberEntity?> = repo.observeMyMembership(familyId, userId)
    fun sharedToMe(familyId: String, userId: String) = repo.observeSharedToMe(familyId, userId)

    data class AuditRow(val table: String, val action: String, val title: String, val actor: String?, val at: Long)

    /** Server audit log; empty when offline, not signed in, or not an admin. */
    suspend fun audit(familyId: String): List<AuditRow> = runCatching {
        val text = c.api.rpc("family_audit", kotlinx.serialization.json.buildJsonObject {
            put("fid", kotlinx.serialization.json.JsonPrimitive(familyId)); put("lim", kotlinx.serialization.json.JsonPrimitive(300))
        })
        kotlinx.serialization.json.Json.parseToJsonElement(text).let { it as kotlinx.serialization.json.JsonArray }.map { e ->
            val o = e as kotlinx.serialization.json.JsonObject
            fun str(k: String) = (o[k] as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it.isString }?.content
            AuditRow(str("table_name").orEmpty(), str("action").orEmpty(), str("title").orEmpty(), str("actor"),
                (o["at"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toLongOrNull() ?: 0L)
        }
    }.getOrDefault(emptyList())

    /** 2.2 notification check: asks the server to send a test wake-up to my phones; returns how many it reached. */
    private val _pushTest = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val pushTest = _pushTest.asStateFlow()
    fun sendTestPush() = viewModelScope.launch {
        _pushTest.value = "…"
        _pushTest.value = runCatching {
            runCatching { com.rimon.smartfamily.sync.Push.register(getApplication()) }
            val o = c.api.json.parseToJsonElement(c.api.function("notify", "selftest=1")) as kotlinx.serialization.json.JsonObject
            (o["sent"]?.toString() ?: "0") + "/" + (o["of"]?.toString() ?: "0")
        }.getOrElse { e -> if (e is java.net.UnknownHostException) "offline" else "error" }
    }

    private fun launchSafe(block: suspend () -> Unit) = viewModelScope.launch {
        _busy.value = true
        try {
            block()
        } catch (e: Exception) {
            _messages.tryEmit(UiMessage.Text(e.message ?: e.javaClass.simpleName))
        } finally {
            _busy.value = false
        }
    }

    private fun say(id: Int, arg: String? = null) { _messages.tryEmit(UiMessage.Res(id, arg)) }

    // ---- onboarding and profile ----

    fun chooseLanguage(tag: String) {
        viewModelScope.launch { c.settings.setLanguage(tag) }
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
    }

    fun finishOnboarding(name: String, familyName: String, modules: Set<AppModule>) = launchSafe {
        repo.setDisplayName(name.trim())
        repo.createFamily(familyName, modules)
        c.settings.setOnboardingDone(true)
    }

    fun setDisplayName(name: String) = launchSafe { repo.setDisplayName(name) }

    /** Onboarding for someone joining an existing family: no family of their own is created (2.1.2). */
    fun joinFromOnboarding(name: String, code: String) = launchSafe {
        if (name.isNotBlank()) repo.setDisplayName(name.trim())
        c.sync.joinWithCode(code.trim(), name.ifBlank { displayName.value }).getOrThrow()
        say(com.rimon.smartfamily.R.string.join_sent)
        c.settings.setOnboardingDone(true)
        SyncScheduler.requestSoon(getApplication())
    }

    // ---- families ----

    fun createFamily(name: String) = launchSafe {
        val f = repo.createFamily(name)
        repo.setActiveFamily(f.id)
    }

    fun setActiveFamily(id: String) = launchSafe { repo.setActiveFamily(id) }
    fun renameFamily(id: String, name: String) = launchSafe { repo.renameFamily(id, name) }
    fun setKashrut(id: String, value: String) = launchSafe { repo.setKashrut(id, value) }
    fun deleteFamily(id: String) = launchSafe { repo.deleteFamily(id) }
    fun setRole(memberId: String, role: Role) = launchSafe { repo.setRole(memberId, role) }
    fun approve(memberId: String) = launchSafe { repo.approveMember(memberId) }
    fun removeMember(memberId: String) = launchSafe { repo.removeMember(memberId) }
    fun addChild(familyId: String, name: String) = launchSafe { repo.addManagedChild(familyId, name) }
    fun revokeInvite(invite: InviteEntity) = launchSafe { repo.revokeInvite(invite) }

    fun createInvite(familyId: String, onCreated: (InviteEntity) -> Unit) = launchSafe {
        onCreated(repo.createInvite(familyId))
    }

    fun join(code: String, onDone: () -> Unit) = launchSafe {
        c.sync.joinWithCode(code, displayName.value).getOrThrow()
        say(com.rimon.smartfamily.R.string.join_sent)
        onDone()
    }

    fun setModule(familyId: String, module: AppModule, enabled: Boolean, forUser: String?) =
        launchSafe { repo.setModule(familyId, module, enabled, forUser) }

    // ---- settings ----

    fun setTheme(mode: ThemeMode) = launchSafe { c.settings.setThemeMode(mode) }
    fun setDynamicColor(v: Boolean) = launchSafe { c.settings.setDynamicColor(v) }
    fun setPrivacy(v: Boolean) = launchSafe { c.settings.setPrivacyMode(v) }
    fun setAppLock(v: Boolean) = launchSafe { c.settings.setAppLock(v) }
    fun setLockMinutes(v: Int) = launchSafe { c.settings.setLockMinutes(v) }
    fun setSecureScreens(v: Boolean) = launchSafe { c.settings.setSecureScreens(v) }
    fun setKidMode(v: String) = launchSafe { c.settings.setKidMode(v) }
    /** Owner or admin in at least one family (or no family yet): may see the cloud project details. */
    val canSeeCloudDetails = combine(c.db.people().observeAllActive(), c.settings.settings) { m, st ->
        m.none { it.userId == st.userId } || m.any { it.userId == st.userId && (it.role == "OWNER" || it.role == "ADMIN") }
    }
    val allPeople = c.db.people().observeAllActive()
    fun setNav(order: String? = null, chat: String? = null) = viewModelScope.launch { c.settings.setNav(order, chat) }
    fun toggleFavCity(key: String) = viewModelScope.launch {
        val cur = c.settings.current().favCities.split(',').filter { it.isNotBlank() }
        c.settings.setFavCities((if (key in cur) cur - key else cur + key).joinToString(","))
    }
    fun setHomeCards(greeting: String? = null, shabbat: String? = null) = viewModelScope.launch { c.settings.setHomeCards(greeting, shabbat) }
    fun setUpdateChecks(v: Boolean) = launchSafe { c.settings.setUpdateChecks(v) }
    fun markVersionSeen() = launchSafe { c.settings.setLastSeenVersion(com.rimon.smartfamily.BuildConfig.VERSION_NAME) }
    val update = kotlinx.coroutines.flow.MutableStateFlow<com.rimon.smartfamily.smart.UpdateInfo?>(null)
    fun checkUpdate() = viewModelScope.launch { update.value = runCatching { com.rimon.smartfamily.smart.UpdateChecker.latest() }.getOrNull() }
    fun setExpiryAlerts(v: Boolean) = launchSafe { c.settings.setExpiryAlerts(v) }
    fun setZmanimCity(v: String) = launchSafe { c.settings.setZmanimCity(v) }
    fun setChoreReminders(v: Boolean) = launchSafe { c.settings.setChoreReminders(v) }

    /** Moves a module one place up (-1) or down (+1) in the home-screen order. */
    fun moveModule(module: AppModule, delta: Int) = launchSafe {
        val order = AppModule.ordered(c.settings.current().moduleOrder).toMutableList()
        val i = order.indexOf(module)
        val j = i + delta
        if (i < 0 || j !in order.indices) return@launchSafe
        order[i] = order[j].also { order[j] = order[i] }
        c.settings.setModuleOrder(order.joinToString(",") { it.key })
    }

    val aiConfigured: Boolean get() = c.gemini.isConfigured

    // ---- cloud ----

    fun saveConnection(url: String, key: String) = launchSafe {
        c.settings.setSupabase(url, key)
        say(com.rimon.smartfamily.R.string.cloud_saved)
    }

    fun signIn(email: String, password: String) = launchSafe {
        val s = c.api.signIn(email.trim(), password)
        afterSignIn(s.user?.id, email.trim())
    }

    fun signUp(email: String, password: String) = launchSafe {
        val s = c.api.signUp(email.trim(), password)
        if (s == null) say(com.rimon.smartfamily.R.string.cloud_check_email)
        else afterSignIn(s.user?.id, email.trim())
    }

    private suspend fun afterSignIn(uid: String?, email: String) {
        if (uid != null) repo.adoptAccountId(uid)
        c.settings.setAccountEmail(email)
        // Re-send the profile so it exists on the server under the account id.
        repo.setDisplayName(displayName.value.ifBlank { email.substringBefore('@') })
        c.sync.syncNow().getOrThrow()
        SyncScheduler.schedulePeriodic(getApplication())
        say(com.rimon.smartfamily.R.string.cloud_synced)
    }

    fun signOut() = launchSafe {
        c.api.signOut()
        c.settings.setAccountEmail("")
    }

    fun syncNow() = launchSafe {
        c.sync.syncNow().getOrThrow()
        say(com.rimon.smartfamily.R.string.cloud_synced)
    }

    // ---- backup ----

    fun exportBackup(uri: Uri, password: String) = launchSafe {
        c.backup.export(uri, password, c.settings.current().userId)
        say(com.rimon.smartfamily.R.string.backup_done)
    }

    fun readBackup(uri: Uri, password: String, onRead: (BackupPayload) -> Unit) = launchSafe {
        try {
            onRead(c.backup.read(uri, password))
        } catch (e: com.rimon.smartfamily.backup.WrongPasswordException) {
            say(com.rimon.smartfamily.R.string.backup_wrong_password)
        }
    }

    fun restoreBackup(payload: BackupPayload) = launchSafe {
        c.backup.restore(payload)
        if (payload.userId.isNotBlank()) c.settings.setUserId(payload.userId)
        payload.families.firstOrNull { it.deletedAt == null }?.let { c.settings.setActiveFamily(it.id) }
        c.settings.setOnboardingDone(true)
        c.settings.resetPulls(c.sync.tableNames)
        SyncScheduler.requestSoon(getApplication())
        say(com.rimon.smartfamily.R.string.backup_restore_done)
    }
}
