package com.rimon.smartfamily.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rimon.smartfamily.R
import com.rimon.smartfamily.data.db.GoalEntity
import com.rimon.smartfamily.data.db.PocketEntryEntity
import com.rimon.smartfamily.data.db.RecurringPaymentEntity
import com.rimon.smartfamily.data.db.SplitExpenseEntity
import com.rimon.smartfamily.ui.RecordsViewModel
import java.time.LocalDate

/** Money hub (2.0): bills & subscriptions, pocket money, savings goals, shared costs. */
@Composable
fun MoneyScreen(vm: RecordsViewModel, viewer: Viewer, startTab: Int, onBack: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(startTab) }
    val members by remember(viewer.familyId) { vm.members(viewer.familyId) }.collectAsState(initial = emptyList())
    val payments by remember(viewer.familyId) { vm.payments(viewer.familyId) }.collectAsState(initial = emptyList())
    val pocket by remember(viewer.familyId) { vm.pocket(viewer.familyId) }.collectAsState(initial = emptyList())
    val goals by remember(viewer.familyId) { vm.goals(viewer.familyId) }.collectAsState(initial = emptyList())
    val splits by remember(viewer.familyId) { vm.splits(viewer.familyId) }.collectAsState(initial = emptyList())
    val names = members.associate { it.userId to it.displayName }
    var editPay by remember { mutableStateOf<RecurringPaymentEntity?>(null) }
    var editPocket by remember { mutableStateOf<PocketEntryEntity?>(null) }
    var editGoal by remember { mutableStateOf<GoalEntity?>(null) }
    var editSplit by remember { mutableStateOf<SplitExpenseEntity?>(null) }
    var addToGoal by remember { mutableStateOf<GoalEntity?>(null) }
    val tabs = listOf(stringResource(R.string.money_bills), stringResource(R.string.money_pocket), stringResource(R.string.money_goals), stringResource(R.string.money_splits))
    val canEdit = !viewer.isChild
    val today = LocalDate.now()

    ModuleScaffold(
        stringResource(R.string.money_title), R.string.help_money, onBack, tabs, tab, { tab = it },
        fabLabel = if (canEdit || tab == 3) stringResource(R.string.budget_add) else null,
        onFab = {
            when (tab) {
                0 -> editPay = RecurringPaymentEntity(id = vm.id(), familyId = viewer.familyId, title = "", nextDate = today.toString(), createdBy = viewer.userId)
                1 -> editPocket = PocketEntryEntity(id = vm.id(), familyId = viewer.familyId, userId = members.firstOrNull { it.role == "CHILD" }?.userId ?: viewer.userId, amount = 0.0, date = System.currentTimeMillis())
                2 -> editGoal = GoalEntity(id = vm.id(), familyId = viewer.familyId, title = "")
                else -> editSplit = SplitExpenseEntity(id = vm.id(), familyId = viewer.familyId, title = "", amount = 0.0, paidBy = viewer.userId,
                    participants = members.filter { it.status == "ACTIVE" && it.role != "CHILD" }.joinToString(",") { it.userId }, date = System.currentTimeMillis())
            }
        },
    ) {
        when (tab) {
            0 -> {
                val monthly = payments.filter { it.active }.sumOf { perMonth(it) }
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.money_monthly_fixed), style = MaterialTheme.typography.labelLarge)
                            Text(shekels(monthly), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.money_yearly, shekels(monthly * 12)), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (payments.isEmpty()) item { Text(stringResource(R.string.money_bills_empty)) }
                listOf("BILL" to R.string.money_kind_bill, "SUBSCRIPTION" to R.string.money_kind_sub).forEach { (kind, label) ->
                    val list = payments.filter { it.kind == kind }.sortedBy { it.nextDate }
                    if (list.isNotEmpty()) item { Text(stringResource(label), style = MaterialTheme.typography.titleMedium) }
                    items(list, key = { it.id }) { p ->
                        val next = runCatching { LocalDate.parse(p.nextDate) }.getOrNull()
                        val soon = next != null && !next.isAfter(today.plusDays(7))
                        Card(onClick = { if (canEdit) editPay = p }, colors = if (soon) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer) else CardDefaults.cardColors(), modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(p.title + if (!p.active) " (" + stringResource(R.string.money_paused) + ")" else "", style = MaterialTheme.typography.titleSmall)
                                    Text(listOfNotNull(next?.let { stringResource(R.string.money_next, "${it.dayOfMonth}/${it.monthValue}") }, periodLabel(p.period)).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                                }
                                Text(shekels(p.amount), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            1 -> {
                val kids = members.filter { it.status == "ACTIVE" && (it.role == "CHILD" || pocket.any { p -> p.userId == it.userId }) }
                    .filter { !viewer.isChild || it.userId == viewer.userId }
                if (kids.isEmpty()) item { Text(stringResource(R.string.money_pocket_empty)) }
                items(kids, key = { it.userId }) { k ->
                    val mine = pocket.filter { it.userId == k.userId }.sortedByDescending { it.date }
                    val balance = mine.sumOf { it.amount }
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(k.displayName, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                                Text(shekels(balance), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                                    color = if (balance < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                            }
                            if (canEdit) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton(onClick = { editPocket = PocketEntryEntity(id = vm.id(), familyId = viewer.familyId, userId = k.userId, amount = 0.0, date = System.currentTimeMillis(), kind = "ALLOWANCE") }) { Text(stringResource(R.string.money_deposit)) }
                                OutlinedButton(onClick = { editPocket = PocketEntryEntity(id = vm.id(), familyId = viewer.familyId, userId = k.userId, amount = -0.0, date = System.currentTimeMillis()) }) { Text(stringResource(R.string.money_spend)) }
                            }
                            mine.take(6).forEach { e ->
                                Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                                    Text(e.note.ifBlank { stringResource(if (e.amount >= 0) R.string.money_deposit else R.string.money_spend) } + " · " +
                                        java.text.SimpleDateFormat("dd/MM", java.util.Locale.getDefault()).format(java.util.Date(e.date)), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                    Text((if (e.amount >= 0) "+" else "") + shekels(e.amount), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
            2 -> {
                if (goals.isEmpty()) item { Text(stringResource(R.string.money_goals_empty)) }
                items(goals.filter { !viewer.isChild || it.ownerId.isBlank() || it.ownerId == viewer.userId }, key = { it.id }) { g ->
                    val frac = if (g.target > 0) (g.saved / g.target).toFloat().coerceIn(0f, 1f) else 0f
                    Card(onClick = { if (canEdit) editGoal = g }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) {
                            Row { Text((if (g.done) "🏆 " else "🎯 ") + g.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium); Text("${(frac * 100).toInt()}%") }
                            Text(listOfNotNull(names[g.ownerId], shekels(g.saved) + " / " + shekels(g.target), g.reward.takeIf { it.isNotBlank() }?.let { "🎁 $it" }).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                            LinearProgressIndicator(progress = { frac }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
                            if (canEdit && !g.done) TextButton(onClick = { addToGoal = g }) { Text(stringResource(R.string.money_add_to_goal)) }
                        }
                    }
                }
            }
            else -> {
                val open = splits.filter { !it.settled }
                // who owes whom: everyone pays an equal share of each open expense
                val net = HashMap<String, Double>()
                open.forEach { s ->
                    val who = s.participants.split(',').filter { it.isNotBlank() }.ifEmpty { listOf(s.paidBy) }
                    val share = s.amount / who.size
                    who.forEach { u -> net[u] = (net[u] ?: 0.0) - share }
                    net[s.paidBy] = (net[s.paidBy] ?: 0.0) + s.amount
                }
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) {
                            Text(stringResource(R.string.money_balances), style = MaterialTheme.typography.titleSmall)
                            if (net.values.all { kotlin.math.abs(it) < 0.5 }) Text(stringResource(R.string.money_all_even))
                            net.filter { kotlin.math.abs(it.value) >= 0.5 }.forEach { (u, v) ->
                                Text((names[u] ?: "?") + ": " + (if (v > 0) stringResource(R.string.money_gets, shekels(v)) else stringResource(R.string.money_owes, shekels(-v))))
                            }
                        }
                    }
                }
                items(splits.sortedWith(compareBy({ it.settled }, { -it.date })), key = { it.id }) { s ->
                    Card(onClick = { editSplit = s }, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text((if (s.kind == "GIFT") "🎁 " else "") + s.title + if (s.settled) " ✓" else "", style = MaterialTheme.typography.titleSmall)
                                Text(stringResource(R.string.money_paid_by, names[s.paidBy] ?: "?") + " · " + stringResource(R.string.money_split_n, s.participants.split(',').count { it.isNotBlank() }), style = MaterialTheme.typography.bodySmall)
                            }
                            Text(shekels(s.amount), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    editPay?.let { p ->
        var kind by remember { mutableStateOf(p.kind) }; var title by remember { mutableStateOf(p.title) }
        var amount by remember { mutableStateOf(if (p.amount == 0.0) "" else p.amount.toString()) }; var period by remember { mutableStateOf(p.period) }
        var next by remember { mutableStateOf(p.nextDate) }; var note by remember { mutableStateOf(p.note) }; var active by remember { mutableStateOf(p.active) }
        EditDialog(stringResource(R.string.money_bills), title.isNotBlank(), { editPay = null }, {
            vm.save(p.copy(kind = kind, title = title.trim(), amount = amount.toDoubleOrNull() ?: 0.0, period = period, nextDate = next, note = note.trim(), active = active)); editPay = null
        }, if (p.title.isNotBlank()) ({ vm.delete(p); editPay = null }) else null) {
            ChoiceIn(null, listOf("BILL" to stringResource(R.string.money_kind_bill), "SUBSCRIPTION" to stringResource(R.string.money_kind_sub)), kind) { kind = it }
            TextIn(stringResource(R.string.money_what), title) { title = it }
            NumIn("₪", amount) { amount = it }
            ChoiceIn(stringResource(R.string.money_period), listOf("MONTH" to stringResource(R.string.money_p_month), "BIMONTH" to stringResource(R.string.money_p_bimonth), "YEAR" to stringResource(R.string.money_p_year)), period) { period = it }
            DateIn(stringResource(R.string.money_next_date), next) { next = it }
            TextIn(stringResource(R.string.budget_note), note) { note = it }
            SwitchLine(stringResource(R.string.money_active), active) { active = it }
        }
    }
    editPocket?.let { e ->
        val spend = e.amount < 0 || (1.0 / e.amount) < 0
        var amount by remember { mutableStateOf("") }; var note by remember { mutableStateOf(e.note) }
        EditDialog(stringResource(if (spend) R.string.money_spend else R.string.money_deposit) + " · " + (names[e.userId] ?: ""), (amount.toDoubleOrNull() ?: 0.0) > 0, { editPocket = null }, {
            val a = amount.toDoubleOrNull() ?: 0.0
            vm.save(e.copy(amount = if (spend) -a else a, note = note.trim())); editPocket = null
        }) {
            NumIn("₪", amount) { amount = it }
            TextIn(stringResource(R.string.budget_note), note) { note = it }
        }
    }
    editGoal?.let { g ->
        var title by remember { mutableStateOf(g.title) }; var target by remember { mutableStateOf(if (g.target == 0.0) "" else g.target.toLong().toString()) }
        var owner by remember { mutableStateOf(g.ownerId) }; var reward by remember { mutableStateOf(g.reward) }
        EditDialog(stringResource(R.string.money_goals), title.isNotBlank() && (target.toDoubleOrNull() ?: 0.0) > 0, { editGoal = null }, {
            vm.save(g.copy(title = title.trim(), target = target.toDoubleOrNull() ?: 0.0, ownerId = owner, reward = reward.trim())); editGoal = null
        }, if (g.title.isNotBlank()) ({ vm.delete(g); editGoal = null }) else null) {
            TextIn(stringResource(R.string.money_goal_what), title) { title = it }
            NumIn(stringResource(R.string.money_goal_target), target) { target = it }
            ChoiceIn(stringResource(R.string.money_goal_for), listOf("" to stringResource(R.string.money_goal_family)) + members.filter { it.status == "ACTIVE" }.map { it.userId to it.displayName }, owner) { owner = it }
            TextIn(stringResource(R.string.money_goal_reward), reward) { reward = it }
        }
    }
    addToGoal?.let { g ->
        var amount by remember { mutableStateOf("") }
        EditDialog(g.title, (amount.toDoubleOrNull() ?: 0.0) != 0.0, { addToGoal = null }, {
            val saved = g.saved + (amount.toDoubleOrNull() ?: 0.0)
            vm.save(g.copy(saved = saved, done = saved >= g.target)); addToGoal = null
        }) { NumIn("₪", amount) { amount = it } }
    }
    editSplit?.let { s ->
        var title by remember { mutableStateOf(s.title) }; var amount by remember { mutableStateOf(if (s.amount == 0.0) "" else s.amount.toString()) }
        var paidBy by remember { mutableStateOf(s.paidBy) }; var who by remember { mutableStateOf(s.participants.split(',').filter { it.isNotBlank() }.toSet()) }
        var kind by remember { mutableStateOf(s.kind) }; var settled by remember { mutableStateOf(s.settled) }
        EditDialog(stringResource(R.string.money_splits), title.isNotBlank() && (amount.toDoubleOrNull() ?: 0.0) > 0 && who.isNotEmpty(), { editSplit = null }, {
            vm.save(s.copy(title = title.trim(), amount = amount.toDoubleOrNull() ?: 0.0, paidBy = paidBy, participants = who.joinToString(","), kind = kind, settled = settled)); editSplit = null
        }, if (s.title.isNotBlank()) ({ vm.delete(s); editSplit = null }) else null) {
            ChoiceIn(null, listOf("EXPENSE" to stringResource(R.string.money_split_expense), "GIFT" to stringResource(R.string.money_split_gift)), kind) { kind = it }
            TextIn(stringResource(R.string.money_what), title) { title = it }
            NumIn("₪", amount) { amount = it }
            MemberIn(stringResource(R.string.money_paid_by_q), members, paidBy) { paidBy = it }
            Text(stringResource(R.string.money_split_between), style = MaterialTheme.typography.labelLarge)
            members.filter { it.status == "ACTIVE" }.forEach { m ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(m.userId in who, { on -> who = if (on) who + m.userId else who - m.userId })
                    Text(m.displayName)
                }
            }
            SwitchLine(stringResource(R.string.money_settled), settled) { settled = it }
        }
    }
}

private fun perMonth(p: RecurringPaymentEntity) = when (p.period) { "YEAR" -> p.amount / 12; "BIMONTH" -> p.amount / 2; else -> p.amount }

@Composable
private fun periodLabel(p: String) = stringResource(when (p) { "YEAR" -> R.string.money_p_year; "BIMONTH" -> R.string.money_p_bimonth; else -> R.string.money_p_month })
