package com.rimon.smartfamily.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rimon.smartfamily.R
import com.rimon.smartfamily.data.db.ListItemEntity
import com.rimon.smartfamily.model.QtyUnit
import com.rimon.smartfamily.model.ShopCategory
import com.rimon.smartfamily.model.qty
import com.rimon.smartfamily.ui.ShoppingViewModel
import com.rimon.smartfamily.ui.components.AddBar
import com.rimon.smartfamily.ui.components.Avatar
import com.rimon.smartfamily.ui.components.HelpButton
import com.rimon.smartfamily.ui.components.SectionTitle

/** Who is using the screen: needed for children's requests and parents' approvals. */
data class Viewer(val familyId: String, val userId: String, val isChild: Boolean, val canManage: Boolean, val inventoryOn: Boolean)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingListsScreen(vm: ShoppingViewModel, viewer: Viewer, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val lists by remember(viewer.familyId) { vm.lists(viewer.familyId) }.collectAsState(initial = emptyList())
    val templates by remember(viewer.familyId) { vm.templates(viewer.familyId) }.collectAsState(initial = emptyList())
    val counts by remember(viewer.familyId) { vm.counts(viewer.familyId) }.collectAsState(initial = emptyList())
    var creating by remember { mutableStateOf(false) }
    val defaultName = stringResource(R.string.shop_default_list)
    LaunchedEffect(viewer.familyId) { vm.ensureDefaultList(viewer.familyId, defaultName) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.shop_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) } },
                actions = { HelpButton(R.string.help_shopping) },
            )
        },
        floatingActionButton = {
            if (!viewer.isChild) {
                ExtendedFloatingActionButton(
                    onClick = { creating = true },
                    icon = { Icon(Icons.Outlined.Add, null) },
                    text = { Text(stringResource(R.string.shop_new_list)) },
                )
            }
        },
    ) { inner ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = inner.calculateTopPadding(), bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { SectionTitle(stringResource(R.string.shop_lists)) }
            items(lists, key = { it.id }) { l ->
                val c = counts.firstOrNull { it.listId == l.id }
                Card(onClick = { onOpen(l.id) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Outlined.ShoppingCart, null, tint = MaterialTheme.colorScheme.onPrimaryContainer) }
                        Spacer(Modifier.size(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(l.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(R.string.shop_open_count, c?.open ?: 0, c?.done ?: 0),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (l.shopperId != null) Icon(Icons.Outlined.Storefront, null, tint = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
            item { SectionTitle(stringResource(R.string.shop_templates)) }
            if (templates.isEmpty()) {
                item { Text(stringResource(R.string.shop_templates_hint), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(templates, key = { it.id }) { t ->
                Card(onClick = { onOpen(t.id) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Repeat, null)
                        Spacer(Modifier.size(12.dp))
                        Text(t.name, style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }
    }
    if (creating) {
        TextInputDialog(
            title = stringResource(R.string.shop_new_list),
            label = stringResource(R.string.shop_list_name),
            initial = "",
            onDismiss = { creating = false },
            onConfirm = { vm.createList(viewer.familyId, it); creating = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingListScreen(vm: ShoppingViewModel, viewer: Viewer, listId: String, onBack: () -> Unit) {
    val list by remember(listId) { vm.list(listId) }.collectAsState(initial = null)
    val items by remember(listId) { vm.items(listId) }.collectAsState(initial = emptyList())
    val frequent by remember(viewer.familyId) { vm.frequent(viewer.familyId) }.collectAsState(initial = emptyList())
    val low by remember(viewer.familyId) { vm.lowStock(viewer.familyId) }.collectAsState(initial = emptyList())
    val members by remember(viewer.familyId) { vm.members(viewer.familyId) }.collectAsState(initial = emptyList())
    val templates by remember(viewer.familyId) { vm.templates(viewer.familyId) }.collectAsState(initial = emptyList())
    val busy by vm.busy.collectAsStateWithLifecycle()
    val names = members.associate { it.userId to it.displayName }

    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var savingTemplate by remember { mutableStateOf(false) }
    var pickingTemplate by remember { mutableStateOf(false) }
    var finishing by remember { mutableStateOf(false) }
    var showCart by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ListItemEntity?>(null) }

    LaunchedEffect(listId) { vm.liveSync() }
    var party by remember { mutableStateOf<Long?>(null) }

    val l = list
    val isTemplate = l?.isTemplate == true
    val iAmShopping = l?.shopperId == viewer.userId
    val active = items.filter { it.status == "ACTIVE" }
    val requests = items.filter { it.status == "REQUESTED" }
    val open = active.filter { !it.checked }
    val inCart = active.filter { it.checked }
    // SHP-11: categories follow the order this family usually ticks them in this list (learned per phone).
    val context = androidx.compose.ui.platform.LocalContext.current
    val aisle = remember(listId) { AisleOrder(context, listId) }
    var aisleTick by remember { mutableStateOf(0) }
    val rank = remember(aisleTick) { aisle.rank() }
    val grouped = ShopCategory.entries.sortedBy { c -> rank[c.key] ?: (100f + c.ordinal) }.mapNotNull { cat ->
        open.filter { ShopCategory.fromKey(it.category) == cat }.takeIf { it.isNotEmpty() }?.let { cat to it }
    }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    var undo by remember { mutableStateOf<ListItemEntity?>(null) }
    LaunchedEffect(undo) { if (undo != null) { kotlinx.coroutines.delay(5000); undo = null } }
    fun tick(item: ListItemEntity) {
        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
        if (!item.checked && open.size == 1 && open.first().id == item.id) party = System.currentTimeMillis()
        if (!item.checked && iAmShopping) { aisle.record(item.category); aisleTick++ }
        vm.toggle(item.id)
    }
    fun remove(item: ListItemEntity) { undo = item; vm.deleteItem(item.id) }
    // "I'm at the store": the screen stays on while shopping (SHP-10).
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.DisposableEffect(iAmShopping) { view.keepScreenOn = iAmShopping; onDispose { view.keepScreenOn = false } }
    // SHP-13: staples that are due again, from how often the family buys them.
    val onList = remember(items) { items.filter { it.deletedAt == null }.map { it.name.trim().lowercase() }.toSet() }
    val dueStaples = remember(frequent, onList) {
        val now = System.currentTimeMillis()
        frequent.filter { it.everyDays in 2..60 && it.name.trim().lowercase() !in onList && now - it.last >= it.everyDays * 86_400_000L * 0.9 }.take(8)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(l?.name.orEmpty()) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) } },
                actions = {
                    HelpButton(R.string.help_shopping)
                    if (!viewer.isChild) {
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, null) }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.shop_rename)) }, onClick = { menu = false; renaming = true })
                                if (!isTemplate) {
                                    DropdownMenuItem(text = { Text(stringResource(R.string.shop_save_template)) }, onClick = { menu = false; savingTemplate = true })
                                    if (templates.isNotEmpty()) {
                                        DropdownMenuItem(text = { Text(stringResource(R.string.shop_apply_template)) }, onClick = { menu = false; pickingTemplate = true })
                                    }
                                }
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.shop_delete_list), color = MaterialTheme.colorScheme.error) },
                                    onClick = { menu = false; vm.deleteList(listId); onBack() },
                                )
                            }
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (!isTemplate && !viewer.isChild && active.isNotEmpty()) {
                Column(Modifier.background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 16.dp, vertical = 10.dp)) {
                    LinearProgressIndicator(
                        progress = { if (active.isEmpty()) 0f else inCart.size.toFloat() / active.size },
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        FilterChip(
                            selected = iAmShopping,
                            onClick = { vm.setShopping(listId, !iAmShopping) },
                            leadingIcon = { Icon(Icons.Outlined.Storefront, null, Modifier.size(18.dp)) },
                            label = { Text(stringResource(if (iAmShopping) R.string.shop_leave_store else R.string.shop_at_store)) },
                        )
                        Spacer(Modifier.weight(1f))
                        Button(onClick = { finishing = true }, enabled = inCart.isNotEmpty() && !busy) {
                            Icon(Icons.Outlined.CheckCircle, null, Modifier.size(18.dp))
                            Spacer(Modifier.size(6.dp))
                            Text(stringResource(R.string.shop_finish))
                        }
                    }
                }
            }
        },
    ) { inner ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = inner.calculateTopPadding() + 4.dp, bottom = inner.calculateBottomPadding() + 24.dp),
        ) {
            item {
                AddBar(
                    hint = stringResource(R.string.shop_add_hint),
                    suggestions = frequent.map { it.name },
                    onText = { vm.addText(listId, it, viewer.isChild) },
                    onScan = { vm.scanToList(listId, viewer.isChild) },
                    onPhoto = { vm.photoToList(listId, it, viewer.isChild) },
                    photoLabel = stringResource(R.string.shop_photo),
                    enabled = !busy,
                )
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp))
            }
            if (!isTemplate && dueStaples.isNotEmpty()) item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(stringResource(R.string.shop_staples_due), style = MaterialTheme.typography.titleSmall)
                        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            dueStaples.forEach { f ->
                                androidx.compose.material3.AssistChip(onClick = { vm.addText(listId, f.name, viewer.isChild) },
                                    label = { Text(f.name + " · " + stringResource(R.string.shop_every_days, f.everyDays)) })
                            }
                        }
                    }
                }
            }
            undo?.let { u ->
                item(key = "undo") {
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.shop_deleted_x, u.name), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { vm.update(u.copy(deletedAt = null)); undo = null }) { Text(stringResource(R.string.undo)) }
                    }
                }
            }
            val shopper = l?.shopperId
            if (shopper != null && shopper != viewer.userId) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Storefront, null)
                            Spacer(Modifier.size(10.dp))
                            Text(stringResource(R.string.shop_someone_shopping, names[shopper] ?: "?"))
                        }
                    }
                }
            }
            if (!isTemplate && low.isNotEmpty() && !viewer.isChild) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    ) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Inventory2, null)
                            Spacer(Modifier.size(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.shop_low_stock, low.size), style = MaterialTheme.typography.titleSmall)
                                Text(low.take(4).joinToString(", ") { it.item.name }, style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(onClick = { vm.addLowStock(listId, low) }) { Text(stringResource(R.string.shop_add_low)) }
                        }
                    }
                }
            }
            if (requests.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.shop_requests)) }
                items(requests, key = { it.id }) { r ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Avatar(names[r.addedBy] ?: "?", 32.dp)
                        Spacer(Modifier.size(10.dp))
                        Text(r.name + quantityText(r), modifier = Modifier.weight(1f))
                        if (viewer.canManage || !viewer.isChild) {
                            TextButton(onClick = { vm.approve(r.id) }) { Text(stringResource(R.string.shop_approve)) }
                            TextButton(onClick = { vm.deleteItem(r.id) }) { Text(stringResource(R.string.delete)) }
                        }
                    }
                }
            }
            if (active.isEmpty() && requests.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.shop_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 32.dp),
                    )
                }
            }
            grouped.forEach { (cat, rows) ->
                item(key = "cat-" + cat.key) { SectionTitle(stringResource(cat.title)) }
                items(rows, key = { it.id }) { item ->
                    SwipeRow(item, enabled = !isTemplate && !viewer.isChild, onTick = { tick(item) }, onDelete = { remove(item) }) {
                        ItemRow(item, names, isTemplate, onToggle = { tick(item) }, onEdit = { editing = item }, big = iAmShopping)
                    }
                }
            }
            if (inCart.isNotEmpty()) {
                item(key = "cart") {
                    Row(
                        Modifier.fillMaxWidth().clickable { showCart = !showCart }.padding(top = 20.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.shop_in_cart, inCart.size),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(if (showCart) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
                    }
                }
                if (showCart) {
                    items(inCart, key = { it.id }) { item ->
                        SwipeRow(item, enabled = !isTemplate && !viewer.isChild, onTick = { tick(item) }, onDelete = { remove(item) }) {
                            ItemRow(item, names, isTemplate, onToggle = { tick(item) }, onEdit = { editing = item }, big = iAmShopping)
                        }
                    }
                }
            }
        }
    }

    com.rimon.smartfamily.ui.components.Celebration(party)
    editing?.let { item ->
        ItemEditDialog(
            item = item,
            onDismiss = { editing = null },
            onSave = { vm.update(it); editing = null },
            onDelete = { vm.deleteItem(item.id); editing = null },
        )
    }
    if (renaming && l != null) {
        TextInputDialog(
            title = stringResource(R.string.shop_rename), label = stringResource(R.string.shop_list_name), initial = l.name,
            onDismiss = { renaming = false }, onConfirm = { vm.renameList(listId, it); renaming = false },
        )
    }
    if (savingTemplate && l != null) {
        TextInputDialog(
            title = stringResource(R.string.shop_save_template), label = stringResource(R.string.shop_template_name), initial = l.name,
            onDismiss = { savingTemplate = false }, onConfirm = { vm.saveAsTemplate(listId, it); savingTemplate = false },
        )
    }
    if (pickingTemplate) {
        AlertDialog(
            onDismissRequest = { pickingTemplate = false },
            title = { Text(stringResource(R.string.shop_apply_template)) },
            text = {
                Column {
                    templates.forEach { t ->
                        TextButton(onClick = { vm.applyTemplate(t.id, listId); pickingTemplate = false }) { Text(t.name) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickingTemplate = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (finishing) {
        var toInventory by remember { mutableStateOf(viewer.inventoryOn) }
        AlertDialog(
            onDismissRequest = { finishing = false },
            title = { Text(stringResource(R.string.shop_finish_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.shop_finish_body, inCart.size))
                    if (viewer.inventoryOn) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
                            Text(stringResource(R.string.shop_finish_inventory), modifier = Modifier.weight(1f))
                            Switch(checked = toInventory, onCheckedChange = { toInventory = it })
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { vm.finish(listId, toInventory); finishing = false }) { Text(stringResource(R.string.shop_finish)) } },
            dismissButton = { TextButton(onClick = { finishing = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun quantityText(i: ListItemEntity): String {
    val u = QtyUnit.fromKey(i.unit)
    return if (i.quantity == 1.0 && u == QtyUnit.NONE) "" else " · " + i.quantity.qty() + if (u == QtyUnit.NONE) "" else " " + stringResource(u.title)
}

@Composable
private fun ItemRow(item: ListItemEntity, names: Map<String, String>, isTemplate: Boolean, onToggle: () -> Unit, onEdit: () -> Unit, big: Boolean = false) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onEdit)
            .animateContentSize()
            .padding(vertical = if (big) 8.dp else 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!isTemplate) Checkbox(checked = item.checked, onCheckedChange = { onToggle() }) else Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.name + quantityText(item),
                style = if (big) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge,
                textDecoration = if (item.checked) TextDecoration.LineThrough else null,
                color = if (item.checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            val extra = listOfNotNull(
                item.brand.takeIf { it.isNotBlank() },
                item.note.takeIf { it.isNotBlank() },
                item.checkedBy?.let { names[it] }?.let { stringResource(R.string.shop_ticked_by, it) },
            ).joinToString(" · ")
            if (extra.isNotEmpty()) {
                Text(extra, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (item.recurring) Icon(Icons.Outlined.Repeat, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ItemEditDialog(item: ListItemEntity, onDismiss: () -> Unit, onSave: (ListItemEntity) -> Unit, onDelete: () -> Unit) {
    var name by remember { mutableStateOf(item.name) }
    var quantity by remember { mutableStateOf(item.quantity.qty()) }
    var unit by remember { mutableStateOf(item.unit) }
    var category by remember { mutableStateOf(item.category) }
    var brand by remember { mutableStateOf(item.brand) }
    var anyBrand by remember { mutableStateOf(item.anyBrand) }
    var note by remember { mutableStateOf(item.note) }
    var recurring by remember { mutableStateOf(item.recurring) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.item_edit)) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.item_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                item {
                    OutlinedTextField(
                        quantity, { quantity = it }, label = { Text(stringResource(R.string.item_quantity)) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    Text(stringResource(R.string.item_unit), style = MaterialTheme.typography.labelLarge)
                    ChipRow(QtyUnit.entries.map { it.key to it.title }, unit) { unit = it }
                }
                item {
                    Text(stringResource(R.string.item_category), style = MaterialTheme.typography.labelLarge)
                    ChipRow(ShopCategory.entries.map { it.key to it.title }, category) { category = it }
                }
                item {
                    OutlinedTextField(brand, { brand = it }, label = { Text(stringResource(R.string.item_brand)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                item { SwitchLine(stringResource(R.string.item_any_brand), anyBrand) { anyBrand = it } }
                item {
                    OutlinedTextField(note, { note = it }, label = { Text(stringResource(R.string.item_note)) }, modifier = Modifier.fillMaxWidth())
                }
                item { SwitchLine(stringResource(R.string.item_recurring), recurring) { recurring = it } }
                item {
                    TextButton(onClick = onDelete) { Text(stringResource(R.string.item_delete), color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                onSave(
                    item.copy(
                        name = name.trim(), quantity = quantity.replace(',', '.').toDoubleOrNull() ?: item.quantity, unit = unit,
                        category = category, brand = brand.trim(), anyBrand = anyBrand, note = note.trim(), recurring = recurring,
                    )
                )
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ChipRow(options: List<Pair<String, Int>>, selected: String, onSelect: (String) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (key, label) ->
            FilterChip(selected = selected == key, onClick = { onSelect(key) }, label = { Text(stringResource(label)) })
        }
    }
}

@Composable
fun SwitchLine(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(text, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}


/** Swipe right to tick, swipe left to delete (with undo). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeRow(item: ListItemEntity, enabled: Boolean, onTick: () -> Unit, onDelete: () -> Unit, content: @Composable () -> Unit) {
    if (!enabled) { content(); return }
    val state = androidx.compose.material3.rememberSwipeToDismissBoxState(confirmValueChange = { v ->
        when (v) {
            androidx.compose.material3.SwipeToDismissBoxValue.StartToEnd -> { onTick(); false }
            androidx.compose.material3.SwipeToDismissBoxValue.EndToStart -> { onDelete(); true }
            else -> false
        }
    })
    androidx.compose.material3.SwipeToDismissBox(state, backgroundContent = {
        val dir = state.dismissDirection
        Row(
            Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)).background(
                if (dir == androidx.compose.material3.SwipeToDismissBoxValue.EndToStart) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
            ).padding(horizontal = 16.dp),
            horizontalArrangement = if (dir == androidx.compose.material3.SwipeToDismissBoxValue.EndToStart) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(if (dir == androidx.compose.material3.SwipeToDismissBoxValue.EndToStart) Icons.Outlined.Delete else Icons.Outlined.CheckCircle, null)
        }
    }) { Box(Modifier.background(MaterialTheme.colorScheme.surface)) { content() } }
}

/** Learns the order categories are ticked in, per list, on this phone (SHP-11). */
class AisleOrder(context: android.content.Context, private val listId: String) {
    private val prefs = context.getSharedPreferences("sf_aisles", android.content.Context.MODE_PRIVATE)
    private val session = mutableListOf<String>()
    fun record(category: String) {
        if (category in session) return
        session += category
        // moving average of the position each category was reached at
        val pos = session.size.toFloat()
        val old = prefs.getFloat("$listId|$category", pos)
        prefs.edit().putFloat("$listId|$category", old * 0.7f + pos * 0.3f).apply()
    }
    fun rank(): Map<String, Float> = prefs.all.filterKeys { it.startsWith("$listId|") }.mapNotNull { (k, v) -> (v as? Float)?.let { k.substringAfter('|') to it } }.toMap()
}
