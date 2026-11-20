package com.rimon.smartfamily.ui

import kotlinx.coroutines.flow.map
import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rimon.smartfamily.R
import com.rimon.smartfamily.SmartFamilyApp
import com.rimon.smartfamily.data.db.ApplianceEntity
import com.rimon.smartfamily.data.db.LaundryPresetEntity
import com.rimon.smartfamily.smart.AiNotConfiguredException
import com.rimon.smartfamily.smart.ApplianceProfile
import com.rimon.smartfamily.smart.Cities
import com.rimon.smartfamily.smart.HomeConnectClient
import com.rimon.smartfamily.smart.LaundryLoad
import com.rimon.smartfamily.smart.LiveStatus
import com.rimon.smartfamily.smart.SmartDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.util.UUID

/** What the care-label photo says (APL-9). */
data class CareLabel(val maxTemp: Int?, val tumbleDry: Boolean?, val notes: String)

class ApplianceViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as SmartFamilyApp).container
    private val repo = c.appliances
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()

    private val _live = MutableStateFlow<Map<String, LiveStatus>>(emptyMap())
    val live = _live.asStateFlow()
    private val _devices = MutableStateFlow<List<SmartDevice>>(emptyList())
    val devices = _devices.asStateFlow()
    private val _careLabel = MutableStateFlow<CareLabel?>(null)
    val careLabel = _careLabel.asStateFlow()
    private val _aiAdvice = MutableStateFlow("")
    val aiAdvice = _aiAdvice.asStateFlow()
    private val _hcCode = MutableStateFlow<HomeConnectClient.DeviceCode?>(null)
    val hcCode = _hcCode.asStateFlow()

    private fun say(id: Int, arg: String? = null) { _messages.tryEmit(UiMessage.Res(id, arg)) }
    private fun launchSafe(block: suspend () -> Unit) = viewModelScope.launch {
        _busy.value = true
        try { block() }
        catch (e: AiNotConfiguredException) { say(R.string.ai_needed) }
        catch (e: Exception) { _messages.tryEmit(UiMessage.Text(e.message ?: e.javaClass.simpleName)) }
        finally { _busy.value = false }
    }
    private suspend fun uid() = c.settings.current().userId
    fun lang(): String = getApplication<Application>().resources.configuration.locales[0].language.let { if (it == "iw" || it == "he") "Hebrew" else "English" }

    fun appliances(familyId: String) = repo.observeAll(familyId)

    // ---- 2.2 add-appliance wizard ----
    val aiConfigured: Boolean get() = c.gemini.isConfigured
    /** (brand, model) pairs the family already used, for suggestions. */
    fun knownBrandsModels() = c.db.appliances().observeAllActive().map { l -> l.map { it.brand to it.model }.distinct() }
    private val _modelSuggestions = MutableStateFlow<List<String>>(emptyList())
    val modelSuggestions = _modelSuggestions.asStateFlow()
    fun suggestModels(type: String, brand: String) = launchSafe {
        val text = c.gemini.ask("List up to 10 common current model numbers of $brand $type appliances sold in Israel, as printed on the model sticker. " +
            "Answer only JSON: {\"models\":[string]}")
        _modelSuggestions.value = runCatching {
            (json.parseToJsonElement(text) as JsonObject)["models"]!!.jsonArray.mapNotNull { it.jsonPrimitive.contentOrNull?.trim()?.takeIf { m -> m.length in 3..30 } }
        }.getOrDefault(emptyList())
    }
    fun appliance(id: String) = repo.observeOne(id)
    fun presets(applianceId: String) = repo.observePresets(applianceId)
    fun profileOf(a: ApplianceEntity): ApplianceProfile? = a.profile.takeIf { it.isNotBlank() }?.let { ApplianceProfile.parse(it) }

    val homeConnectConnected get() = c.homeConnect.isConnected
    val smartThingsConnected get() = c.smartThings.isConnected

    fun newAppliance(familyId: String, userId: String, type: String) =
        ApplianceEntity(id = UUID.randomUUID().toString(), familyId = familyId, type = type, name = "", createdBy = userId)

    fun save(a: ApplianceEntity) = launchSafe { repo.save(a) }
    fun delete(id: String) = launchSafe { repo.delete(id) }

    /** APL-1: brand, model and serial from a photo of the label. */
    fun fillFromLabel(a: ApplianceEntity, uri: Uri, onFilled: (ApplianceEntity) -> Unit) = launchSafe {
        val text = c.gemini.askWithImage(getApplication(), uri,
            "This is the rating label of a home appliance or a car's registration. Answer only JSON: " +
                "{\"type\":\"washer\"|\"dryer\"|\"washer_dryer\"|\"dishwasher\"|\"ac\"|\"car\"|\"oven\"|\"fridge\"|\"water_heater\"|\"other\"," +
                "\"brand\":string,\"model\":string,\"serial\":string}")
        val o = runCatching { json.parseToJsonElement(text) as JsonObject }.getOrNull() ?: return@launchSafe say(R.string.shop_nothing_found)
        fun s(k: String) = o[k]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        onFilled(a.copy(type = s("type").ifBlank { a.type }, brand = s("brand").ifBlank { a.brand }, model = s("model").ifBlank { a.model }, serial = s("serial").ifBlank { a.serial }))
    }

    // ---------- manual -> profile ----------

    private fun manualFile(id: String) = File(getApplication<Application>().filesDir, "manuals/$id.pdf")
    fun hasManual(id: String) = manualFile(id).exists()
    fun manualUri(id: String): Uri = androidx.core.content.FileProvider.getUriForFile(getApplication(), getApplication<Application>().packageName + ".files", manualFile(id))

    /** Keeps a private copy of the PDF on this phone (to open its pages later). */
    fun storeManual(a: ApplianceEntity, uri: Uri) = launchSafe {
        withContext(Dispatchers.IO) {
            val f = manualFile(a.id).apply { parentFile?.mkdirs() }
            getApplication<Application>().contentResolver.openInputStream(uri)!!.use { input -> f.outputStream().use { input.copyTo(it) } }
        }
    }

    /** APL-4: the PDF goes to Gemini with the schema; the result is checked and saved. */
    fun parseManualWithAi(a: ApplianceEntity, uri: Uri) = launchSafe {
        val bytes = withContext(Dispatchers.IO) {
            val f = manualFile(a.id).apply { parentFile?.mkdirs() }
            getApplication<Application>().contentResolver.openInputStream(uri)!!.use { input -> f.outputStream().use { input.copyTo(it) } }
            f.readBytes()
        }
        if (bytes.size > 19 * 1024 * 1024) throw IllegalStateException(getApplication<Application>().getString(R.string.apl_pdf_too_big))
        val text = c.gemini.askWithPdf(bytes, ApplianceProfile.prompt(a.type, a.brand, a.model, lang()))
        val profile = ApplianceProfile.parse(text) ?: throw IllegalStateException(getApplication<Application>().getString(R.string.apl_parse_failed))
        repo.setProfile(a.id, profile, "pdf")
        say(R.string.apl_profile_saved, profile.programs.size.toString())
    }

    /** APL-5: the prompt to paste into Claude, ChatGPT or Gemini together with the PDF. */
    fun aiChatPrompt(a: ApplianceEntity) = ApplianceProfile.prompt(a.type, a.brand, a.model, lang())

    /** APL-5 / APL-6: imports a JSON answer, or (with a Gemini key) any free text about the appliance. */
    fun importText(a: ApplianceEntity, text: String) = launchSafe {
        val direct = ApplianceProfile.parse(text)
        val profile = direct ?: if (c.gemini.isConfigured) {
            ApplianceProfile.parse(c.gemini.ask(ApplianceProfile.prompt(a.type, a.brand, a.model, lang()) + "\n\nTEXT:\n" + text.take(30_000)))
        } else null
        if (profile == null) throw IllegalStateException(getApplication<Application>().getString(R.string.apl_parse_failed))
        repo.setProfile(a.id, profile, if (direct != null) "ai_chat" else "text")
        say(R.string.apl_profile_saved, profile.programs.size.toString())
    }

    fun importPhoto(a: ApplianceEntity, uri: Uri) = launchSafe {
        val text = c.gemini.askWithImage(getApplication(), uri, ApplianceProfile.prompt(a.type, a.brand, a.model, lang()) +
            "\nThe photo shows the control panel or a page of the manual; fill in what is visible.")
        val profile = ApplianceProfile.parse(text) ?: throw IllegalStateException(getApplication<Application>().getString(R.string.apl_parse_failed))
        val old = profileOf(a)
        val merged = if (old == null) profile else old.copy(
            programs = (old.programs + profile.programs).distinctBy { it.name },
            options = (old.options + profile.options).distinctBy { it.name },
            symbols = (old.symbols + profile.symbols).distinctBy { it.symbol },
            errors = (old.errors + profile.errors).distinctBy { it.code },
            maintenance = (old.maintenance + profile.maintenance).distinctBy { it.task },
        )
        repo.setProfile(a.id, merged, "photo")
        say(R.string.apl_profile_saved, merged.programs.size.toString())
    }

    fun useGeneric(a: ApplianceEntity) = launchSafe {
        repo.setProfile(a.id, com.rimon.smartfamily.smart.LaundryAdvisor.genericWasher(lang()), "generic")
    }

    // ---------- laundry ----------

    fun readCareLabel(uri: Uri) = launchSafe {
        val text = c.gemini.askWithImage(getApplication(), uri,
            "Read the laundry care symbols on this clothing label. Answer only JSON: {\"maxTemp\":number or null (°C)," +
                "\"tumbleDry\":true|false|null,\"notes\":string (in ${lang()}, bleach/iron/dry-clean in a few words)}")
        val o = runCatching { json.parseToJsonElement(text) as JsonObject }.getOrNull() ?: return@launchSafe say(R.string.shop_nothing_found)
        _careLabel.value = CareLabel(
            o["maxTemp"]?.jsonPrimitive?.intOrNull,
            o["tumbleDry"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull(),
            o["notes"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        )
    }
    fun clearCareLabel() { _careLabel.value = null }

    fun adviseAi(profile: ApplianceProfile, load: LaundryLoad, person: String) = launchSafe {
        _aiAdvice.value = ""
        val text = c.gemini.ask(
            "In ${lang()}, recommend how to wash this load using ONLY these programs of the user's machine: ${ApplianceProfile.toJson(profile.copy(errors = emptyList(), symbols = emptyList()))}. " +
                "Load: fabric=${load.fabric}, colour=${load.color}, soil=${load.soil}, care label max=${load.labelMaxTemp ?: "unknown"}°C, for=$person. " +
                "Answer only JSON: {\"advice\":string (program, temperature, spin, options, detergent, one short reason, page numbers)}"
        )
        _aiAdvice.value = runCatching { (json.parseToJsonElement(text) as JsonObject)["advice"]?.jsonPrimitive?.contentOrNull }.getOrNull() ?: text
    }

    /** 2.2: AI dry advice using ONLY this dryer's programmes. */
    fun adviseDryAi(profile: ApplianceProfile, fabric: String, dryness: String, size: String, tumbleAllowed: Boolean?) = launchSafe {
        _aiAdvice.value = ""
        val text = c.gemini.ask(
            "In ${lang()}, recommend how to DRY this load in a tumble dryer, using ONLY these programmes of the user's dryer: ${ApplianceProfile.toJson(profile.copy(errors = emptyList(), symbols = emptyList()))}. " +
                "Load: fabric=$fabric, wanted dryness=$dryness, load size=$size, care label allows tumble drying=${tumbleAllowed ?: "unknown"}. " +
                "If the label forbids tumble drying, say so and suggest air drying. " +
                "Answer only JSON: {\"advice\":string (programme, dryness level, heat, options such as anti-crease, one short reason, page numbers)}"
        )
        _aiAdvice.value = runCatching { (json.parseToJsonElement(text) as JsonObject)["advice"]?.jsonPrimitive?.contentOrNull }.getOrNull() ?: text
    }

    fun savePreset(applianceId: String, familyId: String, name: String, program: String, temperature: String, spin: String, options: String) = launchSafe {
        repo.savePreset(LaundryPresetEntity(UUID.randomUUID().toString(), familyId, applianceId, name, program, temperature, spin, options))
    }
    fun deletePreset(id: String) = launchSafe { repo.deletePreset(id) }

    fun addMaintenance(a: ApplianceEntity) = launchSafe {
        val p = profileOf(a) ?: return@launchSafe
        val n = repo.addMaintenanceChores(a, p, uid(), Cities.byKey(c.settings.current().zmanimCity))
        say(R.string.apl_maintenance_added, n.toString())
    }

    // ---------- smart home ----------

    fun refreshLive(a: ApplianceEntity) = viewModelScope.launch {
        val st = runCatching {
            when (a.ecosystem) {
                "home_connect" -> c.homeConnect.status(a.externalId)
                "smartthings" -> c.smartThings.status(a.externalId)
                else -> null
            }
        }.getOrNull() ?: return@launch
        _live.value = _live.value + (a.id to st)
    }

    suspend fun liveLoop(a: ApplianceEntity) {
        while (true) { refreshLive(a); delay(30_000) }
    }

    fun startProgram(a: ApplianceEntity, programKey: String) = launchSafe {
        c.homeConnect.start(a.externalId, programKey)
        refreshLive(a)
    }

    private val _programs = MutableStateFlow<List<String>>(emptyList())
    val programs = _programs.asStateFlow()
    fun loadPrograms(a: ApplianceEntity) = launchSafe { _programs.value = c.homeConnect.programs(a.externalId) }

    fun setHomeConnectClientId(id: String) = launchSafe { c.settings.setHomeConnectClientId(id) }

    /** 2.2 status line of the Home Connect wizard (null = nothing to report). */
    private val _hcStatus = MutableStateFlow<Int?>(null)
    val hcStatus = _hcStatus.asStateFlow()
    private var hcJob: kotlinx.coroutines.Job? = null
    @Volatile private var pokeNow = false

    /**
     * Device flow: shows a code and a link, then waits for the user to approve on Home Connect's site.
     * 2.2: a network hiccup (VPN, background limits) no longer ends the wait — it keeps trying until the code expires,
     * and checks at once when the user comes back to the app ([pollNow]).
     */
    fun connectHomeConnect() {
        hcJob?.cancel()
        hcJob = viewModelScope.launch {
            _hcStatus.value = null
            val code = try { c.homeConnect.startLogin() } catch (e: java.io.IOException) {
                _hcStatus.value = if (e is java.net.UnknownHostException || e is java.io.InterruptedIOException) R.string.hc_offline else R.string.hc_client_bad
                return@launch
            }
            _hcCode.value = code
            var waited = 0L
            while (System.currentTimeMillis() < code.expiresAt) {
                val step = code.interval * 1000L
                while (waited < step && !pokeNow) { delay(250); waited += 250 }
                waited = 0; pokeNow = false
                try {
                    if (c.homeConnect.pollLogin(code)) {
                        _hcCode.value = null; _hcStatus.value = R.string.sh_connected; say(R.string.sh_connected); return@launch
                    }
                    if (_hcStatus.value == R.string.hc_offline) _hcStatus.value = null
                } catch (e: com.rimon.smartfamily.smart.HomeConnectClient.LoginFailed) {
                    _hcCode.value = null
                    _hcStatus.value = when (e.reason) { "denied" -> R.string.hc_denied; "expired" -> R.string.hc_expired; "user" -> R.string.hc_user; else -> R.string.hc_client_bad }
                    return@launch
                } catch (e: java.io.IOException) {
                    _hcStatus.value = R.string.hc_offline // keep waiting
                }
            }
            _hcCode.value = null; _hcStatus.value = R.string.hc_expired
        }
    }
    fun pollNow() { pokeNow = true }

    fun disconnectHomeConnect() { c.homeConnect.disconnect() }
    fun setSmartThingsToken(t: String?) { c.smartThings.setToken(t); if (t != null) say(R.string.sh_connected) }
    val smartThingsExpiresAt: Long? get() = c.smartThings.tokenExpiresAt
    val smartThingsOAuth: Boolean get() = c.smartThings.usesOAuth
    fun smartThingsAuthorizeUrl(clientId: String, state: String) = c.smartThings.authorizeUrl(clientId.trim(), com.rimon.smartfamily.smart.SmartThingsClient.REDIRECT, state)
    fun finishSmartThingsOAuth(clientId: String, secret: String, pasted: String, onDone: () -> Unit) = launchSafe {
        c.smartThings.finishOAuth(clientId.trim(), secret.trim(), com.rimon.smartfamily.smart.SmartThingsClient.REDIRECT, pasted)
        say(R.string.sh_connected); onDone()
    }
    fun disconnectSmartThings() = c.smartThings.disconnect()
    /** Checks the SmartThings connection by listing devices. */
    private val _stCheck = MutableStateFlow<Int?>(null)
    val stCheck = _stCheck.asStateFlow()
    fun testSmartThings() = viewModelScope.launch {
        _stCheck.value = runCatching { c.smartThings.devices(); R.string.st_check_ok }.getOrElse { e ->
            when (e) { is com.rimon.smartfamily.smart.SmartThingsClient.Expired -> R.string.st_expired; is java.net.UnknownHostException -> R.string.hc_offline; else -> R.string.st_check_fail }
        }
    }

    fun loadDevices(ecosystem: String) = launchSafe {
        _devices.value = when (ecosystem) {
            "home_connect" -> c.homeConnect.devices()
            "smartthings" -> c.smartThings.devices()
            else -> emptyList()
        }
        if (_devices.value.isEmpty()) say(R.string.sh_no_devices)
    }

    fun link(a: ApplianceEntity, ecosystem: String, device: SmartDevice?) = launchSafe {
        repo.save(a.copy(ecosystem = if (device == null) "" else ecosystem, externalId = device?.id.orEmpty()))
    }
}
