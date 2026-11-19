package com.rimon.smartfamily.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rimon.smartfamily.R
import com.rimon.smartfamily.SmartFamilyApp
import com.rimon.smartfamily.data.db.BudgetEntryEntity
import com.rimon.smartfamily.data.db.HomeEntryEntity
import com.rimon.smartfamily.data.db.PollEntity
import com.rimon.smartfamily.data.db.TripEntity
import com.rimon.smartfamily.data.db.TripItemEntity
import com.rimon.smartfamily.data.repo.PackingLists
import com.rimon.smartfamily.data.repo.Place
import com.rimon.smartfamily.smart.AiNotConfiguredException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

class ExtrasViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as SmartFamilyApp).container
    private val repo = c.extras
    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _places = MutableStateFlow<List<Place>>(emptyList())
    val places = _places.asStateFlow()

    private fun launchSafe(block: suspend () -> Unit) = viewModelScope.launch {
        _busy.value = true
        try { block() }
        catch (e: AiNotConfiguredException) { _messages.tryEmit(UiMessage.Res(R.string.ai_needed)) }
        catch (e: Exception) { _messages.tryEmit(UiMessage.Text(e.message ?: e.javaClass.simpleName)) }
        finally { _busy.value = false }
    }
    private suspend fun uid() = c.settings.current().userId
    private val hebrew get() = getApplication<Application>().resources.configuration.locales[0].language.let { it == "iw" || it == "he" }

    fun members(familyId: String) = c.families.observeMembers(familyId)

    // ---------- travel ----------
    fun trips(familyId: String) = repo.trips(familyId)
    fun trip(id: String) = repo.trip(id)
    fun tripItems(id: String) = repo.tripItems(id)

    fun searchPlace(q: String) = launchSafe { _places.value = if (q.isBlank()) emptyList() else repo.meteo.search(q, if (hebrew) "he" else "en") }

    fun createTrip(familyId: String, title: String, place: Place?, start: String, end: String, kinds: Set<String>, travelers: List<String>, onCreated: (String) -> Unit) = launchSafe {
        val t = TripEntity(
            id = repo.newId(), familyId = familyId, title = title.trim(), destination = place?.let { "${it.name}, ${it.country}" }.orEmpty(),
            lat = place?.lat ?: 0.0, lon = place?.lon ?: 0.0, startDate = start, endDate = end,
            kinds = kinds.joinToString(","), travelers = travelers.joinToString(","), createdBy = uid(),
        )
        repo.saveTrip(t)
        generate(t)
        onCreated(t.id)
    }

    /** Builds the packing list from trip type, travellers, days and the free Open-Meteo forecast. */
    fun regenerate(t: TripEntity) = launchSafe { generate(t) }

    private suspend fun generate(t: TripEntity) {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val days = runCatching { ((fmt.parse(t.endDate)!!.time - fmt.parse(t.startDate)!!.time) / 86_400_000L).toInt() + 1 }.getOrDefault(2)
        val weather = if (t.lat != 0.0 || t.lon != 0.0) runCatching { repo.meteo.forecast(t.lat, t.lon, t.startDate, t.endDate) }.getOrNull() else null
        val summary = weather?.let { "%.0f°–%.0f° · ☔ %d%%".format(it.minC, it.maxC, it.rainChance) }.orEmpty()
        val updated = if (summary.isNotEmpty()) t.copy(weather = summary) else t
        if (summary.isNotEmpty()) repo.saveTrip(updated)
        val n = t.travelers.split(',').count { it.isNotBlank() }
        repo.addItems(updated, PackingLists.build(t.kinds.split(',').filter { it.isNotBlank() }.toSet(), weather, n, days, hebrew))
        _messages.tryEmit(UiMessage.Res(if (weather == null) R.string.trip_generated_no_weather else R.string.trip_generated))
    }

    fun addItem(t: TripEntity, title: String, ownerId: String) = launchSafe {
        repo.saveItem(TripItemEntity(id = repo.newId(), familyId = t.familyId, tripId = t.id, title = title.trim(), ownerId = ownerId, sort = 999))
    }
    fun togglePacked(i: TripItemEntity) = launchSafe { repo.saveItem(i.copy(packed = !i.packed, packedBy = if (!i.packed) uid() else "")) }
    fun deleteItem(i: TripItemEntity) = launchSafe { repo.deleteItem(i) }
    fun resetPacked(t: TripEntity) = launchSafe { repo.resetPacked(t.id) }
    fun deleteTrip(t: TripEntity) = launchSafe { repo.deleteTrip(t) }

    // ---------- budget ----------
    fun month(familyId: String, year: Int, month: Int) = repo.month(familyId, year, month)
    fun limits(familyId: String) = repo.limits(familyId)
    /** BUD-2: after an expense, warn at 80% and 100% of that category's monthly limit. */
    fun saveEntry(e: BudgetEntryEntity) = launchSafe {
        repo.saveEntry(e)
        if (e.kind != "EXPENSE") return@launchSafe
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = e.date }
        val spent = repo.month(e.familyId, cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH)).first()
            .filter { it.kind == "EXPENSE" && it.category == e.category }.sumOf { it.amount }
        val limit = repo.limits(e.familyId).first().firstOrNull { it.category == e.category }?.monthly ?: 0.0
        if (limit <= 0) return@launchSafe
        val pct = (spent / limit * 100).toInt()
        if (pct >= 100) _messages.tryEmit(UiMessage.Res(R.string.budget_over, "$pct"))
        else if (pct >= 80) _messages.tryEmit(UiMessage.Res(R.string.budget_near, "$pct"))
    }

    /** BUD-3: export a month to CSV (opens in Excel / Google Sheets). */
    fun exportCsv(uri: android.net.Uri, entries: List<BudgetEntryEntity>) = launchSafe {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        fun q(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
        val csv = buildString {
            append('\uFEFF') // so Excel opens Hebrew correctly
            append("date,kind,category,amount,description,monthly\n")
            entries.forEach { e -> append("${fmt.format(java.util.Date(e.date))},${e.kind},${e.category},${e.amount},${q(e.note)},${e.monthly}\n") }
        }
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            getApplication<Application>().contentResolver.openOutputStream(uri, "wt")?.use { it.write(csv.toByteArray()) }
        }
        _messages.tryEmit(UiMessage.Res(R.string.budget_exported))
    }
    fun newEntry(familyId: String, userId: String, date: Long) =
        BudgetEntryEntity(id = repo.newId(), familyId = familyId, amount = 0.0, date = date, payer = userId, createdBy = userId)
    fun deleteEntry(e: BudgetEntryEntity) = launchSafe { repo.deleteEntry(e) }
    fun setLimit(familyId: String, category: String, monthly: Double) = launchSafe { repo.setLimit(familyId, category, monthly) }

    // ---------- home book & knowledge ----------
    fun section(familyId: String, section: String) = repo.section(familyId, section)
    fun newHomeEntry(familyId: String, userId: String, section: String) =
        HomeEntryEntity(id = repo.newId(), familyId = familyId, section = section, kind = if (section == "HOME") "CONTACT" else "HOWTO", title = "", createdBy = userId)
    fun saveHomeEntry(e: HomeEntryEntity) = launchSafe { repo.saveHomeEntry(e) }
    fun deleteHomeEntry(e: HomeEntryEntity) = launchSafe { repo.deleteHomeEntry(e) }

    /** Saves a secret note (Wi-Fi password, door code) encrypted for the family (KNW-2). */
    fun saveSecret(e: HomeEntryEntity, secret: String) = launchSafe {
        val enc = c.chat.encryptForFamily(e.familyId, secret)
        if (enc == null) _messages.tryEmit(UiMessage.Res(R.string.secret_needs_chat))
        repo.saveHomeEntry(e.copy(body = enc ?: secret))
    }
    suspend fun openSecret(body: String): String? = runCatching { c.chat.decryptNote(body) }.getOrNull()

    /** Knowledge box: ask Gemini to write a short family how-to from a rough note (optional, free tier). */
    fun polish(e: HomeEntryEntity, onDone: (String) -> Unit) = launchSafe {
        val lang = if (hebrew) "Hebrew" else "English"
        onDone(c.gemini.ask("Rewrite this family household note as a short clear how-to in $lang, numbered steps, no intro. Title: ${e.title}\n\n${e.body}").trim())
    }

    // ---------- polls ----------
    fun polls(familyId: String) = repo.polls(familyId)
    fun votes(familyId: String) = repo.votes(familyId)
    fun createPoll(familyId: String, question: String, options: List<String>, multi: Boolean, anonymous: Boolean, closesAt: Long) = launchSafe {
        repo.savePoll(PollEntity(id = repo.newId(), familyId = familyId, question = question.trim(), options = options.map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n"),
            multi = multi, anonymous = anonymous, closesAt = closesAt, createdBy = uid()))
    }
    fun vote(p: PollEntity, choice: Int) = launchSafe {
        if (p.closed || (p.closesAt in 1 until System.currentTimeMillis())) return@launchSafe _messages.tryEmit(UiMessage.Res(R.string.poll_closed)).let { }
        repo.vote(p, uid(), choice)
    }
    fun closePoll(p: PollEntity) = launchSafe { repo.savePoll(p.copy(closed = true)) }
    fun deletePoll(p: PollEntity) = launchSafe { repo.deletePoll(p) }
}
