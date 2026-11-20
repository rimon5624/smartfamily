package com.rimon.smartfamily.ui.screens

import com.rimon.smartfamily.ui.components.SfTopAppBar
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rimon.smartfamily.R
import com.rimon.smartfamily.data.repo.StoreBasket
import com.rimon.smartfamily.smart.Chains
import com.rimon.smartfamily.ui.PriceViewModel
import com.rimon.smartfamily.ui.components.HelpButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun shekel(v: Double) = "₪" + String.format(Locale.US, "%.2f", v)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PricesScreen(vm: PriceViewModel, viewer: Viewer, onBack: () -> Unit) {
    val hebrew = LocalConfiguration.current.locales[0].language.let { it == "iw" || it == "he" }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val progress by vm.progress.collectAsState()
    val selected by vm.selected.collectAsState(initial = emptyList())
    var checking by remember { mutableStateOf(false) }
    if (checking) {
        val results by vm.checks.collectAsState()
        AlertDialog(
            onDismissRequest = { checking = false },
            title = { Text(stringResource(R.string.prices_check)) },
            text = {
                androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 460.dp)) {
                    items(com.rimon.smartfamily.smart.Chains.all.size) { i ->
                        val ch = com.rimon.smartfamily.smart.Chains.all[i]
                        val r = results[ch.key]
                        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(when { r == null -> "⏳"; r.first -> "✅"; else -> "⚠️" }, Modifier.width(28.dp))
                            Column(Modifier.weight(1f)) {
                                Text(if (hebrew) ch.he else ch.en, style = MaterialTheme.typography.bodyMedium)
                                if (r != null) Text(r.second, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { checking = false }) { Text(stringResource(R.string.close)) } },
        )
    }
    Scaffold(topBar = {
        SfTopAppBar(
            title = { Text(stringResource(R.string.mod_prices)) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) } },
            actions = {
                IconButton(enabled = progress == null && selected.isNotEmpty(), onClick = { vm.refreshAll() }) { Icon(Icons.Outlined.Refresh, stringResource(R.string.prices_refresh)) }
                IconButton(onClick = { checking = true; vm.checkSources() }) { Icon(Icons.Outlined.NetworkCheck, stringResource(R.string.prices_check)) }
                HelpButton(R.string.help_prices)
            },
        )
    }) { inner ->
        Column(Modifier.padding(inner).fillMaxSize()) {
            if (progress != null) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                if (progress!!.isNotEmpty()) Text(stringResource(R.string.prices_downloading, progress!!), Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.labelMedium)
            }
            TabRow(tab) {
                Tab(tab == 0, { tab = 0 }, text = { Text(stringResource(R.string.prices_compare)) })
                Tab(tab == 1, { tab = 1 }, text = { Text(stringResource(R.string.prices_stores, selected.size)) })
            }
            if (tab == 0) CompareTab(vm, viewer, hebrew, selected.isEmpty(), onPickStores = { tab = 1 }) else StoresTab(vm, hebrew)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompareTab(vm: PriceViewModel, viewer: Viewer, hebrew: Boolean, noStores: Boolean, onPickStores: () -> Unit) {
    val lists by remember(viewer.familyId) { vm.lists(viewer.familyId) }.collectAsState(initial = emptyList())
    val baskets by vm.baskets.collectAsState()
    var listId by rememberSaveable { mutableStateOf("") }
    var hidden by rememberSaveable { mutableStateOf("") } // chains not to show, csv
    var open by rememberSaveable { mutableStateOf("") }
    val hiddenSet = hidden.split(',').filter { it.isNotBlank() }.toSet()
    var watchLine by remember { mutableStateOf<com.rimon.smartfamily.data.repo.PricedLine?>(null) }
    val shown = baskets.filter { it.store.chainKey !in hiddenSet }
    // PRC-4: "comparable total" only over the items every shown store has, so a missing item never looks cheap.
    val common = shown.map { b -> b.lines.filter { it.match != null }.map { it.item.id }.toSet() }.reduceOrNull { a, b -> a intersect b }.orEmpty()
    fun comparable(b: StoreBasket) = b.lines.filter { it.item.id in common }.sumOf { it.total }
    val cheapest = if (common.isNotEmpty()) shown.minOfOrNull { comparable(it) } else null
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (noStores) item {
            Text(stringResource(R.string.prices_pick_first))
            FilledTonalButton(onClick = onPickStores) { Text(stringResource(R.string.prices_choose_stores)) }
        }
        item {
            Text(stringResource(R.string.prices_which_list), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                lists.forEach { l -> FilterChip(l.id == listId, { listId = l.id; vm.compare(l.id) }, { Text(l.name) }) }
            }
            if (baskets.isNotEmpty()) {
                Text(stringResource(R.string.prices_show_chains), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    baskets.map { it.store.chainKey }.distinct().forEach { k ->
                        val ch = Chains.byKey(k) ?: return@forEach
                        FilterChip(k !in hiddenSet, { hidden = (if (k in hiddenSet) hiddenSet - k else hiddenSet + k).joinToString(",") }, { Text(if (hebrew) ch.he else ch.en) })
                    }
                }
            }
        }
        items(shown, key = { it.store.chainKey + it.store.storeId }) { b ->
            BasketCard(b, hebrew, comparable(b), common.size, best = cheapest != null && comparable(b) == cheapest, expanded = open == b.store.chainKey + b.store.storeId, onLine = { watchLine = it }) {
                open = if (open == b.store.chainKey + b.store.storeId) "" else b.store.chainKey + b.store.storeId
            }
        }
        if (shown.size >= 2) item { SplitPlan(shown, hebrew) }
        if (baskets.isNotEmpty()) item { Text(stringResource(R.string.prices_disclaimer), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    watchLine?.let { l -> WatchDialog(vm, l, onDismiss = { watchLine = null }) }
}

@Composable
private fun BasketCard(b: StoreBasket, hebrew: Boolean, comparable: Double, commonCount: Int, best: Boolean, expanded: Boolean, onLine: (com.rimon.smartfamily.data.repo.PricedLine) -> Unit = {}, onToggle: () -> Unit) {
    val ch = Chains.byKey(b.store.chainKey)
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onToggle),
        colors = if (best) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else CardDefaults.cardColors(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text((if (best) "🏆 " else "") + (if (hebrew) ch?.he else ch?.en).orEmpty(), style = MaterialTheme.typography.titleMedium)
                    Text(listOf(b.store.name, b.store.city).filter { it.isNotBlank() }.distinct().joinToString(" · "), style = MaterialTheme.typography.labelMedium)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(shekel(b.total), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.prices_found, b.found, b.lines.size), style = MaterialTheme.typography.labelSmall)
                    if (commonCount > 0) Text(stringResource(R.string.prices_comparable, shekel(comparable), commonCount), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
            }
            if (expanded) {
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                b.lines.forEach { l ->
                    Row(Modifier.fillMaxWidth().clickable(enabled = l.match != null) { onLine(l) }.padding(vertical = 3.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(l.item.name + if (l.item.quantity > 1) " ×${l.item.quantity.toInt()}" else "")
                            Text(
                                l.match?.let { (if (l.exact) "✓ " else "≈ ") + it.name } ?: stringResource(R.string.prices_not_found),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (l.match == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            l.promo?.let { p -> Text("🏷 " + p.description + if (p.until.isNotBlank()) " · " + stringResource(R.string.prices_until, p.until.split('-').reversed().joinToString("/")) else "",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary) }
                        }
                        Text(if (l.match != null) shekel(l.total) else "—")
                    }
                }
                b.store.lastFetched.takeIf { it > 0 }?.let {
                    Text(stringResource(R.string.prices_as_of, SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(it))), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun StoresTab(vm: PriceViewModel, hebrew: Boolean) {
    var open by rememberSaveable { mutableStateOf("") }
    var city by rememberSaveable { mutableStateOf("") }
    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        item {
            Text(stringResource(R.string.prices_stores_intro), style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(city, { city = it }, label = { Text(stringResource(R.string.prices_city_filter)) }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
            Text(stringResource(R.string.prices_shira_note), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Chains.all.forEach { chain ->
            item(key = chain.key) {
                val stores by remember(chain.key) { vm.stores(chain.key) }.collectAsState(initial = emptyList())
                val chosen = stores.count { it.selected }
                Column {
                    Row(Modifier.fillMaxWidth().clickable {
                        open = if (open == chain.key) "" else chain.key
                        if (open == chain.key && stores.isEmpty()) vm.loadStores(chain.key)
                    }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text((if (hebrew) chain.he else chain.en) + if (chosen > 0) "  ($chosen)" else "", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        Icon(if (open == chain.key) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
                    }
                    if (open == chain.key) {
                        if (stores.isEmpty()) TextButton(onClick = { vm.loadStores(chain.key) }) { Text(stringResource(R.string.prices_load_stores)) }
                        stores.filter { city.isBlank() || it.city.contains(city, true) || it.name.contains(city, true) || it.address.contains(city, true) }.take(80).forEach { s ->
                            Row(Modifier.fillMaxWidth().clickable { vm.toggle(s) }, verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(s.selected, { vm.toggle(s) })
                                Column(Modifier.weight(1f)) {
                                    Text(s.name)
                                    Text(listOf(s.city, s.address).filter { it.isNotBlank() }.joinToString(" · ") +
                                        if (s.itemCount > 0) " · " + stringResource(R.string.prices_items, s.itemCount) else "", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}


/** PRC-5: buy each item where it is cheapest among the shown stores. Shown as an extra view. */
@Composable
private fun SplitPlan(shown: List<StoreBasket>, hebrew: Boolean) {
    var open by rememberSaveable { mutableStateOf(false) }
    val ids = shown.first().lines.map { it.item.id }
    val pick = ids.mapNotNull { id ->
        shown.mapNotNull { b -> b.lines.firstOrNull { it.item.id == id && it.match != null }?.let { b to it } }.minByOrNull { it.second.total }
    }
    val byStore = pick.groupBy { it.first }
    val splitTotal = pick.sumOf { it.second.total }
    val common = pick.map { it.second.item.id }.toSet()
    val bestSingle = shown.filter { b -> common.all { id -> b.lines.any { it.item.id == id && it.match != null } } }.minOfOrNull { b -> b.lines.filter { it.item.id in common }.sumOf { it.total } }
    if (byStore.size < 2) return
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(14.dp).clickable { open = !open }) {
            Text("✂️ " + stringResource(R.string.prices_split_title, shekel(splitTotal)) + (bestSingle?.let { " · " + stringResource(R.string.prices_split_saves, shekel(it - splitTotal)) } ?: ""), style = MaterialTheme.typography.titleSmall)
            if (open) byStore.forEach { (b, lines) ->
                val ch = Chains.byKey(b.store.chainKey)
                Text((if (hebrew) ch?.he else ch?.en).orEmpty() + " · " + b.store.name + ": " + lines.joinToString(", ") { it.second.item.name }, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun WatchDialog(vm: PriceViewModel, l: com.rimon.smartfamily.data.repo.PricedLine, onDismiss: () -> Unit) {
    val m = l.match ?: return
    var target by rememberSaveable { mutableStateOf(String.format(java.util.Locale.US, "%.2f", m.price * 0.9)) }
    val hist by androidx.compose.runtime.produceState(emptyList<com.rimon.smartfamily.data.db.PriceHistoryEntity>(), m.code) { value = vm.history(m.code) }
    androidx.compose.material3.AlertDialog(onDismissRequest = onDismiss, title = { Text(m.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.prices_now, shekel(m.price)) + if (m.unitPrice > 0) " · " + stringResource(R.string.prices_unit, shekel(m.unitPrice), m.unit) else "")
                if (hist.isNotEmpty()) {
                    Text(stringResource(R.string.prices_history), style = MaterialTheme.typography.labelLarge)
                    hist.groupBy { it.day }.toSortedMap().entries.toList().takeLast(10).forEach { (d, list) -> Text(d.split('-').reversed().take(2).joinToString("/") + "  " + shekel(list.minOf { it.price }), style = MaterialTheme.typography.bodySmall) }
                }
                NumIn(stringResource(R.string.prices_alert_below), target) { target = it }
            }
        },
        confirmButton = { TextButton(onClick = { target.toDoubleOrNull()?.let { vm.watch(m.code, m.name, it) }; onDismiss() }) { Text(stringResource(R.string.prices_watch)) } },
        dismissButton = { TextButton(onClick = { vm.unwatch(m.code); onDismiss() }) { Text(stringResource(R.string.prices_unwatch)) } })
}
