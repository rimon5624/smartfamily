package com.rimon.smartfamily.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rimon.smartfamily.R
import com.rimon.smartfamily.data.db.GrowthEntryEntity
import com.rimon.smartfamily.data.db.RoutineStepEntity
import com.rimon.smartfamily.ui.RecordsViewModel
import java.time.LocalDate

private val MORNING = listOf("🪥" to "צחצוח שיניים", "👕" to "להתלבש", "🛏️" to "לסדר מיטה", "🥣" to "ארוחת בוקר", "🎒" to "תיק מוכן", "🍎" to "אוכל לקחת", "👟" to "נעליים")
private val EVENING = listOf("🛁" to "מקלחת", "🦷" to "צחצוח שיניים", "🎒" to "לארגן תיק למחר", "👖" to "בגדים למחר", "📚" to "שיעורי בית", "📖" to "סיפור", "😴" to "לישון")
private val MORNING_EN = listOf("🪥" to "Brush teeth", "👕" to "Get dressed", "🛏️" to "Make the bed", "🥣" to "Breakfast", "🎒" to "Bag ready", "🍎" to "Packed lunch", "👟" to "Shoes on")
private val EVENING_EN = listOf("🛁" to "Shower", "🦷" to "Brush teeth", "🎒" to "Pack tomorrow's bag", "👖" to "Clothes for tomorrow", "📚" to "Homework", "📖" to "Story", "😴" to "Bed")

/** Kids hub (2.0): picture routine boards the child ticks, and a growth log. */
@Composable
fun KidsScreen(vm: RecordsViewModel, viewer: Viewer, hebrew: Boolean, onBack: () -> Unit, onPocket: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val members by remember(viewer.familyId) { vm.members(viewer.familyId) }.collectAsState(initial = emptyList())
    val steps by remember(viewer.familyId) { vm.routine(viewer.familyId) }.collectAsState(initial = emptyList())
    val checks by remember(viewer.familyId) { vm.checks(viewer.familyId) }.collectAsState(initial = emptyList())
    val growth by remember(viewer.familyId) { vm.growth(viewer.familyId) }.collectAsState(initial = emptyList())
    val kids = members.filter { it.status == "ACTIVE" && (it.role == "CHILD" || steps.any { s -> s.userId == it.userId }) }
    var child by rememberSaveable { mutableStateOf(if (viewer.isChild) viewer.userId else "") }
    val who = child.ifBlank { kids.firstOrNull()?.userId.orEmpty() }
    val hour = java.time.LocalTime.now().hour
    var part by rememberSaveable { mutableStateOf(if (hour in 4..13) "MORNING" else "EVENING") }
    val today = LocalDate.now()
    val dow = today.dayOfWeek.value % 7
    var editStep by remember { mutableStateOf<RoutineStepEntity?>(null) }
    var editGrowth by remember { mutableStateOf<GrowthEntryEntity?>(null) }
    val haptic = LocalHapticFeedback.current

    ModuleScaffold(
        stringResource(R.string.mod_kids), R.string.help_kids, onBack,
        listOf(stringResource(R.string.kids_board), stringResource(R.string.kids_growth), stringResource(R.string.money_pocket)), tab, { if (it == 2) onPocket() else tab = it },
        fabLabel = if (!viewer.isChild && who.isNotBlank()) stringResource(R.string.budget_add) else null,
        onFab = {
            if (tab == 0) editStep = RoutineStepEntity(id = vm.id(), familyId = viewer.familyId, userId = who, title = "", part = part, sort = 99)
            else editGrowth = GrowthEntryEntity(id = vm.id(), familyId = viewer.familyId, personId = who, date = today.toString())
        },
    ) {
        if (!viewer.isChild) item {
            if (kids.isEmpty()) Text(stringResource(R.string.kids_none))
            ChoiceIn(null, kids.map { it.userId to it.displayName }, who) { child = it }
        }
        if (tab == 0) {
            item { ChoiceIn(null, listOf("MORNING" to stringResource(R.string.kids_morning), "EVENING" to stringResource(R.string.kids_evening)), part) { part = it } }
            val mine = steps.filter { it.userId == who && it.part == part && (it.days shr dow) and 1 == 1 }.sortedBy { it.sort }
            val done = mine.count { s -> checks.any { it.stepId == s.id && it.date == today.toString() && it.done && it.deletedAt == null } }
            if (mine.isNotEmpty()) item {
                Text(if (done == mine.size) stringResource(R.string.kids_all_done) else stringResource(R.string.kids_progress, done, mine.size), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                LinearProgressIndicator(progress = { done / mine.size.toFloat() }, modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)))
            }
            if (mine.isEmpty() && who.isNotBlank() && !viewer.isChild) item {
                FilledTonalButton(onClick = {
                    vm.addRoutineTemplate(viewer.familyId, who, part, if (part == "MORNING") (if (hebrew) MORNING else MORNING_EN) else (if (hebrew) EVENING else EVENING_EN))
                }) { Text(stringResource(R.string.kids_use_template)) }
            }
            items(mine.chunked(2), key = { row -> row.joinToString { it.id } }) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { s ->
                        val check = checks.firstOrNull { it.stepId == s.id && it.date == today.toString() && it.deletedAt == null }
                        val on = check?.done == true
                        val bg by animateColorAsState(if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh, label = "step")
                        Box(
                            Modifier.weight(1f).height(120.dp).clip(RoundedCornerShape(20.dp)).background(bg)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    vm.toggleCheck(s, today.toString(), check)
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(if (on) "✅" else s.emoji, fontSize = 40.sp)
                                Text(s.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                if (!viewer.isChild) TextButton(onClick = { editStep = s }) { Text(stringResource(R.string.chat_edit)) }
                            }
                        }
                    }
                    if (row.size == 1) Box(Modifier.weight(1f))
                }
            }
        } else if (tab == 1) {
            val mine = growth.filter { it.personId == who }.sortedByDescending { it.date }
            if (mine.isEmpty()) item { Text(stringResource(R.string.kids_growth_empty)) }
            mine.firstOrNull()?.let { last ->
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) {
                            Text(stringResource(R.string.kids_latest), style = MaterialTheme.typography.labelLarge)
                            Text(listOfNotNull(
                                last.heightCm.takeIf { it > 0 }?.let { "📏 ${it.toInt()}" }, last.weightKg.takeIf { it > 0 }?.let { "⚖️ $it" },
                                last.shoeSize.takeIf { it.isNotBlank() }?.let { "👟 $it" }, last.clothesSize.takeIf { it.isNotBlank() }?.let { "👕 $it" },
                            ).joinToString("   "), style = MaterialTheme.typography.titleMedium)
                            val prev = mine.getOrNull(1)
                            if (prev != null && prev.heightCm > 0 && last.heightCm > prev.heightCm) Text(stringResource(R.string.kids_grew, (last.heightCm - prev.heightCm).toInt()), color = MaterialTheme.colorScheme.primary)
                            val months = runCatching { java.time.temporal.ChronoUnit.MONTHS.between(LocalDate.parse(last.date), today) }.getOrDefault(0L)
                            if (months >= 6) Text(stringResource(R.string.kids_measure_again), color = MaterialTheme.colorScheme.tertiary)
                        }
                    }
                }
            }
            items(mine, key = { it.id }) { g ->
                Row(Modifier.fillMaxWidth().clickable(enabled = !viewer.isChild) { editGrowth = g }.padding(vertical = 6.dp)) {
                    Text(g.date.split('-').reversed().joinToString("/"), Modifier.weight(1f))
                    Text(listOfNotNull(g.heightCm.takeIf { it > 0 }?.let { "${it.toInt()}cm" }, g.weightKg.takeIf { it > 0 }?.let { "${it}kg" }, g.shoeSize.takeIf { it.isNotBlank() }, g.clothesSize.takeIf { it.isNotBlank() }).joinToString(" · "))
                }
            }
        }
    }

    editStep?.let { s ->
        var title by remember { mutableStateOf(s.title) }; var emoji by remember { mutableStateOf(s.emoji) }; var days by remember { mutableIntStateOf(s.days) }
        EditDialog(stringResource(R.string.kids_board), title.isNotBlank(), { editStep = null }, { vm.save(s.copy(title = title.trim(), emoji = emoji.trim().ifBlank { "⭐" }, days = days)); editStep = null },
            if (s.title.isNotBlank()) ({ vm.delete(s); editStep = null }) else null) {
            TextIn(stringResource(R.string.kids_step), title) { title = it }
            TextIn(stringResource(R.string.kids_emoji), emoji) { emoji = it }
            val names = stringResource(R.string.cal_weekdays_short).split(',')
            Row { (0..6).forEach { d -> androidx.compose.material3.FilterChip((days shr d) and 1 == 1, { days = days xor (1 shl d) }, { Text(names[d]) }) } }
        }
    }
    editGrowth?.let { g ->
        var date by remember { mutableStateOf(g.date) }; var h by remember { mutableStateOf(if (g.heightCm > 0) g.heightCm.toString() else "") }
        var w by remember { mutableStateOf(if (g.weightKg > 0) g.weightKg.toString() else "") }; var shoe by remember { mutableStateOf(g.shoeSize) }; var clothes by remember { mutableStateOf(g.clothesSize) }
        EditDialog(stringResource(R.string.kids_growth), true, { editGrowth = null }, {
            vm.save(g.copy(date = date, heightCm = h.toDoubleOrNull() ?: 0.0, weightKg = w.toDoubleOrNull() ?: 0.0, shoeSize = shoe.trim(), clothesSize = clothes.trim())); editGrowth = null
        }, if (g.heightCm > 0 || g.shoeSize.isNotBlank()) ({ vm.delete(g); editGrowth = null }) else null) {
            DateIn(stringResource(R.string.budget_date), date) { date = it }
            NumIn(stringResource(R.string.kids_height), h) { h = it }
            NumIn(stringResource(R.string.kids_weight), w) { w = it }
            TextIn(stringResource(R.string.kids_shoe), shoe) { shoe = it }
            TextIn(stringResource(R.string.kids_clothes), clothes) { clothes = it }
        }
    }
}
