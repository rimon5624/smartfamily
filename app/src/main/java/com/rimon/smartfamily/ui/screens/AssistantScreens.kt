package com.rimon.smartfamily.ui.screens

import com.rimon.smartfamily.ui.components.SfTopAppBar
import android.app.Application
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rimon.smartfamily.R
import com.rimon.smartfamily.SmartFamilyApp
import com.rimon.smartfamily.data.db.EventEntity
import com.rimon.smartfamily.data.db.HomeEntryEntity
import com.rimon.smartfamily.data.db.SearchHit
import com.rimon.smartfamily.data.repo.Schedule
import com.rimon.smartfamily.smart.Cities
import com.rimon.smartfamily.smart.JewishDays
import com.rimon.smartfamily.smart.QuickAdd
import com.rimon.smartfamily.smart.QuickKind
import com.rimon.smartfamily.smart.SpeechParser
import com.rimon.smartfamily.ui.UiMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

class AssistantViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as SmartFamilyApp).container
    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()
    val hits = MutableStateFlow<List<SearchHit>>(emptyList())

    private fun launchSafe(block: suspend () -> Unit) = viewModelScope.launch {
        try { block() } catch (e: Exception) { _messages.tryEmit(UiMessage.Text(e.message ?: e.javaClass.simpleName)) }
    }

    fun search(familyId: String, q: String) = launchSafe {
        hits.value = if (q.trim().length < 2) emptyList() else c.db.search().search(familyId, q.trim())
    }

    /** Saves a quick-add sentence as the chosen kind of item. */
    fun save(familyId: String, kind: QuickKind, title: String, date: LocalDate?, time: LocalTime?, asRequest: Boolean) = launchSafe {
        val s = c.settings.current()
        val uid = s.userId
        when (kind) {
            QuickKind.SHOPPING -> {
                val lists = c.shopping.observeLists(familyId).first()
                val list = lists.firstOrNull() ?: c.shopping.createList(familyId, getApplication<Application>().getString(R.string.shop_default_list), uid)
                val n = c.shopping.addItems(list.id, SpeechParser.parse(title), uid, asRequest)
                _messages.tryEmit(UiMessage.Res(R.string.shop_added, n.toString()))
            }
            QuickKind.EVENT -> {
                val d = date ?: LocalDate.now(JewishDays.ZONE)
                val start = JewishDays.startMs(d) + ((time ?: LocalTime.of(9, 0)).toSecondOfDay() * 1000L)
                c.homeLife.saveEvent(EventEntity(id = UUID.randomUUID().toString(), familyId = familyId, title = title, startAt = start,
                    endAt = start + 3_600_000L, allDay = time == null, ownerId = uid))
                _messages.tryEmit(UiMessage.Res(R.string.qa_saved_event))
            }
            QuickKind.CHORE -> {
                val ch = c.homeLife.newChore(familyId, title, uid, Schedule.ONCE, Cities.byKey(s.zmanimCity))
                val due = JewishDays.startMs(date ?: LocalDate.now(JewishDays.ZONE)) + ((time ?: LocalTime.of(18, 0)).toSecondOfDay() * 1000L)
                c.db.chores().get(ch.id)?.let { c.homeLife.saveChore(it.copy(nextDue = due), Cities.byKey(s.zmanimCity)) }
                _messages.tryEmit(UiMessage.Res(R.string.qa_saved_chore))
            }
            QuickKind.NOTE -> {
                c.extras.saveHomeEntry(HomeEntryEntity(id = UUID.randomUUID().toString(), familyId = familyId, section = "KNOWLEDGE", kind = "NOTE",
                    title = title.take(60), body = if (title.length > 60) title else "", createdBy = uid))
                _messages.tryEmit(UiMessage.Res(R.string.qa_saved_note))
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------- quick add

@Composable
fun QuickAddDialog(vm: AssistantViewModel, viewer: Viewer, onDismiss: () -> Unit, initialText: String = "") {
    var text by rememberSaveable { mutableStateOf(initialText) }
    val guess = remember(text) { QuickAdd.guess(text) }
    var kind by remember(guess.kind) { mutableStateOf(guess.kind) }
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { text = it }
    }
    val lang = stringResource(R.string.speech_locale)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.qa_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(text, { text = it }, placeholder = { Text(stringResource(R.string.qa_hint)) }, modifier = Modifier.weight(1f))
                    IconButton(onClick = {
                        runCatching {
                            voice.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang))
                        }
                    }) { Icon(Icons.Outlined.Mic, stringResource(R.string.qa_voice)) }
                }
                if (text.isNotBlank()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(QuickKind.SHOPPING to R.string.qa_shopping, QuickKind.EVENT to R.string.qa_event, QuickKind.CHORE to R.string.qa_chore, QuickKind.NOTE to R.string.qa_note).forEach { (k, l) ->
                            FilterChip(kind == k, { kind = k }, { Text(stringResource(l)) })
                        }
                    }
                    Text(
                        "→ " + guess.title + (guess.date?.let { " · ${it.dayOfMonth}/${it.monthValue}" } ?: "") + (guess.time?.let { " · $it" } ?: ""),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank() && viewer.familyId.isNotEmpty(), onClick = {
                vm.save(viewer.familyId, kind, guess.title, guess.date, guess.time, asRequest = viewer.isChild)
                onDismiss()
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

// ---------------------------------------------------------------------------------------------- search

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(vm: AssistantViewModel, viewer: Viewer, onBack: () -> Unit, onOpen: (SearchHit) -> Unit) {
    var q by rememberSaveable { mutableStateOf("") }
    val hits by vm.hits.collectAsState()
    LaunchedEffect(q) { delay(250); vm.search(viewer.familyId, q) }
    Scaffold(topBar = {
        SfTopAppBar(
            title = {
                OutlinedTextField(q, { q = it }, placeholder = { Text(stringResource(R.string.search_all)) }, leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    singleLine = true, modifier = Modifier.fillMaxWidth().padding(end = 8.dp))
            },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) } },
        )
    }) { inner ->
        LazyColumn(Modifier.padding(inner), contentPadding = PaddingValues(16.dp)) {
            if (q.length >= 2 && hits.isEmpty()) item { Text(stringResource(R.string.search_none)) }
            items(hits, key = { it.kind + it.id + it.title }) { h ->
                Row(Modifier.fillMaxWidth().clickable { onOpen(h) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(kindEmoji(h.kind), Modifier.size(32.dp))
                    Column(Modifier.weight(1f)) {
                        Text(h.title, style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(kindLabel(h.kind)) + if (h.sub.isNotBlank() && h.kind != "home") " · " + h.sub else "", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

private fun kindEmoji(k: String) = when (k) { "item" -> "🛒"; "recipe" -> "🍲"; "home" -> "🏠"; "event" -> "📅"; "date" -> "🎂"; "trip" -> "🧳"; "poll" -> "📊"; "chore" -> "🧹"; "pantry" -> "🥫"; "appliance" -> "🔌"; else -> "•" }
private fun kindLabel(k: String) = when (k) {
    "item" -> R.string.mod_shopping; "recipe" -> R.string.mod_recipes; "home" -> R.string.mod_homebook; "event", "date" -> R.string.mod_calendar
    "trip" -> R.string.mod_travel; "poll" -> R.string.mod_polls; "chore" -> R.string.mod_chores; "pantry" -> R.string.mod_inventory; "appliance" -> R.string.mod_appliances
    else -> R.string.search_all
}

// ---------------------------------------------------------------------------------------------- "what now" home panel

/**
 * The top of the home screen adapts to the time of day (2.0): mornings show what to pack and
 * today's chores, evenings the shopping list and tomorrow, Friday the Shabbat countdown; plus
 * today's holiday and the family dates of the coming week.
 */
@Composable
fun NowPanel(
    viewer: Viewer, cityKey: String,
    life: com.rimon.smartfamily.ui.HomeLifeViewModel, kitchen: com.rimon.smartfamily.ui.KitchenViewModel, shop: com.rimon.smartfamily.ui.ShoppingViewModel,
    onQuickAdd: () -> Unit, onSearch: () -> Unit, onOpen: (String) -> Unit,
) {
    val today = LocalDate.now(JewishDays.ZONE)
    val city = Cities.byKey(cityKey)
    val info = remember(today, cityKey) { JewishDays.info(today, city) }
    val hour = LocalTime.now(JewishDays.ZONE).hour
    val chores by remember(viewer.familyId) { life.chores(viewer.familyId) }.collectAsState(initial = emptyList())
    val events by remember(viewer.familyId) { life.events(viewer.familyId) }.collectAsState(initial = emptyList())
    val dates by remember(viewer.familyId) { life.dates(viewer.familyId) }.collectAsState(initial = emptyList())
    val counts by remember(viewer.familyId) { shop.counts(viewer.familyId) }.collectAsState(initial = emptyList())
    val focusDay = if (hour >= 17) today.plusDays(1) else today
    val packed by remember(viewer.familyId, focusDay) { kitchen.packed(viewer.familyId, focusDay.toString(), focusDay.toString()) }.collectAsState(initial = emptyList())
    val plan by remember(viewer.familyId, focusDay) { kitchen.plan(viewer.familyId, focusDay.toString(), focusDay.toString()) }.collectAsState(initial = emptyList())
    val endToday = JewishDays.startMs(today.plusDays(1))
    val dueChores = chores.filter { it.active && it.nextDue in 1 until endToday && (it.assignee.isBlank() || it.assignee == viewer.userId) }
    val todays = remember(events) { life.expand(events, JewishDays.startMs(focusDay), JewishDays.startMs(focusDay.plusDays(1))) }
    val upcoming = remember(dates) { dates.mapNotNull { life.nextOccurrence(it) }.filter { it.daysAway in 0..7 }.sortedBy { it.daysAway } }
    val openItems = counts.sumOf { it.open }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = onQuickAdd, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.size(6.dp)); Text(stringResource(R.string.qa_title)) }
            OutlinedButton(onClick = onSearch) { Icon(Icons.Outlined.Search, stringResource(R.string.search_all)) }
        }
        if (info.holidays.isNotEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer), modifier = Modifier.fillMaxWidth()) {
                Text("✨ " + info.holidays.joinToString(" · ") + " · " + info.hFull, Modifier.padding(12.dp), style = MaterialTheme.typography.titleSmall)
            }
        }
        val lines = mutableListOf<Pair<String, String>>() // text to route
        if (packed.isNotEmpty()) lines += stringResource(if (focusDay == today) R.string.now_pack_today else R.string.now_pack_tomorrow, packed.joinToString(" · ") { it.items.replace('\n', ',').take(60) }) to "kitchen/2"
        plan.firstOrNull()?.let { lines += stringResource(if (focusDay == today) R.string.now_cook_today else R.string.now_cook_tomorrow, plan.joinToString(", ") { it.title }) to "kitchen/1" }
        if (dueChores.isNotEmpty()) lines += stringResource(R.string.now_chores, dueChores.size, dueChores.take(3).joinToString(", ") { it.title }) to "chores"
        todays.take(3).forEach { o -> lines += ("📅 " + (if (o.event.allDay) "" else String.format("%tR ", o.start)) + o.event.title) to "calendar" }
        if (openItems > 0 && (hour >= 16 || today.dayOfWeek == java.time.DayOfWeek.THURSDAY || today.dayOfWeek == java.time.DayOfWeek.FRIDAY)) lines += stringResource(R.string.now_shopping, openItems) to "shopping"
        upcoming.take(3).forEach { o -> lines += ("🎂 " + o.date.title + " · " + (if (o.daysAway == 0) stringResource(R.string.cal_today) else stringResource(R.string.date_in_days, o.daysAway))) to "calendar" }
        if (lines.isNotEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(when (hour) { in 5..11 -> R.string.now_morning; in 12..16 -> R.string.now_noon; in 17..21 -> R.string.now_evening; else -> R.string.now_night }),
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    lines.forEach { (t, route) -> Text(t, Modifier.fillMaxWidth().clickable { onOpen(route) }, style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
    }
}
