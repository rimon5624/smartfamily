package com.rimon.smartfamily.ui.screens

import com.rimon.smartfamily.ui.components.SfTopAppBar
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rimon.smartfamily.R
import com.rimon.smartfamily.data.repo.ModuleState
import com.rimon.smartfamily.model.AppModule
import com.rimon.smartfamily.ui.AppViewModel
import com.rimon.smartfamily.ui.components.HelpButton
import com.rimon.smartfamily.ui.components.ModuleBadge
import kotlinx.coroutines.flow.flowOf
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: AppViewModel,
    padding: PaddingValues,
    onCreateFamily: () -> Unit,
    onOpenModules: (String) -> Unit,
    onOpenCloud: () -> Unit,
    onOpenModule: (AppModule) -> Unit,
    /** Each card of the Today feed gets its own row (2.1: they used to share one cell and overlapped). */
    extras: List<@Composable () -> Unit> = emptyList(),
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val family by vm.activeFamily.collectAsStateWithLifecycle()
    val families by vm.families.collectAsStateWithLifecycle()
    val name by vm.displayName.collectAsStateWithLifecycle()
    val uid = settings?.userId.orEmpty()
    val fam = family
    val modules by remember(fam?.id, uid) {
        if (fam == null) flowOf(emptyList()) else vm.modules(fam.id, uid)
    }.collectAsState(initial = emptyList())
    val members by remember(fam?.id) {
        if (fam == null) flowOf(emptyList()) else vm.members(fam.id)
    }.collectAsState(initial = emptyList())

    Scaffold(
        modifier = Modifier.padding(padding),
        topBar = {
            SfTopAppBar(
                title = { Text(stringResource(R.string.nav_today)) },
                actions = { HelpButton(R.string.help_home) },
            )
        },
    ) { inner ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = inner.calculateTopPadding() + 4.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val gMode = settings?.homeGreeting ?: "COMPACT"
            if (gMode != "HIDE") item(span = { GridItemSpan(2) }) {
                HeaderCard(
                    greeting = greeting(name),
                    familyName = fam?.name,
                    membersCount = members.count { it.status == "ACTIVE" },
                    families = families.map { it.id to it.name },
                    onSwitch = { vm.setActiveFamily(it) },
                    compact = gMode == "COMPACT",
                    onMode = { vm.setHomeCards(greeting = it) },
                )
            }
            extras.forEachIndexed { i, card -> item(span = { GridItemSpan(2) }, key = "card-$i") { card() } }
            val st = settings
            // the sync card only when something needs attention (not connected, or the last sync failed)
            if (st != null && (!(st.supabaseConfigured && vm.isSignedIn) || st.lastSyncError.isNotBlank())) item(span = { GridItemSpan(2) }) {
                val s = settings
                SyncCard(
                    configured = s?.supabaseConfigured == true && vm.isSignedIn,
                    lastSync = s?.lastSyncAt ?: 0,
                    error = s?.lastSyncError.orEmpty(),
                    onClick = onOpenCloud,
                )
            }
            if (fam == null) {
                item(span = { GridItemSpan(2) }) {
                    Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.home_no_family), style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = onCreateFamily) { Text(stringResource(R.string.home_create_family)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun greeting(name: String): String {
    val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val res = when (h) {
        in 5..11 -> R.string.greeting_morning
        in 12..16 -> R.string.greeting_afternoon
        in 17..21 -> R.string.greeting_evening
        else -> R.string.greeting_night
    }
    return stringResource(res, name.ifBlank { "🙂" })
}

@Composable
private fun HeaderCard(
    greeting: String,
    familyName: String?,
    membersCount: Int,
    families: List<Pair<String, String>>,
    onSwitch: (String) -> Unit,
    compact: Boolean = false,
    onMode: (String) -> Unit = {},
) {
    var menu by remember { mutableStateOf(false) }
    var sizeMenu by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    val sizeButton: @Composable () -> Unit = {
        Box {
            IconButton(onClick = { sizeMenu = true }, modifier = Modifier.size(32.dp)) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.card_size), tint = cs.onPrimary, modifier = Modifier.size(18.dp)) }
            DropdownMenu(sizeMenu, { sizeMenu = false }) {
                listOf("FULL" to R.string.card_full, "COMPACT" to R.string.card_compact, "HIDE" to R.string.card_hide).forEach { (k, l) ->
                    DropdownMenuItem(text = { Text(stringResource(l)) }, onClick = { sizeMenu = false; onMode(k) })
                }
            }
        }
    }
    if (compact) {
        // one slim line: greeting · family (tap to switch) · size menu
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Brush.linearGradient(listOf(cs.primary, cs.tertiary)))
                .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(greeting, style = MaterialTheme.typography.titleSmall, color = cs.onPrimary, maxLines = 1, modifier = Modifier.weight(1f))
            if (familyName != null) Box {
                TextButton(onClick = { if (families.size > 1) menu = true }) { Text(familyName, color = cs.onPrimary, maxLines = 1) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    families.forEach { (id, n) -> DropdownMenuItem(text = { Text(n) }, onClick = { menu = false; onSwitch(id) }) }
                }
            }
            sizeButton()
        }
        return
    }
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(cs.primary, cs.primary.copy(alpha = 0.78f), cs.tertiary)))
            .padding(20.dp)
    ) {
        Box(Modifier.align(Alignment.TopEnd)) { sizeButton() }
        Column {
            Text(greeting, style = MaterialTheme.typography.headlineSmall, color = cs.onPrimary)
            if (familyName != null) {
                Spacer(Modifier.height(12.dp))
                Box {
                    AssistChip(
                        onClick = { if (families.size > 1) menu = true },
                        label = { Text(familyName, color = cs.onPrimary) },
                        trailingIcon = if (families.size > 1) {
                            { Icon(Icons.Outlined.ExpandMore, stringResource(R.string.home_switch_family), tint = cs.onPrimary) }
                        } else null,
                    )
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        families.forEach { (id, n) ->
                            DropdownMenuItem(text = { Text(n) }, onClick = { menu = false; onSwitch(id) })
                        }
                    }
                }
                Text(
                    stringResource(R.string.home_members_count, membersCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onPrimary.copy(alpha = 0.85f),
                )
            }
        }
    }
}

@Composable
private fun SyncCard(configured: Boolean, lastSync: Long, error: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val (icon, text, tint) = when {
        !configured -> Triple(Icons.Outlined.CloudOff, stringResource(R.string.home_sync_local), cs.onSurfaceVariant)
        error.isNotEmpty() -> Triple(Icons.Outlined.ErrorOutline, stringResource(R.string.home_sync_error, error), cs.error)
        lastSync == 0L -> Triple(Icons.Outlined.CloudOff, stringResource(R.string.home_sync_pending), cs.onSurfaceVariant)
        else -> Triple(
            Icons.Outlined.CloudDone,
            stringResource(R.string.home_sync_ok, DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(lastSync))),
            cs.secondary,
        )
    }
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerLow)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = tint)
            Spacer(Modifier.size(12.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ModuleCard(state: ModuleState, onClick: () -> Unit) {
    val ready = state.module.available
    Card(
        onClick = onClick,
        enabled = ready,
        colors = CardDefaults.cardColors(
            containerColor = if (ready) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        modifier = Modifier.fillMaxWidth().height(150.dp),
    ) {
        Column(Modifier.padding(14.dp).fillMaxWidth()) {
            ModuleBadge(state.module)
            Spacer(Modifier.height(10.dp))
            Text(stringResource(state.module.title), style = MaterialTheme.typography.titleSmall, maxLines = 2)
            Spacer(Modifier.weight(1f))
            Text(
                if (ready) stringResource(R.string.home_open) else stringResource(R.string.home_coming, state.module.phase + 1),
                style = MaterialTheme.typography.labelSmall,
                color = if (ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
