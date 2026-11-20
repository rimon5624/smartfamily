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
    else -> Hub.byKey(item)?.title ?: R.string.nav_more
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
    content: @Composable (HubSection) -> Unit,
) {
    val sel = sections.firstOrNull { it.key == selectedKey } ?: sections.firstOrNull()
    Scaffold(
        modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(stringResource(hub.title)) },
                    actions = {
                        actions()
                        IconButton(onClick = onSearch) { Icon(Icons.Outlined.Search, stringResource(R.string.search)) }
                        IconButton(onClick = onQuickAdd) { Icon(Icons.Outlined.Add, stringResource(R.string.sc_quickadd)) }
                        ChatTopIcon()
                    },
                )
                if (sections.size > 1 && sel != null) {
                    ScrollableTabRow(selectedTabIndex = sections.indexOf(sel), edgePadding = 12.dp) {
                        sections.forEach { s -> Tab(selected = s == sel, onClick = { onSelect(s.key) }, text = { Text(stringResource(s.title), maxLines = 1) }) }
                    }
                }
            }
        },
    ) { inner ->
        Box(Modifier.padding(inner).consumeWindowInsets(inner).fillMaxSize()) {
            if (sel == null) {
                Text(stringResource(R.string.hub_empty), Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else key(sel.key) {
                CompositionLocalProvider(LocalEmbedded provides true, LocalFixedTab provides sel.tab) { content(sel) }
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
                Row(Modifier.fillMaxWidth().clickable { onPick(item) }.padding(horizontal = 24.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
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
