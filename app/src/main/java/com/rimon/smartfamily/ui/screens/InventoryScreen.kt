package com.rimon.smartfamily.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rimon.smartfamily.R
import com.rimon.smartfamily.data.db.InventoryItemEntity
import com.rimon.smartfamily.model.QtyUnit
import com.rimon.smartfamily.model.StorageLocation
import com.rimon.smartfamily.model.qty
import com.rimon.smartfamily.ui.ShoppingViewModel
import com.rimon.smartfamily.ui.components.AddBar
import com.rimon.smartfamily.ui.components.HelpButton
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(vm: ShoppingViewModel, viewer: Viewer, onBack: () -> Unit) {
    val all by remember(viewer.familyId) { vm.inventory(viewer.familyId) }.collectAsState(initial = emptyList())
    val busy by vm.busy.collectAsStateWithLifecycle()
    var location by rememberSaveable { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<InventoryItemEntity?>(null) }

    // Expiry reminders need the notification permission on Android 13+.
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    val shown = all.filter { location == null || it.location == location }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.inv_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) } },
                actions = { HelpButton(R.string.help_inventory) },
            )
        },
    ) { inner ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = inner.calculateTopPadding() + 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                if (!viewer.isChild) {
                    AddBar(
                        hint = stringResource(R.string.inv_add_hint),
                        suggestions = all.map { it.name }.distinct(),
                        onText = { vm.addInventoryText(viewer.familyId, it, location) },
                        onScan = { vm.scanToInventory(viewer.familyId, location) },
                        onPhoto = { vm.photoToInventory(viewer.familyId, it, location) },
                        photoLabel = stringResource(R.string.inv_shelf_photo),
                        enabled = !busy,
                    )
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp))
            }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = location == null, onClick = { location = null }, label = { Text(stringResource(R.string.inv_all)) })
                    StorageLocation.entries.forEach { loc ->
                        val n = all.count { it.location == loc.key }
                        FilterChip(
                            selected = location == loc.key,
                            onClick = { location = loc.key },
                            label = { Text(stringResource(loc.title) + if (n > 0) " ($n)" else "") },
                        )
                    }
                }
            }
            if (shown.isEmpty()) {
                item { Text(stringResource(R.string.inv_empty), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 24.dp)) }
            }
            items(shown, key = { it.id }) { item ->
                InventoryRow(
                    item,
                    onPlus = { vm.updateInventory(item.copy(quantity = item.quantity + 1)) },
                    onMinus = { vm.useInventory(item.id) },
                    onEdit = { if (!viewer.isChild) editing = item },
                )
            }
        }
    }

    editing?.let { item ->
        InventoryEditDialog(
            item,
            onDismiss = { editing = null },
            onSave = { vm.updateInventory(it); editing = null },
            onDelete = { vm.deleteInventory(item.id); editing = null },
        )
    }
}

@Composable
private fun ExpiryChip(expiresAt: Long?) {
    if (expiresAt == null) return
    val days = TimeUnit.MILLISECONDS.toDays(expiresAt - startOfToday()).toInt()
    val (text, color) = when {
        days < 0 -> stringResource(R.string.inv_expired) to MaterialTheme.colorScheme.error
        days == 0 -> stringResource(R.string.inv_expires_today) to MaterialTheme.colorScheme.error
        days <= 3 -> stringResource(R.string.inv_expires_in, days) to Color(0xFFB26A00)
        else -> DateFormat.getDateInstance(DateFormat.SHORT).format(Date(expiresAt)) to MaterialTheme.colorScheme.onSurfaceVariant
    }
    AssistChip(onClick = {}, label = { Text(text) }, colors = AssistChipDefaults.assistChipColors(labelColor = color))
}

private fun startOfToday(): Long = java.util.Calendar.getInstance().apply {
    set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
    set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
}.timeInMillis

@Composable
private fun InventoryRow(item: InventoryItemEntity, onPlus: () -> Unit, onMinus: () -> Unit, onEdit: () -> Unit) {
    val low = item.minQuantity > 0 && item.quantity < item.minQuantity
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onEdit),
    ) {
        Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.bodyLarge)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(StorageLocation.fromKey(item.location).title),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (low) Text(stringResource(R.string.inv_low), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                }
                ExpiryChip(item.expiresAt)
            }
            IconButton(onClick = onMinus) { Icon(Icons.Outlined.Remove, stringResource(R.string.inv_used)) }
            val u = QtyUnit.fromKey(item.unit)
            Text(
                item.quantity.qty() + if (u == QtyUnit.NONE) "" else " " + stringResource(u.title),
                style = MaterialTheme.typography.titleMedium,
            )
            IconButton(onClick = onPlus) { Icon(Icons.Outlined.Add, null) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InventoryEditDialog(item: InventoryItemEntity, onDismiss: () -> Unit, onSave: (InventoryItemEntity) -> Unit, onDelete: () -> Unit) {
    var name by remember { mutableStateOf(item.name) }
    var quantity by remember { mutableStateOf(item.quantity.qty()) }
    var unit by remember { mutableStateOf(item.unit) }
    var location by remember { mutableStateOf(item.location) }
    var min by remember { mutableStateOf(item.minQuantity.qty()) }
    var expires by remember { mutableStateOf(item.expiresAt) }
    var picking by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.inv_edit)) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.item_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
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
                    Text(stringResource(R.string.inv_location), style = MaterialTheme.typography.labelLarge)
                    ChipRow(StorageLocation.entries.map { it.key to it.title }, location) { location = it }
                }
                item {
                    OutlinedTextField(
                        min, { min = it }, label = { Text(stringResource(R.string.inv_min)) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            expires?.let { stringResource(R.string.inv_expiry) + ": " + DateFormat.getDateInstance().format(Date(it)) }
                                ?: stringResource(R.string.inv_no_expiry),
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { picking = true }) { Text(stringResource(R.string.inv_expiry)) }
                        if (expires != null) IconButton(onClick = { expires = null }) { Icon(Icons.Outlined.Remove, null) }
                    }
                }
                item { TextButton(onClick = onDelete) { Text(stringResource(R.string.inv_delete), color = MaterialTheme.colorScheme.error) } }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                onSave(
                    item.copy(
                        name = name.trim(),
                        quantity = quantity.replace(',', '.').toDoubleOrNull() ?: item.quantity,
                        unit = unit, location = location,
                        minQuantity = min.replace(',', '.').toDoubleOrNull() ?: 0.0,
                        expiresAt = expires,
                    )
                )
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )

    if (picking) {
        val state = rememberDatePickerState(initialSelectedDateMillis = expires ?: System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = { TextButton(onClick = { expires = state.selectedDateMillis; picking = false }) { Text(stringResource(R.string.ok)) } },
            dismissButton = { TextButton(onClick = { picking = false }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(state = state) }
    }
}
