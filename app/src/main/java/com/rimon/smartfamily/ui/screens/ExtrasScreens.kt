package com.rimon.smartfamily.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.rimon.smartfamily.R
import com.rimon.smartfamily.data.db.BudgetEntryEntity
import com.rimon.smartfamily.data.db.HomeEntryEntity
import com.rimon.smartfamily.data.db.MemberEntity
import com.rimon.smartfamily.data.db.PollEntity
import com.rimon.smartfamily.data.repo.Place
import com.rimon.smartfamily.ui.ExtrasViewModel
import com.rimon.smartfamily.ui.components.HelpButton
import com.rimon.smartfamily.ui.components.SectionTitle
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private val ymd = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
private fun dmy(ms: Long) = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(ms))
private fun money(v: Double) = "₪" + String.format(Locale.US, "%,.0f", v)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScreenScaffold(title: String, help: Int, onBack: () -> Unit, busy: Boolean, fab: (@Composable () -> Unit)? = null, actions: @Composable () -> Unit = {}, content: @Composable (PaddingValues) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) } },
                actions = { actions(); HelpButton(help) },
            )
        },
        floatingActionButton = { fab?.invoke() },
    ) { inner ->
        Column(Modifier.padding(inner).fillMaxSize()) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            content(PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(label: String, value: String, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier = modifier) { Text(if (value.isBlank()) label else "$label: " + value.split('-').reversed().joinToString("/")) }
    if (open) {
        val state = rememberDatePickerState(initialSelectedDateMillis = runCatching { ymd.parse(value)!!.time }.getOrNull() ?: System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { onPick(ymd.format(Date(it))) }; open = false }) { Text(stringResource(R.string.save)) } },
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(state) }
    }
}

// =================== Travel ===================

private val TRIP_KINDS = listOf(
    "BEACH" to R.string.trip_beach, "NATURE" to R.string.trip_nature, "CAMPING" to R.string.trip_camping, "CITY" to R.string.trip_city,
    "HOTEL" to R.string.trip_hotel, "ABROAD" to R.string.trip_abroad, "SHABBAT" to R.string.trip_shabbat, "BABY" to R.string.trip_baby,
)

@Composable
fun TravelScreen(vm: ExtrasViewModel, viewer: Viewer, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val trips by remember(viewer.familyId) { vm.trips(viewer.familyId) }.collectAsState(initial = emptyList())
    val members by remember(viewer.familyId) { vm.members(viewer.familyId) }.collectAsState(initial = emptyList())
    val busy by vm.busy.collectAsState()
    var creating by remember { mutableStateOf(false) }
    ScreenScaffold(stringResource(R.string.mod_travel), R.string.help_travel, onBack, busy,
        fab = { if (!viewer.isChild) ExtendedFloatingActionButton(onClick = { creating = true }, icon = { Icon(Icons.Outlined.Add, null) }, text = { Text(stringResource(R.string.trip_new)) }) }) { pad ->
        LazyColumn(contentPadding = pad, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (trips.isEmpty()) item { Text(stringResource(R.string.trip_none), style = MaterialTheme.typography.bodyLarge) }
            items(trips, key = { it.id }) { t ->
                Card(Modifier.fillMaxWidth().clickable { onOpen(t.id) }) {
                    Column(Modifier.padding(16.dp)) {
                        Text(t.title, style = MaterialTheme.typography.titleMedium)
                        if (t.destination.isNotBlank()) Text("📍 " + t.destination, style = MaterialTheme.typography.bodyMedium)
                        Text(listOf(t.startDate, t.endDate).filter { it.isNotBlank() }.joinToString(" – ") { it.split('-').reversed().joinToString("/") }, style = MaterialTheme.typography.labelMedium)
                        if (t.weather.isNotBlank()) Text("🌤 " + t.weather, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
    if (creating) NewTripDialog(vm, members.filter { it.status == "ACTIVE" }, onDismiss = { creating = false }) { title, place, s, e, kinds, travelers ->
        creating = false
        vm.createTrip(viewer.familyId, title, place, s, e, kinds, travelers, onOpen)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NewTripDialog(vm: ExtrasViewModel, members: List<MemberEntity>, onDismiss: () -> Unit, onCreate: (String, Place?, String, String, Set<String>, List<String>) -> Unit) {
    val places by vm.places.collectAsState()
    var title by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var place by remember { mutableStateOf<Place?>(null) }
    var start by rememberSaveable { mutableStateOf("") }
    var end by rememberSaveable { mutableStateOf("") }
    var kinds by rememberSaveable { mutableStateOf("") }
    var who by rememberSaveable { mutableStateOf(members.joinToString(",") { it.userId }) }
    val kindSet = kinds.split(',').filter { it.isNotBlank() }.toSet()
    val whoSet = who.split(',').filter { it.isNotBlank() }.toSet()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.trip_new)) },
        text = {
            LazyColumn {
                item {
                    OutlinedTextField(title, { title = it }, label = { Text(stringResource(R.string.trip_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(query, { query = it }, label = { Text(stringResource(R.string.trip_where)) }, singleLine = true, modifier = Modifier.weight(1f))
                        IconButton(onClick = { vm.searchPlace(query) }) { Icon(Icons.Outlined.Search, stringResource(R.string.trip_where)) }
                    }
                    place?.let { Text("📍 ${it.name}, ${it.country}", color = MaterialTheme.colorScheme.primary) }
                }
                if (place == null) items(places) { p ->
                    Text("${p.name}, ${p.country}", Modifier.fillMaxWidth().clickable { place = p; if (title.isBlank()) title = p.name }.padding(8.dp))
                }
                item {
                    Spacer(Modifier.height(8.dp))
                    DateField(stringResource(R.string.trip_from), start, { start = it; if (end.isBlank() || end < it) end = it }, Modifier.fillMaxWidth())
                    DateField(stringResource(R.string.trip_to), end, { end = it }, Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.trip_kind), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TRIP_KINDS.forEach { (k, label) ->
                            FilterChip(k in kindSet, { kinds = (if (k in kindSet) kindSet - k else kindSet + k).joinToString(",") }, { Text(stringResource(label)) })
                        }
                    }
                    Text(stringResource(R.string.trip_travelers), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        members.forEach { m ->
                            FilterChip(m.userId in whoSet, { who = (if (m.userId in whoSet) whoSet - m.userId else whoSet + m.userId).joinToString(",") }, { Text(m.displayName) })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank() && start.isNotBlank(), onClick = { onCreate(title, place, start, end.ifBlank { start }, kindSet, whoSet.toList()) }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private val ITEM_CATS = mapOf(
    "docs" to R.string.pcat_docs, "clothes" to R.string.pcat_clothes, "toiletries" to R.string.pcat_toiletries, "health" to R.string.pcat_health,
    "tech" to R.string.pcat_tech, "food" to R.string.pcat_food, "kids" to R.string.pcat_kids, "camping" to R.string.pcat_camping,
    "shabbat" to R.string.pcat_shabbat, "general" to R.string.pcat_general,
)

@Composable
fun TripScreen(vm: ExtrasViewModel, viewer: Viewer, tripId: String, onBack: () -> Unit, onLeaveHome: () -> Unit = {}) {
    val trip by remember(tripId) { vm.trip(tripId) }.collectAsState(initial = null)
    val items by remember(tripId) { vm.tripItems(tripId) }.collectAsState(initial = emptyList())
    val members by remember(viewer.familyId) { vm.members(viewer.familyId) }.collectAsState(initial = emptyList())
    val busy by vm.busy.collectAsState()
    var text by rememberSaveable { mutableStateOf("") }
    var mineOnly by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val t = trip ?: return
    val names = members.associate { it.userId to it.displayName }
    val shown = items.filter { !mineOnly || it.ownerId.isBlank() || it.ownerId == viewer.userId }
    val packed = shown.count { it.packed }
    ScreenScaffold(t.title, R.string.help_travel, onBack, busy, actions = {
        IconButton(onClick = { vm.regenerate(t) }) { Icon(Icons.Outlined.Refresh, stringResource(R.string.trip_regenerate)) }
        if (!viewer.isChild) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, stringResource(R.string.delete)) }
    }) { pad ->
        LazyColumn(contentPadding = pad) {
            item {
                if (t.destination.isNotBlank()) Text("📍 " + t.destination)
                if (t.weather.isNotBlank()) Text("🌤 " + t.weather, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.trip_progress, packed, shown.size), style = MaterialTheme.typography.titleMedium)
                LinearProgressIndicator(progress = { if (shown.isEmpty()) 0f else packed / shown.size.toFloat() }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
                TextButton(onClick = onLeaveHome) { Text("🏠 " + stringResource(R.string.kit_leave_home)) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(mineOnly, { mineOnly = !mineOnly }, { Text(stringResource(R.string.trip_mine)) })
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { vm.resetPacked(t) }) { Text(stringResource(R.string.trip_unpack_all)) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(text, { text = it }, label = { Text(stringResource(R.string.trip_add_item)) }, singleLine = true, modifier = Modifier.weight(1f))
                    IconButton(enabled = text.isNotBlank(), onClick = { vm.addItem(t, text, if (mineOnly) viewer.userId else ""); text = "" }) { Icon(Icons.Outlined.Add, stringResource(R.string.trip_add_item)) }
                }
            }
            shown.groupBy { it.category }.forEach { (cat, list) ->
                item(key = "h$cat") { SectionTitle(stringResource(ITEM_CATS[cat] ?: R.string.pcat_general)) }
                items(list, key = { it.id }) { i ->
                    Row(Modifier.fillMaxWidth().clickable { vm.togglePacked(i) }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(i.packed, { vm.togglePacked(i) })
                        Column(Modifier.weight(1f)) {
                            Text(
                                i.title + if (i.qty > 1) " ×${i.qty}" else "",
                                textDecoration = if (i.packed) TextDecoration.LineThrough else null,
                                color = if (i.packed) MaterialTheme.colorScheme.outline else Color.Unspecified,
                            )
                            val who = listOfNotNull(names[i.ownerId]?.let { "👤 $it" }, names[i.packedBy]?.takeIf { i.packed }?.let { "✓ $it" })
                            if (who.isNotEmpty()) Text(who.joinToString(" · "), style = MaterialTheme.typography.labelSmall)
                        }
                        var learnMenu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { learnMenu = true }) { Icon(Icons.Outlined.Delete, stringResource(R.string.delete), tint = MaterialTheme.colorScheme.outline) }
                            androidx.compose.material3.DropdownMenu(learnMenu, { learnMenu = false }) {
                                androidx.compose.material3.DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, onClick = { learnMenu = false; vm.deleteItem(i) })
                                androidx.compose.material3.DropdownMenuItem(text = { Text(stringResource(R.string.trip_learn_skip)) }, onClick = { learnMenu = false; vm.learn(i, always = false) })
                                androidx.compose.material3.DropdownMenuItem(text = { Text(stringResource(R.string.trip_learn_always)) }, onClick = { learnMenu = false; vm.learn(i, always = true) })
                            }
                        }
                    }
                }
            }
        }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text(stringResource(R.string.trip_delete_q)) },
        confirmButton = { TextButton(onClick = { confirmDelete = false; vm.deleteTrip(t); onBack() }) { Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
    )
}

// =================== Budget ===================

val BUDGET_CATS = listOf(
    "food" to R.string.bcat_food, "home" to R.string.bcat_home, "bills" to R.string.bcat_bills, "kids" to R.string.bcat_kids,
    "car" to R.string.bcat_car, "health" to R.string.bcat_health, "fun" to R.string.bcat_fun, "clothes" to R.string.bcat_clothes,
    "gifts" to R.string.bcat_gifts, "savings" to R.string.bcat_savings, "salary" to R.string.bcat_salary, "other" to R.string.bcat_other,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BudgetScreen(vm: ExtrasViewModel, viewer: Viewer, onBack: () -> Unit, onMoney: (Int) -> Unit = {}) {
    val now = remember { Calendar.getInstance() }
    var year by rememberSaveable { mutableIntStateOf(now.get(Calendar.YEAR)) }
    var month by rememberSaveable { mutableIntStateOf(now.get(Calendar.MONTH)) }
    val entries by remember(viewer.familyId, year, month) { vm.month(viewer.familyId, year, month) }.collectAsState(initial = emptyList())
    val limits by remember(viewer.familyId) { vm.limits(viewer.familyId) }.collectAsState(initial = emptyList())
    val busy by vm.busy.collectAsState()
    var editing by remember { mutableStateOf<BudgetEntryEntity?>(null) }
    var limitFor by remember { mutableStateOf<String?>(null) }
    val exporter = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) vm.exportCsv(uri, entries)
    }
    val receiptPick = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.receipt(viewer.familyId, uri)
    }
    val income = entries.filter { it.kind == "INCOME" }.sumOf { it.amount }
    val expense = entries.filter { it.kind == "EXPENSE" }.sumOf { it.amount }
    val byCat = entries.filter { it.kind == "EXPENSE" }.groupBy { it.category }.mapValues { e -> e.value.sumOf { it.amount } }
    val monthLabel = remember(year, month) { SimpleDateFormat("LLLL yyyy", Locale.getDefault()).format(Calendar.getInstance().apply { clear(); set(year, month, 1) }.time) }
    fun shift(d: Int) { val c = Calendar.getInstance().apply { clear(); set(year, month, 1); add(Calendar.MONTH, d) }; year = c.get(Calendar.YEAR); month = c.get(Calendar.MONTH) }
    val dayInMonth = remember(year, month) {
        val c = Calendar.getInstance()
        if (c.get(Calendar.YEAR) == year && c.get(Calendar.MONTH) == month) c.timeInMillis else Calendar.getInstance().apply { clear(); set(year, month, 1, 12, 0) }.timeInMillis
    }

    ScreenScaffold(stringResource(R.string.mod_budget), R.string.help_budget, onBack, busy,
        fab = { if (!viewer.isChild) ExtendedFloatingActionButton(onClick = { editing = vm.newEntry(viewer.familyId, viewer.userId, dayInMonth) }, icon = { Icon(Icons.Outlined.Add, null) }, text = { Text(stringResource(R.string.budget_add)) }) }) { pad ->
        LazyColumn(contentPadding = pad, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item { androidx.compose.material3.AssistChip(onClick = { onMoney(0) }, label = { Text("🧾 " + stringResource(R.string.money_bills)) }) }
                    item { androidx.compose.material3.AssistChip(onClick = { onMoney(1) }, label = { Text("🐷 " + stringResource(R.string.money_pocket)) }) }
                    item { androidx.compose.material3.AssistChip(onClick = { onMoney(2) }, label = { Text("🎯 " + stringResource(R.string.money_goals)) }) }
                    item { androidx.compose.material3.AssistChip(onClick = { onMoney(3) }, label = { Text("🤝 " + stringResource(R.string.money_splits)) }) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { shift(-1) }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null) }
                    Text(monthLabel, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = { shift(1) }) { Icon(Icons.AutoMirrored.Outlined.ArrowForward, null) }
                }
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp)) {
                        Column(Modifier.weight(1f)) { Text(stringResource(R.string.budget_income)); Text(money(income), style = MaterialTheme.typography.titleLarge) }
                        Column(Modifier.weight(1f)) { Text(stringResource(R.string.budget_expense)); Text(money(expense), style = MaterialTheme.typography.titleLarge) }
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.budget_balance))
                            Text(money(income - expense), style = MaterialTheme.typography.titleLarge, color = if (income - expense < 0) MaterialTheme.colorScheme.error else Color.Unspecified)
                        }
                    }
                }
                // BUD-3 forecast and 6-month comparison
                val isThisMonth = year == now.get(Calendar.YEAR) && month == now.get(Calendar.MONTH)
                if (isThisMonth && expense > 0) {
                    val day = now.get(Calendar.DAY_OF_MONTH); val dim = now.getActualMaximum(Calendar.DAY_OF_MONTH)
                    val fixed = entries.filter { it.kind == "EXPENSE" && it.monthly }.sumOf { it.amount }
                    val forecast = fixed + (expense - fixed) / day * dim
                    Text(stringResource(R.string.budget_forecast, money(forecast)), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
                }
                val all by remember(viewer.familyId) { vm.allBudget(viewer.familyId) }.collectAsState(initial = emptyList())
                val months = (5 downTo 0).map { back -> Calendar.getInstance().apply { clear(); set(year, month, 1); add(Calendar.MONTH, -back) } }
                val sums = months.map { m ->
                    val from = m.timeInMillis; val to = (m.clone() as Calendar).apply { add(Calendar.MONTH, 1) }.timeInMillis
                    all.filter { it.kind == "EXPENSE" && it.deletedAt == null && (it.monthly || it.date in from until to) }.sumOf { it.amount }
                }
                if (sums.count { it > 0 } >= 2) {
                    val barColor = MaterialTheme.colorScheme.primary
                    val maxV = sums.max().coerceAtLeast(1.0)
                    Row(Modifier.fillMaxWidth().height(110.dp).padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
                        sums.forEachIndexed { i, v ->
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().height((80 * v / maxV).dp.coerceAtLeast(2.dp)).clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                                    .background(if (i == 5) barColor else barColor.copy(alpha = 0.4f)))
                                Text(SimpleDateFormat("MMM", Locale.getDefault()).format(months[i].time), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
                SectionTitle(stringResource(R.string.budget_by_category))
            }
            items(BUDGET_CATS.filter { (k, _) -> k != "salary" && ((byCat[k] ?: 0.0) > 0 || limits.any { it.category == k && it.monthly > 0 }) }, key = { "c" + it.first }) { (k, label) ->
                val spent = byCat[k] ?: 0.0
                val limit = limits.firstOrNull { it.category == k }?.monthly ?: 0.0
                Column(Modifier.fillMaxWidth().clickable(enabled = !viewer.isChild) { limitFor = k }) {
                    Row { Text(stringResource(label), Modifier.weight(1f)); Text(money(spent) + if (limit > 0) " / " + money(limit) else "") }
                    if (limit > 0) LinearProgressIndicator(
                        progress = { (spent / limit).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(),
                        color = if (spent > limit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                }
            }
            item {
                Row {
                    TextButton(enabled = !viewer.isChild, onClick = { receiptPick.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("🧾 " + stringResource(R.string.receipt_scan)) }
                    TextButton(enabled = !viewer.isChild, onClick = { limitFor = "food" }) { Text(stringResource(R.string.budget_set_limit)) }
                    TextButton(enabled = entries.isNotEmpty(), onClick = { exporter.launch("budget-%04d-%02d.csv".format(year, month + 1)) }) { Text(stringResource(R.string.budget_export)) }
                }
                SectionTitle(stringResource(R.string.budget_entries))
            }
            items(entries, key = { it.id }) { e ->
                Row(Modifier.fillMaxWidth().clickable(enabled = !viewer.isChild) { editing = e }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(e.note.ifBlank { stringResource(BUDGET_CATS.firstOrNull { it.first == e.category }?.second ?: R.string.bcat_other) })
                        Text((if (e.monthly) "🔁 " + stringResource(R.string.budget_monthly) + " · " else "") + dmy(e.date), style = MaterialTheme.typography.labelSmall)
                    }
                    Text((if (e.kind == "INCOME") "+" else "−") + money(e.amount), fontWeight = FontWeight.Bold,
                        color = if (e.kind == "INCOME") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
    editing?.let { e -> BudgetEntryDialog(e, onDismiss = { editing = null }, onDelete = { vm.deleteEntry(e); editing = null }) { vm.saveEntry(it); editing = null } }
    limitFor?.let { k ->
        LimitDialog(k, limits.firstOrNull { it.category == k }?.monthly ?: 0.0, onDismiss = { limitFor = null }) { cat, v -> vm.setLimit(viewer.familyId, cat, v); limitFor = null }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BudgetEntryDialog(e: BudgetEntryEntity, onDismiss: () -> Unit, onDelete: () -> Unit, onSave: (BudgetEntryEntity) -> Unit) {
    var kind by remember { mutableStateOf(e.kind) }
    var amount by remember { mutableStateOf(if (e.amount == 0.0) "" else e.amount.toString()) }
    var cat by remember { mutableStateOf(e.category) }
    var note by remember { mutableStateOf(e.note) }
    var monthly by remember { mutableStateOf(e.monthly) }
    var date by remember { mutableStateOf(ymd.format(Date(e.date))) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.budget_add)) },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(kind == "EXPENSE", { kind = "EXPENSE" }, { Text(stringResource(R.string.budget_expense)) })
                    FilterChip(kind == "INCOME", { kind = "INCOME"; if (cat == "other") cat = "salary" }, { Text(stringResource(R.string.budget_income)) })
                }
                OutlinedTextField(amount, { amount = it.filter { ch -> ch.isDigit() || ch == '.' } }, label = { Text("₪") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(note, { note = it }, label = { Text(stringResource(R.string.budget_note)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BUDGET_CATS.forEach { (k, label) -> FilterChip(cat == k, { cat = k }, { Text(stringResource(label)) }) }
                }
                DateField(stringResource(R.string.budget_date), date, { date = it }, Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(monthly, { monthly = it }); Spacer(Modifier.size(8.dp)); Text(stringResource(R.string.budget_monthly))
                }
            }
        },
        confirmButton = {
            TextButton(enabled = (amount.toDoubleOrNull() ?: 0.0) > 0, onClick = {
                onSave(e.copy(kind = kind, amount = amount.toDouble(), category = cat, note = note.trim(), monthly = monthly,
                    date = runCatching { ymd.parse(date)!!.time + 12 * 3_600_000L }.getOrDefault(e.date)))
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            Row {
                if (e.amount > 0) TextButton(onClick = onDelete) { Text(stringResource(R.string.delete)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LimitDialog(initialCat: String, current: Double, onDismiss: () -> Unit, onSave: (String, Double) -> Unit) {
    var cat by remember { mutableStateOf(initialCat) }
    var v by remember { mutableStateOf(if (current > 0) current.toLong().toString() else "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.budget_set_limit)) },
        text = {
            Column {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BUDGET_CATS.filter { it.first != "salary" }.forEach { (k, label) -> FilterChip(cat == k, { cat = k }, { Text(stringResource(label)) }) }
                }
                OutlinedTextField(v, { v = it.filter(Char::isDigit) }, label = { Text(stringResource(R.string.budget_limit_monthly)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { onSave(cat, v.toDoubleOrNull() ?: 0.0) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

// =================== Home book & knowledge box ===================

private val HOME_KINDS = listOf("CONTACT" to R.string.hk_contact, "SERVICE" to R.string.hk_service, "WARRANTY" to R.string.hk_warranty, "DOCUMENT" to R.string.hk_document, "NOTE" to R.string.hk_note)
private val KNOW_KINDS = listOf("HOWTO" to R.string.hk_howto, "NOTE" to R.string.hk_note, "CONTACT" to R.string.hk_contact)

@Composable
fun HomeBookScreen(vm: ExtrasViewModel, viewer: Viewer, startTab: Int, onBack: () -> Unit, onExtras: (Int) -> Unit = {}) {
    var tab by rememberSaveable { mutableIntStateOf(startTab) }
    val section = if (tab == 0) "HOME" else "KNOWLEDGE"
    val entries by remember(viewer.familyId, section) { vm.section(viewer.familyId, section) }.collectAsState(initial = emptyList())
    val busy by vm.busy.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<HomeEntryEntity?>(null) }
    val context = LocalContext.current
    val soon = System.currentTimeMillis() + 30 * 86_400_000L
    val shown = entries.filter { query.isBlank() || listOf(it.title, it.body, it.tags, it.phone).any { s -> s.contains(query, true) } }
    ScreenScaffold(stringResource(if (tab == 0) R.string.mod_homebook else R.string.mod_knowledge), R.string.help_homebook, onBack, busy,
        fab = { if (!viewer.isChild) ExtendedFloatingActionButton(onClick = { editing = vm.newHomeEntry(viewer.familyId, viewer.userId, section) }, icon = { Icon(Icons.Outlined.Add, null) }, text = { Text(stringResource(R.string.hb_add)) }) }) { pad ->
        TabRow(tab) {
            Tab(tab == 0, { tab = 0 }, text = { Text(stringResource(R.string.mod_homebook)) })
            Tab(tab == 1, { tab = 1 }, text = { Text(stringResource(R.string.mod_knowledge)) })
        }
        LazyColumn(contentPadding = pad, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item { androidx.compose.material3.AssistChip(onClick = { onExtras(0) }, label = { Text("🔧 " + stringResource(R.string.hx_repairs)) }) }
                    item { androidx.compose.material3.AssistChip(onClick = { onExtras(1) }, label = { Text("📈 " + stringResource(R.string.hx_meters)) }) }
                    item { androidx.compose.material3.AssistChip(onClick = { onExtras(2) }, label = { Text("🧰 " + stringResource(R.string.hx_kits)) }) }
                    item { androidx.compose.material3.AssistChip(onClick = { onExtras(3) }, label = { Text("🔒 " + stringResource(R.string.hx_secrets)) }) }
                }
            }
            item { OutlinedTextField(query, { query = it }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, placeholder = { Text(stringResource(R.string.hb_search)) }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
            if (shown.isEmpty()) item { Text(stringResource(if (tab == 0) R.string.hb_empty_home else R.string.hb_empty_knowledge)) }
            items(shown, key = { it.id }) { e ->
                val expiring = e.expiresAt != null && e.expiresAt < soon
                Card(Modifier.fillMaxWidth().clickable(enabled = !viewer.isChild) { editing = e },
                    colors = if (expiring) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer) else CardDefaults.cardColors()) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (e.pinned) Icon(Icons.Outlined.PushPin, null, Modifier.size(16.dp))
                            Text(e.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            if (e.phone.isNotBlank()) IconButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + e.phone))) } }) { Icon(Icons.Outlined.Call, e.phone) }
                            if (e.url.isNotBlank()) IconButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(e.url))) } }) { Icon(Icons.Outlined.Link, e.url) }
                        }
                        if (e.body.isNotBlank()) Text(e.body, style = MaterialTheme.typography.bodyMedium, maxLines = 8)
                        e.expiresAt?.let { Text(stringResource(R.string.hb_expires, dmy(it)), style = MaterialTheme.typography.labelMedium, fontWeight = if (expiring) FontWeight.Bold else null) }
                        if (e.tags.isNotBlank()) Text(e.tags.split(',').joinToString(" ") { "#" + it.trim() }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
    editing?.let { e -> HomeEntryDialog(vm, e, if (e.section == "HOME") HOME_KINDS else KNOW_KINDS, onDismiss = { editing = null }, onDelete = { vm.deleteHomeEntry(e); editing = null }) { vm.saveHomeEntry(it); editing = null } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HomeEntryDialog(vm: ExtrasViewModel, e: HomeEntryEntity, kinds: List<Pair<String, Int>>, onDismiss: () -> Unit, onDelete: () -> Unit, onSave: (HomeEntryEntity) -> Unit) {
    var kind by remember { mutableStateOf(e.kind) }
    var title by remember { mutableStateOf(e.title) }
    var body by remember { mutableStateOf(e.body) }
    var phone by remember { mutableStateOf(e.phone) }
    var url by remember { mutableStateOf(e.url) }
    var tags by remember { mutableStateOf(e.tags) }
    var pinned by remember { mutableStateOf(e.pinned) }
    var expires by remember { mutableStateOf(e.expiresAt?.let { ymd.format(Date(it)) }.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.hb_add)) },
        text = {
            LazyColumn {
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { kinds.forEach { (k, l) -> FilterChip(kind == k, { kind = k }, { Text(stringResource(l)) }) } }
                    OutlinedTextField(title, { title = it }, label = { Text(stringResource(R.string.hb_title)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(body, { body = it }, label = { Text(stringResource(R.string.hb_body)) }, minLines = 3, modifier = Modifier.fillMaxWidth())
                    if (e.section == "KNOWLEDGE") TextButton(enabled = body.isNotBlank(), onClick = { vm.polish(e.copy(title = title, body = body)) { body = it } }) {
                        Icon(Icons.Outlined.AutoAwesome, null); Spacer(Modifier.size(6.dp)); Text(stringResource(R.string.hb_polish))
                    }
                    if (kind in setOf("CONTACT", "SERVICE", "WARRANTY")) OutlinedTextField(phone, { phone = it }, label = { Text(stringResource(R.string.hb_phone)) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(url, { url = it }, label = { Text(stringResource(R.string.hb_url)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    if (kind in setOf("WARRANTY", "DOCUMENT", "SERVICE")) {
                        DateField(stringResource(R.string.hb_expiry), expires, { expires = it }, Modifier.fillMaxWidth())
                        if (expires.isNotBlank()) TextButton(onClick = { expires = "" }) { Text(stringResource(R.string.hb_no_expiry)) }
                    }
                    OutlinedTextField(tags, { tags = it }, label = { Text(stringResource(R.string.hb_tags)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(verticalAlignment = Alignment.CenterVertically) { Switch(pinned, { pinned = it }); Spacer(Modifier.size(8.dp)); Text(stringResource(R.string.hb_pinned)) }
                    Text(stringResource(R.string.hb_no_secrets), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank(), onClick = {
                onSave(e.copy(kind = kind, title = title.trim(), body = body.trim(), phone = phone.trim(), url = url.trim(), tags = tags.trim(), pinned = pinned,
                    expiresAt = expires.takeIf { it.isNotBlank() }?.let { runCatching { ymd.parse(it)!!.time }.getOrNull() }))
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            Row {
                if (e.title.isNotBlank()) TextButton(onClick = onDelete) { Text(stringResource(R.string.delete)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

// =================== Polls ===================

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PollsScreen(vm: ExtrasViewModel, viewer: Viewer, onBack: () -> Unit) {
    val polls by remember(viewer.familyId) { vm.polls(viewer.familyId) }.collectAsState(initial = emptyList())
    val votes by remember(viewer.familyId) { vm.votes(viewer.familyId) }.collectAsState(initial = emptyList())
    val members by remember(viewer.familyId) { vm.members(viewer.familyId) }.collectAsState(initial = emptyList())
    val busy by vm.busy.collectAsState()
    var creating by remember { mutableStateOf(false) }
    val names = members.associate { it.userId to it.displayName }
    ScreenScaffold(stringResource(R.string.mod_polls), R.string.help_polls, onBack, busy,
        fab = { ExtendedFloatingActionButton(onClick = { creating = true }, icon = { Icon(Icons.Outlined.Add, null) }, text = { Text(stringResource(R.string.poll_new)) }) }) { pad ->
        LazyColumn(contentPadding = pad, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (polls.isEmpty()) item { Text(stringResource(R.string.poll_none)) }
            items(polls, key = { it.id }) { p ->
                val pv = votes.filter { it.pollId == p.id && it.deletedAt == null }
                val options = p.options.split('\n')
                val closed = p.closed || (p.closesAt in 1 until System.currentTimeMillis())
                val my = pv.firstOrNull { it.userId == viewer.userId }?.choices?.split(',')?.mapNotNull { it.toIntOrNull() }.orEmpty()
                val voters = pv.filter { it.choices.isNotBlank() }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(p.question, style = MaterialTheme.typography.titleMedium)
                        Text(
                            listOfNotNull(
                                names[p.createdBy], stringResource(R.string.poll_voted, voters.size),
                                if (p.multi) stringResource(R.string.poll_multi) else null,
                                if (p.anonymous) stringResource(R.string.poll_anonymous) else null,
                                if (closed) stringResource(R.string.poll_closed_label) else if (p.closesAt > 0) stringResource(R.string.poll_until, dmy(p.closesAt)) else null,
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                        )
                        Spacer(Modifier.height(8.dp))
                        options.forEachIndexed { i, o ->
                            val who = voters.filter { i.toString() in it.choices.split(',') }
                            val frac = if (voters.isEmpty()) 0f else who.size / voters.size.toFloat()
                            Column(Modifier.fillMaxWidth().clickable(enabled = !closed) { vm.vote(p, i) }.padding(vertical = 6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text((if (i in my) "● " else "○ ") + o, Modifier.weight(1f), fontWeight = if (i in my) FontWeight.Bold else null)
                                    Text("${who.size}")
                                }
                                LinearProgressIndicator(progress = { frac }, modifier = Modifier.fillMaxWidth().padding(top = 2.dp))
                                if (!p.anonymous && who.isNotEmpty()) Text(who.mapNotNull { names[it.userId] }.joinToString(", "), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        val best = options.indices.maxByOrNull { i -> voters.count { i.toString() in it.choices.split(',') } }
                        if (!viewer.isChild && best != null && voters.isNotEmpty()) {
                            var dayPick by remember { mutableStateOf(false) }
                            Row {
                                TextButton(onClick = { vm.pollToShopping(p, options[best]) }) { Text(stringResource(R.string.poll_to_shopping)) }
                                TextButton(onClick = { dayPick = true }) { Text(stringResource(R.string.poll_to_meal)) }
                            }
                            if (dayPick) {
                                val st = androidx.compose.material3.rememberDatePickerState(initialSelectedDateMillis = System.currentTimeMillis())
                                androidx.compose.material3.DatePickerDialog(onDismissRequest = { dayPick = false },
                                    confirmButton = { TextButton(onClick = {
                                        val ms = st.selectedDateMillis ?: System.currentTimeMillis()
                                        vm.pollToMeal(p, options[best], java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString()); dayPick = false
                                    }) { Text(stringResource(R.string.ok)) } }) { androidx.compose.material3.DatePicker(st) }
                            }
                        }
                        if (p.createdBy == viewer.userId || viewer.canManage) Row {
                            if (!closed) TextButton(onClick = { vm.closePoll(p) }) { Text(stringResource(R.string.poll_close)) }
                            TextButton(onClick = { vm.deletePoll(p) }) { Text(stringResource(R.string.delete)) }
                        }
                    }
                }
            }
        }
    }
    if (creating) NewPollDialog(onDismiss = { creating = false }) { q, opts, multi, anon, until ->
        creating = false
        vm.createPoll(viewer.familyId, q, opts, multi, anon, until)
    }
}

@Composable
private fun NewPollDialog(onDismiss: () -> Unit, onCreate: (String, List<String>, Boolean, Boolean, Long) -> Unit) {
    var q by rememberSaveable { mutableStateOf("") }
    var opts by rememberSaveable { mutableStateOf("\n") }
    var multi by rememberSaveable { mutableStateOf(false) }
    var anon by rememberSaveable { mutableStateOf(false) }
    var until by rememberSaveable { mutableStateOf("") }
    val list = opts.split('\n')
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.poll_new)) },
        text = {
            LazyColumn {
                item { OutlinedTextField(q, { q = it }, label = { Text(stringResource(R.string.poll_question)) }, modifier = Modifier.fillMaxWidth()) }
                items(list.size) { i ->
                    OutlinedTextField(list[i], { v ->
                        val l = list.toMutableList(); l[i] = v.replace("\n", " ")
                        if (i == l.lastIndex && v.isNotBlank() && l.size < 10) l.add("")
                        opts = l.joinToString("\n")
                    }, label = { Text(stringResource(R.string.poll_option, i + 1)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) { Switch(multi, { multi = it }); Spacer(Modifier.size(8.dp)); Text(stringResource(R.string.poll_multi)) }
                    Row(verticalAlignment = Alignment.CenterVertically) { Switch(anon, { anon = it }); Spacer(Modifier.size(8.dp)); Text(stringResource(R.string.poll_anonymous)) }
                    DateField(stringResource(R.string.poll_closes), until, { until = it }, Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            val valid = q.isNotBlank() && list.count { it.isNotBlank() } >= 2
            TextButton(enabled = valid, onClick = {
                onCreate(q, list.filter { it.isNotBlank() }, multi, anon, until.takeIf { it.isNotBlank() }?.let { runCatching { ymd.parse(it)!!.time + 86_399_000L }.getOrNull() } ?: 0L)
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
