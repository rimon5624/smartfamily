package com.rimon.smartfamily.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Diversity3
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rimon.smartfamily.R
import com.rimon.smartfamily.model.Hub
import com.rimon.smartfamily.model.HubSection
import com.rimon.smartfamily.model.NavItems
import com.rimon.smartfamily.ui.components.ChatTopIcon
import com.rimon.smartfamily.ui.components.HelpButton
import com.rimon.smartfamily.ui.components.LocalEmbedded
import com.rimon.smartfamily.ui.components.LocalFixedTab

/** Icon and label of each bottom-bar item. */
fun navIcon(item: String): ImageVector = when (item) {
    NavItems.TODAY -> Icons.Outlined.WbSunny; "calendar" -> Icons.Outlined.CalendarMonth; "food" -> Icons.Outlined.Restaurant
    "tasks" -> Icons.Outlined.TaskAlt; "money" -> Icons.Outlined.Savings; "home" -> Icons.Outlined.Home; "family" -> Icons.Outlined.Diversity3
    NavItems.CHAT -> Icons.Outlined.ChatBubbleOutline; "families" -> Icons.Outlined.Groups; else -> Icons.Outlined.Settings
}

fun navLabel(item: String): Int = when (item) {
    NavItems.TODAY -> R.string.nav_today; NavItems.CHAT -> R.string.mod_chat; "families" -> R.string.nav_families; "settings" -> R.string.nav_settings
    else -> Hub.byKey(item)?.short ?: R.string.nav_more
}

/**
 * An area: its title, search, quick add and chat at the top, its sections as tabs, and the chosen section below.
 * Sections are the existing screens shown "embedded" (no second title bar, no duplicate tabs).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HubScreen(
    hub: Hub, sections: List<HubSection>, selectedKey: String?, padding: PaddingValues,
    onSelect: (String) -> Unit, onSearch: () -> Unit, onQuickAdd: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    empty: @Composable () -> Unit = { Text(stringResource(R.string.hub_empty)) },
    content: @Composable (HubSection) -> Unit,
) {
    val sel = sections.firstOrNull { it.key == selectedKey } ?: sections.firstOrNull()
    val help = androidx.compose.runtime.remember(sel?.key) { androidx.compose.runtime.mutableStateOf<Int?>(null) }
    Scaffold(
        modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(stringResource(hub.title), Modifier.testTag("area_${hub.key}")) },
                    actions = {
                        actions()
                        IconButton(onClick = onSearch) { Icon(Icons.Outlined.Search, stringResource(R.string.search)) }
                        IconButton(onClick = onQuickAdd) { Icon(Icons.Outlined.Add, stringResource(R.string.sc_quickadd)) }
                        ChatTopIcon()
                        help.value?.let { HelpButton(it) }
                    },
                )
                if (sections.size > 1 && sel != null) {
                    ScrollableTabRow(selectedTabIndex = sections.indexOf(sel), edgePadding = 12.dp) {
                        sections.forEach { s -> Tab(selected = s == sel, onClick = { onSelect(s.key) }, modifier = Modifier.testTag("sec_${s.key}"), text = { Text(stringResource(s.title), maxLines = 1) }) }
                    }
                }
            }
        },
    ) { inner ->
        Box(Modifier.padding(inner).consumeWindowInsets(inner).fillMaxSize()) {
            if (sel == null) {
                androidx.compose.foundation.lazy.LazyColumn(contentPadding = PaddingValues(16.dp)) { item { empty() } }
            } else key(sel.key) {
                CompositionLocalProvider(LocalEmbedded provides true, LocalFixedTab provides sel.tab,
                    com.rimon.smartfamily.ui.components.LocalHelpSink provides help) { content(sel) }
            }
        }
    }
}

/** "More": everything that did not fit in the bar, plus families and settings. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreSheet(items: List<String>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            (items + listOf("families", "settings")).forEach { item ->
                Row(Modifier.fillMaxWidth().testTag("more_$item").clickable { onPick(item) }.padding(horizontal = 24.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(navIcon(item), null)
                    Spacer(Modifier.size(16.dp))
                    Text(stringResource(navLabel(item)), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

/** Settings: order of the bottom bar and where the chat lives. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun NavSettings(order: List<String>, chatPlacement: String, onOrder: (List<String>) -> Unit, onChat: (String) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.nav_settings_title), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(R.string.nav_settings_sub), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val shown = order.filter { it != NavItems.CHAT || chatPlacement == "TAB" }
        shown.forEachIndexed { i, item ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(navIcon(item), null, Modifier.size(20.dp))
                Spacer(Modifier.size(10.dp))
                Text(stringResource(navLabel(item)) + if (i >= 4) "  · " + stringResource(R.string.nav_more) else "", Modifier.weight(1f))
                IconButton(enabled = i > 0, onClick = { onOrder(swap(order, item, shown[i - 1])) }) { Icon(Icons.Outlined.ArrowUpward, stringResource(R.string.move_up)) }
                IconButton(enabled = i < shown.size - 1, onClick = { onOrder(swap(order, item, shown[i + 1])) }) { Icon(Icons.Outlined.ArrowDownward, stringResource(R.string.move_down)) }
            }
        }
        Text(stringResource(R.string.chat_place), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = chatPlacement != "TAB", onClick = { onChat("ICON") }, label = { Text(stringResource(R.string.chat_place_icon)) })
            FilterChip(selected = chatPlacement == "TAB", onClick = { onChat("TAB") }, label = { Text(stringResource(R.string.chat_place_tab)) })
        }
    }
}

private fun swap(order: List<String>, a: String, b: String): List<String> {
    val l = order.toMutableList(); val i = l.indexOf(a); val j = l.indexOf(b)
    if (i >= 0 && j >= 0) { l[i] = b; l[j] = a }
    return l
}


/**
 * The features of one area with their switches: the family switch (admins) and "for me" (anyone).
 * Used in Settings → Features and inside an area whose features are all off.
 */
@Composable
fun AreaFeatures(
    hub: Hub, states: List<com.rimon.smartfamily.data.repo.ModuleState>, canManage: Boolean,
    onSet: (com.rimon.smartfamily.model.AppModule, Boolean, Boolean) -> Unit, // module, enabled, forMeOnly
) {
    Column(Modifier.fillMaxWidth()) {
        hub.features.forEach { m ->
            val st = states.firstOrNull { it.module == m } ?: return@forEach
            val here = hub.sectionsOf(m)
            val name = if (here.isEmpty()) stringResource(m.title) else here.map { stringResource(it.title) }.joinToString(" · ")
            val elsewhere = Hub.entries.filter { it != hub && it.sectionsOf(m).isNotEmpty() }.map { stringResource(it.title) }
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(m.subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (elsewhere.isNotEmpty()) Text(stringResource(R.string.feature_also_in, elsewhere.joinToString(", ")), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.modules_family_level), style = MaterialTheme.typography.labelSmall)
                    androidx.compose.material3.Switch(checked = st.familyEnabled, enabled = canManage, onCheckedChange = { onSet(m, it, false) })
                }
                Spacer(Modifier.size(8.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.modules_my_level), style = MaterialTheme.typography.labelSmall)
                    androidx.compose.material3.Switch(checked = st.effective, enabled = st.familyEnabled, onCheckedChange = { onSet(m, it, true) })
                }
            }
        }
    }
}

/** Shown in an area whose features are all off: what it is for, and the switches right here. */
@Composable
fun EmptyArea(hub: Hub, states: List<com.rimon.smartfamily.data.repo.ModuleState>, canManage: Boolean,
              onSet: (com.rimon.smartfamily.model.AppModule, Boolean, Boolean) -> Unit) {
    androidx.compose.material3.Card(colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.area_off_title, stringResource(hub.title)), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(if (canManage) R.string.area_off_body else R.string.area_off_body_member), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
            AreaFeatures(hub, states, canManage, onSet)
        }
    }
}

/** Settings → Features, organised by the same areas the app shows. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeaturesScreen(states: List<com.rimon.smartfamily.data.repo.ModuleState>, canManage: Boolean, onBack: () -> Unit,
                   onSet: (com.rimon.smartfamily.model.AppModule, Boolean, Boolean) -> Unit) {
    Scaffold(topBar = {
        com.rimon.smartfamily.ui.components.SfTopAppBar(
            title = { Text(stringResource(R.string.modules_title)) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) } },
            actions = { HelpButton(R.string.help_modules) },
        )
    }) { inner ->
        androidx.compose.foundation.lazy.LazyColumn(Modifier.padding(inner), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(stringResource(if (canManage) R.string.features_intro else R.string.modules_admin_only), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Hub.entries.forEach { hub ->
                item(key = hub.key) {
                    androidx.compose.material3.Card(colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(navIcon(hub.key), null)
                                Spacer(Modifier.size(10.dp))
                                Text(stringResource(hub.title), style = MaterialTheme.typography.titleMedium)
                            }
                            AreaFeatures(hub, states, canManage, onSet)
                        }
                    }
                }
            }
        }
    }
}
