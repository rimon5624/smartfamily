package com.rimon.smartfamily.ui.screens

import com.rimon.smartfamily.ui.components.SfTopAppBar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rimon.smartfamily.R
import com.rimon.smartfamily.model.AppModule
import com.rimon.smartfamily.model.Role
import com.rimon.smartfamily.ui.AppViewModel
import com.rimon.smartfamily.ui.components.HelpButton
import com.rimon.smartfamily.ui.components.ModuleBadge
import kotlinx.coroutines.flow.flowOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModulesScreen(vm: AppViewModel, familyId: String, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val uid = settings?.userId.orEmpty()
    val modules by remember(familyId, uid) {
        if (uid.isBlank()) flowOf(emptyList()) else vm.modules(familyId, uid)
    }.collectAsState(initial = emptyList())
    val me by remember(familyId, uid) { vm.myMembership(familyId, uid) }.collectAsState(initial = null)
    val canManage = me?.let { Role.parse(it.role).canManage } == true

    Scaffold(
        topBar = {
            SfTopAppBar(
                title = { Text(stringResource(R.string.modules_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) }
                },
                actions = { HelpButton(R.string.help_modules) },
            )
        },
    ) { inner ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = inner.calculateTopPadding(), bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    stringResource(if (canManage) R.string.modules_intro else R.string.modules_admin_only),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            item {
                Text(
                    stringResource(R.string.modules_order_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            val order = AppModule.ordered(settings?.moduleOrder.orEmpty())
            val sorted = modules.sortedBy { order.indexOf(it.module) }
            items(sorted, key = { it.module.key }) { st ->
                val index = sorted.indexOf(st)
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column {
                            IconButton(onClick = { vm.moveModule(st.module, -1) }, enabled = index > 0, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Outlined.KeyboardArrowUp, stringResource(R.string.move_up))
                            }
                            IconButton(onClick = { vm.moveModule(st.module, 1) }, enabled = index < sorted.lastIndex, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Outlined.KeyboardArrowDown, stringResource(R.string.move_down))
                            }
                        }
                        ModuleBadge(st.module, 40.dp)
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(st.module.title), style = MaterialTheme.typography.titleSmall)
                            Text(
                                stringResource(st.module.subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.modules_family_level), style = MaterialTheme.typography.labelSmall)
                            Switch(
                                checked = st.familyEnabled,
                                enabled = canManage,
                                onCheckedChange = { vm.setModule(familyId, st.module, it, null) },
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.modules_my_level), style = MaterialTheme.typography.labelSmall)
                            Switch(
                                checked = st.userEnabled && st.familyEnabled,
                                enabled = st.familyEnabled,
                                onCheckedChange = { vm.setModule(familyId, st.module, it, uid) },
                            )
                        }
                    }
                }
            }
        }
    }
}
