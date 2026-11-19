package com.rimon.smartfamily.ui.screens

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
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.mod_prices)) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) } },
            actions = {
                IconButton(enabled = progress == null && selected.isNotEmpty(), onClick = { vm.refreshAll() }) { Icon(Icons.Outlined.Refresh, stringResource(R.string.prices_refresh)) }
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
    val shown = baskets.filter { it.store.chainKey !in hiddenSet }
    val cheapest = shown.filter { it.found == shown.maxOfOrNull { b -> b.found } }.minOfOrNull { it.total }
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
            BasketCard(b, hebrew, best = cheapest != null && b.total == cheapest && b.found > 0, expanded = open == b.store.chainKey + b.store.storeId) {
                open = if (open == b.store.chainKey + b.store.storeId) "" else b.store.chainKey + b.store.storeId
            }
        }
        if (baskets.isNotEmpty()) item { Text(stringResource(R.string.prices_disclaimer), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun BasketCard(b: StoreBasket, hebrew: Boolean, best: Boolean, expanded: Boolean, onToggle: () -> Unit) {
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
                }
                Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
            }
            if (expanded) {
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                b.lines.forEach { l ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(l.item.name + if (l.item.quantity > 1) " ×${l.item.quantity.toInt()}" else "")
                            Text(
                                l.match?.let { (if (l.exact) "✓ " else "≈ ") + it.name } ?: stringResource(R.string.prices_not_found),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (l.match == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
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
