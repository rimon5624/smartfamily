package com.rimon.smartfamily.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.rimon.smartfamily.R
import com.rimon.smartfamily.data.db.MemberEntity
import com.rimon.smartfamily.ui.components.HelpButton
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Shared building blocks for the 2.0 screens. */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModuleScaffold(title: String, help: Int, onBack: () -> Unit, tabs: List<String> = emptyList(), tab: Int = 0, onTab: (Int) -> Unit = {},
                   fabLabel: String? = null, onFab: () -> Unit = {}, content: LazyListScope.() -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) } },
                actions = { HelpButton(help) },
            )
        },
        floatingActionButton = { if (fabLabel != null) ExtendedFloatingActionButton(onClick = onFab, icon = { Icon(Icons.Outlined.Add, null) }, text = { Text(fabLabel) }) },
    ) { inner ->
        Column(Modifier.padding(inner).fillMaxSize()) {
            if (tabs.size > 1) TabRow(tab) { tabs.forEachIndexed { i, t -> Tab(tab == i, { onTab(i) }, text = { Text(t, maxLines = 1) }) } }
            LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
        }
    }
}

@Composable
fun EditDialog(title: String, canSave: Boolean, onDismiss: () -> Unit, onSave: () -> Unit, onDelete: (() -> Unit)? = null, content: @Composable () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { item { content() } } },
        confirmButton = { TextButton(enabled = canSave, onClick = onSave) { Text(stringResource(R.string.save)) } },
        dismissButton = {
            androidx.compose.foundation.layout.Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text(stringResource(R.string.delete)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

@Composable
fun TextIn(label: String, value: String, multi: Boolean = false, onChange: (String) -> Unit) =
    OutlinedTextField(value, onChange, label = { Text(label) }, singleLine = !multi, minLines = if (multi) 3 else 1, modifier = Modifier.fillMaxWidth())

@Composable
fun NumIn(label: String, value: String, onChange: (String) -> Unit) =
    OutlinedTextField(value, { v -> onChange(v.filter { it.isDigit() || it == '.' || it == '-' }) }, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateIn(label: String, value: String, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
        Text(if (value.isBlank()) label else "$label: " + value.split('-').reversed().joinToString("/"))
    }
    if (open) {
        val initial = runCatching { LocalDate.parse(value) }.getOrNull() ?: LocalDate.now()
        val st = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = { TextButton(onClick = { st.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()) }; open = false }) { Text(stringResource(R.string.ok)) } },
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(st) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChoiceIn(label: String?, options: List<Pair<String, String>>, value: String, onChange: (String) -> Unit) {
    Column {
        if (label != null) Text(label, style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { options.forEach { (k, l) -> FilterChip(value == k, { onChange(k) }, { Text(l) }) } }
    }
}

@Composable
fun MemberIn(label: String, members: List<MemberEntity>, value: String, onChange: (String) -> Unit) =
    ChoiceIn(label, members.filter { it.status == "ACTIVE" }.map { it.userId to it.displayName }, value, onChange)

fun shekels(v: Double) = "₪" + String.format(java.util.Locale.US, "%,.0f", v)

/** A QR code drawn on the phone (zxing). */
@Composable
fun QrImage(text: String, size: Dp = 220.dp) {
    val bmp = remember(text) {
        val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 512, 512)
        Bitmap.createBitmap(512, 512, Bitmap.Config.RGB_565).also { b ->
            for (x in 0 until 512) for (y in 0 until 512) b.setPixel(x, y, if (m[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        }
    }
    Image(bmp.asImageBitmap(), null, Modifier.size(size))
}
