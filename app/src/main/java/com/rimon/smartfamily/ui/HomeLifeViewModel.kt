package com.rimon.smartfamily.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rimon.smartfamily.R
import com.rimon.smartfamily.SmartFamilyApp
import com.rimon.smartfamily.data.db.ChoreEntity
import com.rimon.smartfamily.data.db.EventEntity
import com.rimon.smartfamily.data.db.FamilyDateEntity
import com.rimon.smartfamily.data.db.RewardEntity
import com.rimon.smartfamily.data.repo.ChoreTemplate
import com.rimon.smartfamily.data.repo.Schedule
import com.rimon.smartfamily.smart.Cities
import com.rimon.smartfamily.smart.ShabbatTimes
import com.rimon.smartfamily.smart.Zmanim
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class HomeLifeViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as SmartFamilyApp).container
    private val repo = c.homeLife

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    private fun launchSafe(block: suspend () -> Unit) = viewModelScope.launch {
        try { block() } catch (e: Exception) { _messages.tryEmit(UiMessage.Text(e.message ?: e.javaClass.simpleName)) }
    }

    private suspend fun uid() = c.settings.current().userId
    private suspend fun city() = Cities.byKey(c.settings.current().zmanimCity)

    fun chores(familyId: String) = repo.observeChores(familyId)
    fun completionsSince(familyId: String, since: Long) = repo.observeCompletionsSince(familyId, since)
    fun pending(familyId: String) = repo.observePendingApprovals(familyId)
    fun balances(familyId: String) = repo.observeBalances(familyId)
    fun rewards(familyId: String) = repo.observeRewards(familyId)
    fun dates(familyId: String) = repo.observeDates(familyId)
    fun events(familyId: String) = repo.observeEvents(familyId)
    fun members(familyId: String) = c.families.observeMembers(familyId)
    fun nextOccurrence(d: FamilyDateEntity) = repo.nextOccurrence(d)
    fun expand(events: List<EventEntity>, from: Long, to: Long) = repo.expand(events, from, to)
    fun dateOccurrences(d: FamilyDateEntity, from: java.time.LocalDate, to: java.time.LocalDate) = repo.occurrencesBetween(d, from, to)
    fun setCalendar(hebFirst: Boolean? = null, basis: String? = null, view: String? = null, shabbatRow: Boolean? = null) =
        viewModelScope.launch { c.settings.setCalendar(hebFirst, basis, view, shabbatRow) }

    suspend fun shabbat(cityKey: String): ShabbatTimes = withContext(Dispatchers.Default) { Zmanim.nextShabbat(Cities.byKey(cityKey)) }

    fun newChoreDraft(familyId: String, userId: String) = ChoreEntity(id = UUID.randomUUID().toString(), familyId = familyId, title = "", createdBy = userId)

    fun saveChore(ch: ChoreEntity, scheduleChanged: Boolean) = launchSafe {
        repo.saveChore(if (scheduleChanged) ch.copy(nextDue = 0L) else ch, city())
    }

    fun complete(ch: ChoreEntity, doneBy: String, needsApproval: Boolean) = launchSafe {
        repo.complete(ch.id, doneBy, needsApproval, city())
        _messages.tryEmit(UiMessage.Res(R.string.chores_done))
    }

    fun snooze(id: String) = launchSafe { repo.snooze(id, 3) }
    fun deleteChore(id: String) = launchSafe { repo.deleteChore(id) }
    fun approve(id: String) = launchSafe { repo.approveCompletion(id, uid()) }
    fun reject(id: String) = launchSafe { repo.rejectCompletion(id) }

    fun addTemplates(familyId: String, chosen: List<Pair<ChoreTemplate, String>>) = launchSafe {
        repo.addTemplates(familyId, uid(), chosen, city())
    }

    fun saveReward(familyId: String, title: String, cost: Int) = launchSafe {
        repo.saveReward(RewardEntity(id = UUID.randomUUID().toString(), familyId = familyId, title = title, cost = cost))
    }
    fun deleteReward(id: String) = launchSafe { repo.deleteReward(id) }
    fun redeem(familyId: String, userId: String, reward: RewardEntity) = launchSafe { repo.redeem(familyId, userId, reward) }

    fun saveDate(d: FamilyDateEntity) = launchSafe { repo.saveDate(d) }
    fun deleteDate(id: String) = launchSafe { repo.deleteDate(id) }
    fun saveEvent(e: EventEntity) = launchSafe { repo.saveEvent(e) }
    fun deleteEvent(id: String) = launchSafe { repo.deleteEvent(id) }

    fun setCity(key: String) = launchSafe { c.settings.setZmanimCity(key) }
    fun setReminders(on: Boolean) = launchSafe { c.settings.setChoreReminders(on) }

    companion object {
        private const val FRI = 1 shl 5
        private const val THU = 1 shl 4
        private const val SUN_WED = (1 shl 0) or (1 shl 3)

        /** The built-in chores of spec section 12, with their string resources. */
        val templates: List<Pair<ChoreTemplate, Int>> = listOf(
            ChoreTemplate("laundry", Schedule.WEEKLY, weekdays = (1 shl 0) or (1 shl 2) or (1 shl 4), minutes = 10) to R.string.tpl_laundry,
            ChoreTemplate("toilets", Schedule.BEFORE_SHABBAT, hoursBeforeShabbat = 6, minutes = 20) to R.string.tpl_toilets,
            ChoreTemplate("rooms", Schedule.DAILY, minutes = 10) to R.string.tpl_rooms,
            ChoreTemplate("sheets", Schedule.EVERY_N_DAYS, intervalDays = 14, minutes = 20) to R.string.tpl_sheets,
            ChoreTemplate("floors", Schedule.WEEKLY, weekdays = THU or FRI, minutes = 30) to R.string.tpl_floors,
            ChoreTemplate("bins", Schedule.WEEKLY, weekdays = SUN_WED, minutes = 5) to R.string.tpl_bins,
            ChoreTemplate("dishes", Schedule.DAILY, minutes = 15) to R.string.tpl_dishes,
            ChoreTemplate("shop_shabbat", Schedule.WEEKLY, weekdays = THU, minutes = 60, category = "shabbat") to R.string.tpl_shop_shabbat,
            ChoreTemplate("cook", Schedule.BEFORE_SHABBAT, hoursBeforeShabbat = 8, minutes = 180, category = "shabbat") to R.string.tpl_cook,
            ChoreTemplate("table", Schedule.BEFORE_SHABBAT, hoursBeforeShabbat = 2, minutes = 15, category = "shabbat") to R.string.tpl_table,
            ChoreTemplate("showers", Schedule.BEFORE_SHABBAT, hoursBeforeShabbat = 2, minutes = 45, category = "shabbat") to R.string.tpl_showers,
            ChoreTemplate("timer", Schedule.BEFORE_SHABBAT, hoursBeforeShabbat = 1, minutes = 5, category = "shabbat") to R.string.tpl_timer,
            ChoreTemplate("hotplate", Schedule.BEFORE_SHABBAT, hoursBeforeShabbat = 1, minutes = 5, category = "shabbat") to R.string.tpl_hotplate,
            ChoreTemplate("candles", Schedule.BEFORE_SHABBAT, hoursBeforeShabbat = 1, minutes = 5, category = "shabbat") to R.string.tpl_candles,
        )
    }
}
