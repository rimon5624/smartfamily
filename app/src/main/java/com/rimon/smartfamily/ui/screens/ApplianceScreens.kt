package com.rimon.smartfamily.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.DryCleaning
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material.icons.outlined.LocalLaundryService
import androidx.compose.material.icons.outlined.Microwave
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.Wash
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rimon.smartfamily.R
import com.rimon.smartfamily.data.db.ApplianceEntity
import com.rimon.smartfamily.smart.ApplianceProfile
import com.rimon.smartfamily.smart.LaundryAdvisor
import com.rimon.smartfamily.smart.LaundryLoad
import com.rimon.smartfamily.ui.ApplianceViewModel
import com.rimon.smartfamily.ui.components.HelpButton
import com.rimon.smartfamily.ui.components.SectionTitle

private val applianceTypes = listOf(
    "washer" to R.string.at_washer, "dryer" to R.string.at_dryer, "washer_dryer" to R.string.at_washer_dryer,
    "dishwasher" to R.string.at_dishwasher, "ac" to R.string.at_ac, "car" to R.string.at_car, "oven" to R.string.at_oven,
    "fridge" to R.string.at_fridge, "water_heater" to R.string.at_water_heater, "other" to R.string.at_other,
)

private fun typeIcon(t: String): ImageVector = when (t) {
    "washer", "washer_dryer" -> Icons.Outlined.LocalLaundryService
    "dryer" -> Icons.Outlined.DryCleaning
    "dishwasher" -> Icons.Outlined.Wash
    "ac" -> Icons.Outlined.AcUnit
    "car" -> Icons.Outlined.DirectionsCar
    "oven" -> Icons.Outlined.Microwave
    "fridge" -> Icons.Outlined.Kitchen
    "water_heater" -> Icons.Outlined.WaterDrop
    else -> Icons.Outlined.Sensors
}

private fun isLaundry(t: String) = t == "washer" || t == "washer_dryer" || t == "dryer"

private fun formatLeft(sec: Long): String = if (sec >= 3600) "%d:%02d".format(sec / 3600, (sec % 3600) / 60) else "%d:%02d".format(sec / 60, sec % 60)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppliancesScreen(vm: ApplianceViewModel, viewer: Viewer, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val list by remember(viewer.familyId) { vm.appliances(viewer.familyId) }.collectAsState(initial = emptyList())
    val live by vm.live.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf<ApplianceEntity?>(null) }
    LaunchedEffect(list.map { it.id + it.externalId }) { list.filter { it.ecosystem.isNotEmpty() }.forEach { vm.refreshLive(it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.apl_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) } },
                actions = { HelpButton(R.string.help_appliances) },
            )
        },
        floatingActionButton = {
            if (!viewer.isChild) ExtendedFloatingActionButton(
                onClick = { adding = vm.newAppliance(viewer.familyId, viewer.userId, "washer") },
                icon = { Icon(Icons.Outlined.Add, null) }, text = { Text(stringResource(R.string.apl_add)) },
            )
        },
    ) { inner ->
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = inner.calculateTopPadding() + 8.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (list.isEmpty()) item { Text(stringResource(R.string.apl_none), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(list, key = { it.id }) { a ->
                val st = live[a.id]
                Card(onClick = { onOpen(a.id) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(typeIcon(a.type), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.size(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(a.name, style = MaterialTheme.typography.titleMedium)
                            Text(listOf(a.brand, a.model).filter { it.isNotBlank() }.joinToString(" "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (st != null) {
                                Text(
                                    stringResource(R.string.sh_state, st.state) + (st.remainingSeconds?.let { " · " + stringResource(R.string.sh_remaining, formatLeft(it)) } ?: ""),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary,
                                )
                            }
                        }
                        if (a.ecosystem.isNotEmpty()) Icon(Icons.Outlined.Sensors, null, tint = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
            item { Text(stringResource(R.string.sh_tadiran), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp)) }
        }
    }
    adding?.let { a -> ApplianceEditDialog(vm, a, onDismiss = { adding = null }) { vm.save(it); adding = null; onOpen(it.id) } }
}

@Composable
private fun ApplianceEditDialog(vm: ApplianceViewModel, a: ApplianceEntity, onDismiss: () -> Unit, onSave: (ApplianceEntity) -> Unit) {
    var cur by remember { mutableStateOf(a) }
    val busy by vm.busy.collectAsStateWithLifecycle()
    val photo = rememberPhotoPicker { uri -> vm.fillFromLabel(cur, uri) { cur = it } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (a.name.isEmpty()) R.string.apl_add else R.string.apl_tab_info)) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Text(stringResource(R.string.apl_type), style = MaterialTheme.typography.labelLarge); ChipRow(applianceTypes, cur.type) { cur = cur.copy(type = it) } }
                item {
                    OutlinedButton(onClick = photo, enabled = !busy) { Icon(Icons.Outlined.CameraAlt, null); Spacer(Modifier.size(6.dp)); Text(stringResource(R.string.apl_label_photo)) }
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                item { Field(stringResource(R.string.apl_name), cur.name) { cur = cur.copy(name = it) } }
                item { Field(stringResource(R.string.apl_brand), cur.brand) { cur = cur.copy(brand = it) } }
                item { Field(stringResource(R.string.apl_model), cur.model) { cur = cur.copy(model = it) } }
                item { Field(stringResource(R.string.apl_serial), cur.serial) { cur = cur.copy(serial = it) } }
                item { Field(stringResource(R.string.apl_purchase), cur.purchaseDate) { cur = cur.copy(purchaseDate = it) } }
                item { Field(stringResource(R.string.apl_warranty), cur.warrantyEnd) { cur = cur.copy(warrantyEnd = it) } }
                item { Field(stringResource(R.string.apl_store), cur.store) { cur = cur.copy(store = it) } }
                item { Field(stringResource(R.string.apl_service), cur.servicePhone) { cur = cur.copy(servicePhone = it) } }
                item { Field(stringResource(R.string.apl_notes), cur.notes) { cur = cur.copy(notes = it) } }
            }
        },
        confirmButton = {
            TextButton(enabled = cur.name.isNotBlank() || cur.brand.isNotBlank(), onClick = {
                onSave(cur.copy(name = cur.name.ifBlank { listOf(cur.brand, cur.model).filter { it.isNotBlank() }.joinToString(" ") }.trim()))
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApplianceDetailScreen(vm: ApplianceViewModel, viewer: Viewer, id: String, onBack: () -> Unit, onSmartHome: () -> Unit) {
    val appliance by remember(id) { vm.appliance(id) }.collectAsState(initial = null)
    val busy by vm.busy.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val a = appliance ?: return
    val profile = vm.profileOf(a)
    val laundry = isLaundry(a.type)
    val tabs = buildList {
        add(R.string.apl_tab_guide)
        if (laundry) add(R.string.apl_tab_wash)
        add(R.string.apl_tab_care)
        add(R.string.apl_tab_info)
    }
    LaunchedEffect(a.id, a.externalId) { if (a.ecosystem.isNotEmpty()) vm.liveLoop(a) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(a.name) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) } },
                actions = {
                    if (!viewer.isChild) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, stringResource(R.string.apl_delete)) }
                },
            )
        },
    ) { inner ->
        Column(Modifier.padding(top = inner.calculateTopPadding())) {
            LiveCard(vm, a)
            TabRow(selectedTabIndex = tab.coerceAtMost(tabs.lastIndex)) {
                tabs.forEachIndexed { i, res -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(stringResource(res)) }) }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            when (tabs.getOrNull(tab)) {
                R.string.apl_tab_guide -> if (profile == null) ProfileSources(vm, a) else GuideTab(profile, vm, a)
                R.string.apl_tab_wash -> WashTab(vm, viewer, a, profile)
                R.string.apl_tab_care -> CareTab(vm, viewer, a, profile)
                else -> InfoTab(vm, viewer, a, onEdit = { editing = true }, onSmartHome = onSmartHome)
            }
        }
    }
    if (editing) ApplianceEditDialog(vm, a, onDismiss = { editing = false }) { vm.save(it); editing = false }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.apl_delete)) }, text = { Text(a.name) },
            confirmButton = { TextButton(onClick = { vm.delete(a.id); confirmDelete = false; onBack() }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun LiveCard(vm: ApplianceViewModel, a: ApplianceEntity) {
    if (a.ecosystem.isEmpty()) return
    val live by vm.live.collectAsStateWithLifecycle()
    val programs by vm.programs.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf(false) }
    val st = live[a.id]
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), modifier = Modifier.fillMaxWidth().padding(12.dp)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Sensors, null)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.sh_live), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.refreshLive(a) }) { Text(stringResource(R.string.sh_refresh)) }
            }
            if (st != null) {
                Text(stringResource(R.string.sh_state, st.state) + if (st.program.isNotBlank()) " · " + st.program else "")
                st.remainingSeconds?.let { Text(stringResource(R.string.sh_remaining, formatLeft(it)), style = MaterialTheme.typography.titleMedium) }
                st.progressPercent?.let { LinearProgressIndicator(progress = { it / 100f }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) }
                if (st.door.isNotBlank()) Text(stringResource(R.string.sh_door, st.door), style = MaterialTheme.typography.bodySmall)
                if (a.ecosystem == "home_connect") {
                    if (st.remoteStartAllowed) {
                        FilledTonalButton(onClick = { vm.loadPrograms(a); picking = true }) { Text(stringResource(R.string.sh_start)) }
                    } else {
                        Text(stringResource(R.string.sh_remote_off), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
    if (picking) {
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text(stringResource(R.string.sh_start)) },
            text = {
                LazyColumn {
                    items(programs) { p ->
                        TextButton(onClick = { vm.startProgram(a, p); picking = false }) { Text(p.substringAfterLast('.')) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { picking = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun ProfileSources(vm: ApplianceViewModel, a: ApplianceEntity, scrollable: Boolean = true) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var pasted by remember { mutableStateOf("") }
    val pdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? -> if (uri != null) vm.parseManualWithAi(a, uri) }
    val pdfForChat = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            vm.storeManual(a, uri)
            val send = Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, uri)
                .putExtra(Intent.EXTRA_TEXT, vm.aiChatPrompt(a)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(Intent.createChooser(send, null))
        }
    }
    val photo = rememberPhotoPicker { vm.importPhoto(a, it) }
    Column(
        (if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier).padding(if (scrollable) 16.dp else 0.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(R.string.apl_no_profile), style = MaterialTheme.typography.bodyLarge)
        Button(onClick = { pdf.launch(arrayOf("application/pdf")) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.PictureAsPdf, null); Spacer(Modifier.size(8.dp)); Text(stringResource(R.string.apl_from_pdf))
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.apl_with_ai_chat), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.apl_ai_chat_steps), style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { clipboard.setText(AnnotatedString(vm.aiChatPrompt(a))) }) {
                        Icon(Icons.Outlined.ContentCopy, null); Spacer(Modifier.size(6.dp)); Text(stringResource(R.string.apl_copy_prompt))
                    }
                    OutlinedButton(onClick = { pdfForChat.launch(arrayOf("application/pdf")) }) {
                        Icon(Icons.Outlined.Share, null); Spacer(Modifier.size(6.dp)); Text(stringResource(R.string.apl_share_pdf))
                    }
                }
                OutlinedTextField(pasted, { pasted = it }, label = { Text(stringResource(R.string.apl_paste_answer)) }, minLines = 4, modifier = Modifier.fillMaxWidth())
                FilledTonalButton(enabled = pasted.isNotBlank(), onClick = { vm.importText(a, pasted) }) { Text(stringResource(R.string.apl_import)) }
            }
        }
        OutlinedButton(onClick = photo, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.CameraAlt, null); Spacer(Modifier.size(8.dp)); Text(stringResource(R.string.apl_from_photo))
        }
        OutlinedButton(onClick = {
            val q = Uri.encode("${a.brand} ${a.model} user manual pdf")
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$q"))) }
        }, modifier = Modifier.fillMaxWidth(), enabled = a.brand.isNotBlank() || a.model.isNotBlank()) {
            Icon(Icons.Outlined.Search, null); Spacer(Modifier.size(8.dp)); Text(stringResource(R.string.apl_find_manual))
        }
        if (isLaundry(a.type)) {
            TextButton(onClick = { vm.useGeneric(a) }) { Text(stringResource(R.string.apl_generic)) }
        }
    }
}

@Composable
private fun GuideTab(p: ApplianceProfile, vm: ApplianceViewModel, a: ApplianceEntity) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    fun page(n: Int) = if (n > 0) " · " + context.getString(R.string.apl_page, n) else ""
    LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp)) {
        if (vm.hasManual(a.id)) item {
            TextButton(onClick = {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(vm.manualUri(a.id), "application/pdf").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                }
            }) { Icon(Icons.Outlined.PictureAsPdf, null); Spacer(Modifier.size(6.dp)); Text(stringResource(R.string.apl_open_manual)) }
        }
        if (p.errors.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.apl_errors)) }
            item { OutlinedTextField(query, { query = it }, placeholder = { Text(stringResource(R.string.apl_search_error)) }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
            items(p.errors.filter { query.isBlank() || it.code.contains(query, true) || it.meaning.contains(query, true) }) { e ->
                Column(Modifier.padding(vertical = 6.dp)) {
                    Text(e.code + " · " + e.meaning, style = MaterialTheme.typography.titleSmall)
                    if (e.fix.isNotBlank()) Text(e.fix + page(e.page), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (p.programs.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.apl_programs)) }
            items(p.programs) { pr ->
                Column(Modifier.padding(vertical = 6.dp)) {
                    Text(pr.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        listOfNotNull(
                            pr.temperature.takeIf { it.isNotBlank() }, pr.spin.takeIf { it.isNotBlank() }?.let { "↻ $it" },
                            pr.durationMinutes.takeIf { it > 0 }?.let { context.getString(R.string.apl_minutes, it) },
                            pr.maxLoadKg.takeIf { it > 0 }?.let { "${it}kg" },
                        ).joinToString(" · ") + page(pr.page),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (pr.notes.isNotBlank()) Text(pr.notes, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (p.options.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.apl_options)) }
            items(p.options) { o -> Text("• ${o.name}: ${o.description}${page(o.page)}", Modifier.padding(vertical = 4.dp)) }
        }
        if (p.symbols.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.apl_symbols)) }
            items(p.symbols) { s -> Text("• ${s.symbol}: ${s.meaning}${page(s.page)}", Modifier.padding(vertical = 4.dp)) }
        }
        if (p.safety.isNotEmpty() || p.tips.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.apl_safety)) }
            items(p.safety + p.tips) { Text("• $it", Modifier.padding(vertical = 4.dp)) }
        }
        item {
            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            ProfileSources(vm, a, scrollable = false)
        }
    }
}

private val fabrics = listOf(
    "cotton" to R.string.fb_cotton, "synthetics" to R.string.fb_synthetics, "mixed" to R.string.fb_mixed, "wool" to R.string.fb_wool,
    "delicates" to R.string.fb_delicates, "baby" to R.string.fb_baby, "sport" to R.string.fb_sport, "shirts" to R.string.fb_shirts,
    "bedding" to R.string.fb_bedding, "towels" to R.string.fb_towels, "jeans" to R.string.fb_jeans,
)
private val colors = listOf("white" to R.string.cl_white, "light" to R.string.cl_light, "dark" to R.string.cl_dark, "new" to R.string.cl_new)
private val soils = listOf("light" to R.string.sl_light, "normal" to R.string.sl_normal, "heavy" to R.string.sl_heavy)

@Composable
private fun WashTab(vm: ApplianceViewModel, viewer: Viewer, a: ApplianceEntity, profile: ApplianceProfile?) {
    val context = LocalContext.current
    val presets by remember(a.id) { vm.presets(a.id) }.collectAsState(initial = emptyList())
    val label by vm.careLabel.collectAsStateWithLifecycle()
    val ai by vm.aiAdvice.collectAsStateWithLifecycle()
    var fabric by rememberSaveable { mutableStateOf("cotton") }
    var color by rememberSaveable { mutableStateOf("light") }
    var soil by rememberSaveable { mutableStateOf("normal") }
    var savingPreset by remember { mutableStateOf(false) }
    val labelPhoto = rememberPhotoPicker { vm.readCareLabel(it) }
    val p = profile ?: LaundryAdvisor.genericWasher(vm.lang())
    val advice = LaundryAdvisor.advise(p, LaundryLoad(fabric, color, soil, label?.maxTemp))

    LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (profile == null) item { Text(stringResource(R.string.wash_need_profile), color = MaterialTheme.colorScheme.tertiary) }
        if (presets.isNotEmpty()) {
            item { Text(stringResource(R.string.wash_presets), style = MaterialTheme.typography.labelLarge) }
            items(presets, key = { it.id }) { pr ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(pr.name, style = MaterialTheme.typography.titleSmall)
                            Text("${pr.program} · ${pr.temperature} · ${pr.spin}" + if (pr.options.isNotBlank()) " · ${pr.options}" else "", style = MaterialTheme.typography.bodySmall)
                        }
                        if (!viewer.isChild) IconButton(onClick = { vm.deletePreset(pr.id) }) { Icon(Icons.Outlined.Delete, null) }
                    }
                }
            }
        }
        item { Text(stringResource(R.string.wash_fabric), style = MaterialTheme.typography.labelLarge); ChipRow(fabrics, fabric) { fabric = it } }
        item { Text(stringResource(R.string.wash_color), style = MaterialTheme.typography.labelLarge); ChipRow(colors, color) { color = it } }
        item { Text(stringResource(R.string.wash_soil), style = MaterialTheme.typography.labelLarge); ChipRow(soils, soil) { soil = it } }
        item {
            OutlinedButton(onClick = labelPhoto) { Icon(Icons.Outlined.CameraAlt, null); Spacer(Modifier.size(6.dp)); Text(stringResource(R.string.wash_label)) }
            label?.let { l ->
                Text(
                    stringResource(R.string.wash_label_result, l.maxTemp?.toString() ?: "?",
                        when (l.tumbleDry) { false -> stringResource(R.string.wash_no_tumble); true -> stringResource(R.string.wash_tumble_ok); null -> "" }) +
                        if (l.notes.isNotBlank()) " · " + l.notes else "",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(stringResource(R.string.wash_result, advice.program?.name ?: "—"), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.wash_temp_spin, advice.temperature, advice.spin), style = MaterialTheme.typography.titleMedium)
                    if (advice.extras.isNotEmpty()) Text(advice.extras.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
                    Text(advice.reason, style = MaterialTheme.typography.bodySmall)
                    val minutes = advice.program?.durationMinutes ?: 0
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        if (minutes > 0) {
                            FilledTonalButton(onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH, minutes * 60)
                                            .putExtra(AlarmClock.EXTRA_MESSAGE, context.getString(R.string.wash_timer_label, a.name))
                                            .putExtra(AlarmClock.EXTRA_SKIP_UI, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }
                            }) { Icon(Icons.Outlined.Timer, null); Spacer(Modifier.size(6.dp)); Text(stringResource(R.string.wash_start_timer, minutes)) }
                        }
                        if (!viewer.isChild) TextButton(onClick = { savingPreset = true }) { Text(stringResource(R.string.wash_save_preset)) }
                    }
                }
            }
        }
        if (profile != null) item {
            OutlinedButton(onClick = { vm.adviseAi(profile, LaundryLoad(fabric, color, soil, label?.maxTemp), "") }) {
                Icon(Icons.Outlined.AutoAwesome, null); Spacer(Modifier.size(6.dp)); Text(stringResource(R.string.wash_ask_ai))
            }
            if (ai.isNotBlank()) Text(ai, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp))
        }
    }
    if (savingPreset) {
        TextInputDialog(
            title = stringResource(R.string.wash_save_preset), label = stringResource(R.string.wash_preset_name), initial = "",
            onDismiss = { savingPreset = false },
            onConfirm = {
                vm.savePreset(a.id, a.familyId, it, advice.program?.name.orEmpty(), advice.temperature, advice.spin, advice.extras.joinToString(", "))
                savingPreset = false
            },
        )
    }
}

@Composable
private fun CareTab(vm: ApplianceViewModel, viewer: Viewer, a: ApplianceEntity, profile: ApplianceProfile?) {
    val context = LocalContext.current
    LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val tasks = profile?.maintenance.orEmpty()
        if (tasks.isEmpty()) item { Text(stringResource(R.string.apl_no_profile), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(tasks) { m ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Text(m.task, style = MaterialTheme.typography.titleSmall)
                    Text(context.getString(R.string.apl_every_days, m.intervalDays) + if (m.page > 0) " · " + context.getString(R.string.apl_page, m.page) else "", style = MaterialTheme.typography.bodySmall)
                    if (m.howTo.isNotBlank()) Text(m.howTo, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (tasks.isNotEmpty() && !viewer.isChild) item {
            Button(onClick = { vm.addMaintenance(a) }) { Text(stringResource(R.string.apl_add_maintenance)) }
        }
    }
}

@Composable
private fun InfoTab(vm: ApplianceViewModel, viewer: Viewer, a: ApplianceEntity, onEdit: () -> Unit, onSmartHome: () -> Unit) {
    val devices by vm.devices.collectAsStateWithLifecycle()
    var linking by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(
            R.string.apl_brand to a.brand, R.string.apl_model to a.model, R.string.apl_serial to a.serial,
            R.string.apl_purchase to a.purchaseDate, R.string.apl_store to a.store, R.string.apl_service to a.servicePhone, R.string.apl_notes to a.notes,
        ).filter { it.second.isNotBlank() }.forEach { (res, v) ->
            Text(stringResource(res) + ": " + v, style = MaterialTheme.typography.bodyLarge)
        }
        if (a.warrantyEnd.isNotBlank()) {
            val over = a.warrantyEnd < java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
            Text(stringResource(if (over) R.string.apl_warranty_over else R.string.apl_warranty_left, a.warrantyEnd),
                color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary)
        }
        if (a.servicePhone.isNotBlank()) {
            TextButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + a.servicePhone))) } }) { Text("☎ " + a.servicePhone) }
        }
        if (!viewer.isChild) {
            OutlinedButton(onClick = onEdit) { Text(stringResource(R.string.pck_edit)) }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Text(stringResource(R.string.sh_title), style = MaterialTheme.typography.titleSmall)
            if (a.ecosystem.isNotEmpty()) {
                Text(a.ecosystem + " · " + a.externalId.take(12), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { vm.link(a, "", null) }) { Text(stringResource(R.string.sh_unlink)) }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (vm.homeConnectConnected) OutlinedButton(onClick = { vm.loadDevices("home_connect"); linking = "home_connect" }) { Text("Home Connect") }
                    if (vm.smartThingsConnected) OutlinedButton(onClick = { vm.loadDevices("smartthings"); linking = "smartthings" }) { Text("SmartThings") }
                }
                TextButton(onClick = onSmartHome) { Text(stringResource(R.string.sh_link)) }
            }
        }
    }
    linking?.let { eco ->
        AlertDialog(
            onDismissRequest = { linking = null },
            title = { Text(stringResource(R.string.sh_link)) },
            text = {
                LazyColumn {
                    items(devices) { d ->
                        TextButton(onClick = { vm.link(a, eco, d); linking = null }) { Text("${d.name} · ${d.brand} ${d.type}") }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { linking = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartHomeScreen(vm: ApplianceViewModel, clientId: String, onBack: () -> Unit) {
    val code by vm.hcCode.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var id by remember(clientId) { mutableStateOf(clientId) }
    var token by remember { mutableStateOf("") }
    var hcOn by remember { mutableStateOf(vm.homeConnectConnected) }
    var stOn by remember { mutableStateOf(vm.smartThingsConnected) }
    val context = LocalContext.current
    LaunchedEffect(code) { hcOn = vm.homeConnectConnected }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sh_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) } },
            )
        },
    ) { inner ->
        Column(Modifier.padding(top = inner.calculateTopPadding()).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(stringResource(R.string.sh_hc), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(if (hcOn) R.string.sh_sub_on else R.string.sh_sub_off), color = MaterialTheme.colorScheme.secondary)
            Text(stringResource(R.string.sh_hc_intro), style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(id, { id = it }, label = { Text(stringResource(R.string.sh_hc_client)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = id.isNotBlank() && code == null, onClick = { vm.setHomeConnectClientId(id); vm.connectHomeConnect() }) { Text(stringResource(R.string.sh_connect)) }
                if (hcOn) OutlinedButton(onClick = { vm.disconnectHomeConnect(); hcOn = false }) { Text(stringResource(R.string.sh_disconnect)) }
            }
            code?.let { c ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                    Column(Modifier.padding(14.dp)) {
                        Text(stringResource(R.string.sh_hc_code, c.userCode), style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(c.verificationUrl))) } }) { Text(stringResource(R.string.sh_open_link)) }
                    }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text(stringResource(R.string.sh_st), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(if (stOn) R.string.sh_sub_on else R.string.sh_sub_off), color = MaterialTheme.colorScheme.secondary)
            Text(stringResource(R.string.sh_st_intro), style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(token, { token = it }, label = { Text(stringResource(R.string.sh_st_token)) }, singleLine = true,
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = token.isNotBlank(), onClick = { vm.setSmartThingsToken(token); token = ""; stOn = true }) { Text(stringResource(R.string.save)) }
                if (stOn) OutlinedButton(onClick = { vm.setSmartThingsToken(null); stOn = false }) { Text(stringResource(R.string.sh_disconnect)) }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text(stringResource(R.string.sh_tadiran), style = MaterialTheme.typography.bodySmall)
        }
    }
}

