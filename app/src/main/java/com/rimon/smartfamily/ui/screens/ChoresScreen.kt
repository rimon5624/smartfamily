package com.rimon.smartfamily.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.rimon.smartfamily.R
import com.rimon.smartfamily.data.db.ChoreEntity
import com.rimon.smartfamily.data.db.MemberEntity
import com.rimon.smartfamily.data.repo.Schedule
import com.rimon.smartfamily.model.Role
import com.rimon.smartfamily.smart.ShabbatTimes
import com.rimon.smartfamily.ui.HomeLifeViewModel
import com.rimon.smartfamily.ui.components.Avatar
import com.rimon.smartfamily.ui.components.HelpButton
import com.rimon.smartfamily.ui.components.SectionTitle
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChoresScreen(vm: HomeLifeViewModel, viewer: Viewer, cityKey: String, onBack: () -> Unit) {
    val chores by remember(viewer.familyId) { vm.chores(viewer.familyId) }.collectAsState(initial = emptyList())
    val members by remember(viewer.familyId) { vm.members(viewer.familyId) }.collectAsState(initial = emptyList())
    val pending by remember(viewer.familyId) { vm.pending(viewer.familyId) }.collectAsState(initial = emptyList())
    val weekStart = remember { System.currentTimeMillis() - 7 * 86_400_000L }
    val recent by remember(viewer.familyId) { vm.completionsSince(viewer.familyId, weekStart) }.collectAsState(initial = emptyList())
    val shabbat by produceState<ShabbatTimes?>(null, cityKey) { value = runCatching { vm.shabbat(cityKey) }.getOrNull() }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<ChoreEntity?>(null) }
    var templates by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = System.currentTimeMillis() } }
    val names = members.associate { it.userId to it.displayName }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.chores_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) } },
                actions = { HelpButton(R.string.help_chores) },
            )
        },
        floatingActionButton = {
            if (!viewer.isChild && tab == 0) {
                ExtendedFloatingActionButton(
                    onClick = { editing = vm.newChoreDraft(viewer.familyId, viewer.userId) },
                    icon = { Icon(Icons.Outlined.Add, null) },
                    text = { Text(stringResource(R.string.chores_add)) },
                )
            }
        },
    ) { inner ->
        Column(Modifier.padding(top = inner.calculateTopPadding())) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.chores_title)) })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.chores_points_tab)) })
            }
            if (tab == 0) {
                val due = chores.filter { it.nextDue in 1..now }
                val upcoming = chores.filter { it.nextDue > now }
                LazyColumn(contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { ShabbatCard(shabbat, now, chores.filter { it.schedule == Schedule.BEFORE_SHABBAT.name }) }
                    if (viewer.canManage && pending.isNotEmpty()) {
                        item { SectionTitle(stringResource(R.string.chores_approvals)) }
                        items(pending, key = { it.id }) { p ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Avatar(names[p.doneBy] ?: "?", 32.dp)
                                Spacer(Modifier.size(10.dp))
                                Text("${p.title} · ${p.points}", Modifier.weight(1f))
                                TextButton(onClick = { vm.approve(p.id) }) { Text(stringResource(R.string.chores_approve)) }
                                TextButton(onClick = { vm.reject(p.id) }) { Text(stringResource(R.string.chores_reject)) }
                            }
                        }
                    }
                    if (chores.isEmpty()) {
                        item {
                            Column(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(stringResource(R.string.chores_none), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(12.dp))
                                if (!viewer.isChild) {
                                    FilledTonalButton(onClick = { templates = true }) {
                                        Icon(Icons.Outlined.AutoAwesome, null); Spacer(Modifier.size(8.dp)); Text(stringResource(R.string.chores_templates))
                                    }
                                }
                            }
                        }
                    }
                    if (due.isNotEmpty()) {
                        item { SectionTitle(stringResource(R.string.chores_today)) }
                        items(due, key = { it.id }) { ch -> ChoreRow(ch, now, names, viewer, vm, onEdit = { editing = ch }) }
                    }
                    if (upcoming.isNotEmpty()) {
                        item { SectionTitle(stringResource(R.string.chores_upcoming)) }
                        items(upcoming, key = { it.id }) { ch -> ChoreRow(ch, now, names, viewer, vm, onEdit = { editing = ch }) }
                    }
                    if (recent.isNotEmpty()) {
                        item { SectionTitle(stringResource(R.string.chores_balance)) }
                        // CHR-2 fair share counts minutes, not just tasks; CHR-7 streaks keep kids going
                        val minutesOf = chores.associate { it.id to it.estimatedMinutes }
                        val byWho = recent.filter { it.approved }.groupBy { it.doneBy }
                        val total = byWho.values.sumOf { l -> l.sumOf { minutesOf[it.choreId] ?: 15 } }.coerceAtLeast(1)
                        items(byWho.entries.sortedByDescending { e -> e.value.sumOf { minutesOf[it.choreId] ?: 15 } }.toList(), key = { "bal-" + it.key }) { (who, list) ->
                            val mins = list.sumOf { minutesOf[it.choreId] ?: 15 }
                            val days = list.map { com.rimon.smartfamily.smart.JewishDays.toLocal(it.doneAt) }.toSet()
                            var streak = 0
                            var d = java.time.LocalDate.now(com.rimon.smartfamily.smart.JewishDays.ZONE)
                            if (d !in days) d = d.minusDays(1)
                            while (d in days) { streak++; d = d.minusDays(1) }
                            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Row {
                                    Text(stringResource(R.string.chores_balance_line, names[who] ?: "?", list.size), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                    Text(stringResource(R.string.chores_minutes, mins) + if (streak >= 2) "  🔥$streak" else "", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                }
                                LinearProgressIndicator(progress = { mins / total.toFloat() }, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                    if (chores.isNotEmpty() && !viewer.isChild) {
                        item { TextButton(onClick = { templates = true }) { Text(stringResource(R.string.chores_templates)) } }
                    }
                }
            } else {
                PointsTab(vm, viewer, members)
            }
        }
    }

    editing?.let { ch ->
        ChoreEditDialog(
            chore = ch, members = members,
            onDismiss = { editing = null },
            onSave = { updated, changed -> vm.saveChore(updated, changed); editing = null },
            onDelete = { vm.deleteChore(ch.id); editing = null },
        )
    }
    if (templates) {
        TemplatesDialog(onDismiss = { templates = false }) { chosen -> vm.addTemplates(viewer.familyId, chosen); templates = false }
    }
}

private fun countdown(ms: Long): Pair<Int, Int> {
    val totalMin = (ms / 60_000).toInt().coerceAtLeast(0)
    return totalMin / 60 to totalMin % 60
}

@Composable
fun ShabbatCard(s: ShabbatTimes?, now: Long, prep: List<ChoreEntity>) {
    val cs = MaterialTheme.colorScheme
    val time = DateFormat.getTimeInstance(DateFormat.SHORT)
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(cs.secondaryContainer, cs.tertiaryContainer)))
            .padding(18.dp)
    ) {
        if (s == null) {
            Text(stringResource(R.string.shabbat_title), style = MaterialTheme.typography.titleMedium)
            return@Box
        }
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.WbTwilight, null)
                Spacer(Modifier.size(8.dp))
                Text(
                    listOf(s.holiday, s.parasha).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { stringResource(R.string.shabbat_title) },
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.shabbat_candles, time.format(s.candleLighting)), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.shabbat_havdalah, time.format(s.havdalah)), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(6.dp))
            val left = s.candleLighting.time - now
            if (left > 0) {
                val (h, m) = countdown(left)
                val label = if (h >= 24) stringResource(R.string.days_hours, h / 24, h % 24) else stringResource(R.string.hours_minutes, h, m)
                Text(stringResource(R.string.shabbat_countdown, label), style = MaterialTheme.typography.titleLarge)
            } else {
                Text(stringResource(R.string.shabbat_now), style = MaterialTheme.typography.titleLarge)
            }
            if (prep.isNotEmpty() && left > 0) {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.shabbat_tasks), style = MaterialTheme.typography.labelLarge)
                prep.sortedBy { it.nextDue }.take(6).forEach { ch ->
                    val done = ch.lastDoneAt?.let { it > s.candleLighting.time - 7 * 86_400_000L + 86_400_000L } == true
                    Text((if (done) "✓ " else "• ") + ch.title + " · " + time.format(Date(ch.nextDue)), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun ChoreRow(ch: ChoreEntity, now: Long, names: Map<String, String>, viewer: Viewer, vm: HomeLifeViewModel, onEdit: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val overdue = ch.nextDue in 1..now
    val f = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
    Card(colors = CardDefaults.cardColors(containerColor = if (overdue) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(ch.title, style = MaterialTheme.typography.titleSmall)
                val whenText = if (overdue) stringResource(R.string.chores_overdue, f.format(Date(ch.nextDue))) else f.format(Date(ch.nextDue))
                val who = names[ch.assignee] ?: if (ch.assignee.isEmpty()) stringResource(R.string.chores_anyone) else "?"
                Text("$whenText · $who" + if (ch.points > 0) " · ⭐${ch.points}" else "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ch.lastDoneBy?.let { b -> Text(stringResource(R.string.chores_done_by, names[b] ?: "?"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            FilledTonalButton(onClick = {
                // A parent ticking a managed child's chore credits the child directly.
                val doer = if (!viewer.isChild && ch.assignee.startsWith("child-")) ch.assignee else viewer.userId
                vm.complete(ch, doer, needsApproval = viewer.isChild)
            }) {
                Icon(Icons.Outlined.Check, null, Modifier.size(18.dp)); Spacer(Modifier.size(4.dp)); Text(stringResource(R.string.chores_done))
            }
            if (!viewer.isChild) {
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, null) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.chores_edit)) }, onClick = { menu = false; onEdit() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.chores_snooze)) }, onClick = { menu = false; vm.snooze(ch.id) })
                        DropdownMenuItem(text = { Text(stringResource(R.string.chores_delete), color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; vm.deleteChore(ch.id) })
                    }
                }
            }
        }
    }
}

private val weekdayRes = listOf(R.string.wd_sun, R.string.wd_mon, R.string.wd_tue, R.string.wd_wed, R.string.wd_thu, R.string.wd_fri, R.string.wd_sat)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoreEditDialog(chore: ChoreEntity, members: List<MemberEntity>, onDismiss: () -> Unit, onSave: (ChoreEntity, Boolean) -> Unit, onDelete: () -> Unit) {
    var title by remember { mutableStateOf(chore.title) }
    var schedule by remember { mutableStateOf(chore.schedule) }
    var weekdays by remember { mutableIntStateOf(chore.weekdays) }
    var interval by remember { mutableStateOf(chore.intervalDays.toString()) }
    var hoursBefore by remember { mutableStateOf(chore.hoursBeforeShabbat.toString()) }
    var time by remember { mutableStateOf("%02d:%02d".format(chore.timeOfDay / 60, chore.timeOfDay % 60)) }
    var assignee by remember { mutableStateOf(chore.assignee) }
    var rotation by remember { mutableStateOf(chore.rotation.split(',').filter { it.isNotBlank() }.toSet()) }
    var points by remember { mutableStateOf(chore.points.toString()) }
    var minutes by remember { mutableStateOf(chore.estimatedMinutes.toString()) }
    var checklist by remember { mutableStateOf(chore.checklist) }
    val active = members.filter { it.status == "ACTIVE" && Role.parse(it.role) != Role.GUEST }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (chore.title.isEmpty()) R.string.chores_add else R.string.chores_edit)) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { OutlinedTextField(title, { title = it }, label = { Text(stringResource(R.string.chores_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                item {
                    Text(stringResource(R.string.chores_schedule), style = MaterialTheme.typography.labelLarge)
                    ChipRow(
                        listOf(
                            Schedule.DAILY.name to R.string.sch_daily, Schedule.WEEKLY.name to R.string.sch_weekly,
                            Schedule.EVERY_N_DAYS.name to R.string.sch_every_n, Schedule.BEFORE_SHABBAT.name to R.string.sch_shabbat,
                            Schedule.ONCE.name to R.string.sch_once,
                        ),
                        schedule,
                    ) { schedule = it }
                }
                if (schedule == Schedule.WEEKLY.name) {
                    item {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            weekdayRes.forEachIndexed { i, res ->
                                val bit = 1 shl i
                                FilterChip(selected = weekdays and bit != 0, onClick = { weekdays = weekdays xor bit }, label = { Text(stringResource(res)) })
                            }
                        }
                    }
                }
                if (schedule == Schedule.EVERY_N_DAYS.name) {
                    item { NumberField(stringResource(R.string.chores_every_days), interval) { interval = it } }
                }
                if (schedule == Schedule.BEFORE_SHABBAT.name) {
                    item { NumberField(stringResource(R.string.chores_hours_before), hoursBefore) { hoursBefore = it } }
                } else {
                    item { OutlinedTextField(time, { time = it }, label = { Text(stringResource(R.string.chores_time)) }, singleLine = true, placeholder = { Text("18:00") }, modifier = Modifier.fillMaxWidth()) }
                }
                item {
                    Text(stringResource(R.string.chores_assignee), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = assignee.isEmpty(), onClick = { assignee = "" }, label = { Text(stringResource(R.string.chores_anyone)) })
                        active.forEach { m -> FilterChip(selected = assignee == m.userId, onClick = { assignee = m.userId }, label = { Text(m.displayName) }) }
                    }
                }
                if (active.size > 1) {
                    item {
                        Text(stringResource(R.string.chores_rotation), style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            active.forEach { m ->
                                FilterChip(
                                    selected = m.userId in rotation,
                                    onClick = { rotation = if (m.userId in rotation) rotation - m.userId else rotation + m.userId },
                                    label = { Text(m.displayName) },
                                )
                            }
                        }
                    }
                }
                item { NumberField(stringResource(R.string.chores_points), points) { points = it } }
                item { NumberField(stringResource(R.string.chores_minutes), minutes) { minutes = it } }
                item { OutlinedTextField(checklist, { checklist = it }, label = { Text(stringResource(R.string.chores_checklist)) }, modifier = Modifier.fillMaxWidth()) }
                if (chore.title.isNotEmpty()) {
                    item { TextButton(onClick = onDelete) { Text(stringResource(R.string.chores_delete), color = MaterialTheme.colorScheme.error) } }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank(), onClick = {
                val minutesOfDay = time.split(':').let { p ->
                    val h = p.getOrNull(0)?.trim()?.toIntOrNull() ?: 18
                    val m = p.getOrNull(1)?.trim()?.toIntOrNull() ?: 0
                    (h.coerceIn(0, 23) * 60 + m.coerceIn(0, 59))
                }
                val rot = rotation.joinToString(",")
                val updated = chore.copy(
                    title = title.trim(), schedule = schedule, weekdays = weekdays,
                    intervalDays = interval.toIntOrNull()?.coerceAtLeast(1) ?: 7,
                    hoursBeforeShabbat = hoursBefore.toIntOrNull()?.coerceIn(0, 48) ?: 0,
                    timeOfDay = minutesOfDay, assignee = if (rotation.size > 1 && assignee.isEmpty()) rotation.first() else assignee,
                    rotation = rot, points = points.toIntOrNull()?.coerceAtLeast(0) ?: 0,
                    estimatedMinutes = minutes.toIntOrNull()?.coerceAtLeast(0) ?: 15, checklist = checklist,
                )
                val changed = chore.title.isEmpty() || updated.schedule != chore.schedule || updated.weekdays != chore.weekdays ||
                    updated.intervalDays != chore.intervalDays || updated.hoursBeforeShabbat != chore.hoursBeforeShabbat || updated.timeOfDay != chore.timeOfDay
                onSave(updated, changed)
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun NumberField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value, onChange, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun TemplatesDialog(onDismiss: () -> Unit, onAdd: (List<Pair<com.rimon.smartfamily.data.repo.ChoreTemplate, String>>) -> Unit) {
    val all = HomeLifeViewModel.templates
    val titles = all.map { stringResource(it.second) }
    var chosen by remember { mutableStateOf(all.indices.toSet()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chores_templates)) },
        text = {
            LazyColumn {
                item { Text(stringResource(R.string.chores_templates_body), style = MaterialTheme.typography.bodyMedium) }
                items(all.indices.toList()) { i ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = i in chosen, onCheckedChange = { chosen = if (it) chosen + i else chosen - i })
                        Text(titles[i])
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = chosen.isNotEmpty(), onClick = { onAdd(chosen.sorted().map { all[it].first to titles[it] }) }) { Text(stringResource(R.string.shop_add)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun PointsTab(vm: HomeLifeViewModel, viewer: Viewer, members: List<MemberEntity>) {
    val balances by remember(viewer.familyId) { vm.balances(viewer.familyId) }.collectAsState(initial = emptyList())
    val rewards by remember(viewer.familyId) { vm.rewards(viewer.familyId) }.collectAsState(initial = emptyList())
    var adding by remember { mutableStateOf(false) }
    var redeeming by remember { mutableStateOf<com.rimon.smartfamily.data.db.RewardEntity?>(null) }
    val kids = members.filter { Role.parse(it.role) == Role.CHILD }
    val people = if (kids.isNotEmpty()) kids else members
    val pointsOf = { uid: String -> balances.firstOrNull { it.userId == uid }?.total ?: 0 }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(people, key = { it.id }) { m ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(m.displayName)
                    Spacer(Modifier.size(12.dp))
                    Text(m.displayName, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Icon(Icons.Outlined.EmojiEvents, null, tint = MaterialTheme.colorScheme.tertiary)
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.chores_points_of, pointsOf(m.userId)), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        item { SectionTitle(stringResource(R.string.chores_rewards)) }
        items(rewards, key = { it.id }) { r ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${r.title} · ${r.cost}", Modifier.weight(1f))
                if (!viewer.isChild) {
                    TextButton(onClick = { redeeming = r }) { Text(stringResource(R.string.chores_redeem)) }
                    TextButton(onClick = { vm.deleteReward(r.id) }) { Text(stringResource(R.string.delete)) }
                }
            }
        }
        if (!viewer.isChild) {
            item { OutlinedButton(onClick = { adding = true }) { Text(stringResource(R.string.chores_new_reward)) } }
        }
    }
    if (adding) {
        var title by remember { mutableStateOf("") }
        var cost by remember { mutableStateOf("20") }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text(stringResource(R.string.chores_new_reward)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(title, { title = it }, label = { Text(stringResource(R.string.chores_reward_name)) }, singleLine = true)
                    NumberField(stringResource(R.string.chores_reward_cost), cost) { cost = it }
                }
            },
            confirmButton = {
                TextButton(enabled = title.isNotBlank(), onClick = { vm.saveReward(viewer.familyId, title.trim(), cost.toIntOrNull() ?: 10); adding = false }) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    redeeming?.let { r ->
        AlertDialog(
            onDismissRequest = { redeeming = null },
            title = { Text(stringResource(R.string.chores_redeem_for)) },
            text = {
                Column {
                    people.forEach { m ->
                        val enough = pointsOf(m.userId) >= r.cost
                        TextButton(enabled = enough, onClick = { vm.redeem(viewer.familyId, m.userId, r); redeeming = null }) {
                            Text(m.displayName + if (enough) "" else " · " + stringResource(R.string.chores_not_enough))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { redeeming = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** The start of today in Israel, for comparisons. */
fun startOfDay(ms: Long = System.currentTimeMillis()): Long = Calendar.getInstance().apply {
    timeInMillis = ms; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis
