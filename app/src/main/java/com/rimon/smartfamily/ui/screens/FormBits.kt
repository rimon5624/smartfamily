package com.rimon.smartfamily.ui.screens

import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.verticalScroll
import com.rimon.smartfamily.ui.components.SfTopAppBar
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
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
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
            SfTopAppBar(
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) } },
                actions = { HelpButton(help) },
            )
        },
        floatingActionButton = { if (fabLabel != null) ExtendedFloatingActionButton(onClick = onFab, icon = { Icon(Icons.Outlined.Add, null) }, text = { Text(fabLabel) }) },
    ) { inner ->
        Column(Modifier.padding(inner).fillMaxSize()) {
            if (tabs.size > 1 && com.rimon.smartfamily.ui.components.LocalFixedTab.current == null) TabRow(tab) { tabs.forEachIndexed { i, t -> Tab(tab == i, { onTab(i) }, text = { Text(t, maxLines = 1) }) } }
            LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
        }
    }
}

@Composable
fun EditDialog(title: String, canSave: Boolean, onDismiss: () -> Unit, onSave: () -> Unit, onDelete: (() -> Unit)? = null, content: @Composable () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { androidx.compose.foundation.layout.Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()).imePadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() } },
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

/**
 * 2.2 date field for the whole app: the button shows the date as the user chose (Gregorian, Hebrew or both), and the
 * picker has a switch between a Gregorian calendar, a Hebrew date (day, month incl. Adar I/II, Hebrew year) and a
 * Hebrew day/month with a Gregorian year. The value is stored as yyyy-MM-dd; with [allowSunset] a trailing "~" means
 * "after sunset" (the Hebrew date moves to the next day).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DateIn(label: String, value: String, allowSunset: Boolean = false, onChange: (String) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
        Text(if (value.isBlank()) label else "$label: " + com.rimon.smartfamily.smart.DateText.format(context, value) +
            if (com.rimon.smartfamily.smart.DateText.isAfterSunset(value)) " 🌙" else "")
    }
    if (!open) return
    val initial = com.rimon.smartfamily.smart.DateText.parse(value) ?: LocalDate.now()
    var kind by remember { mutableStateOf(if (com.rimon.smartfamily.smart.DateText.mode(context) == "heb") "heb" else "greg") }
    var sunset by remember { mutableStateOf(com.rimon.smartfamily.smart.DateText.isAfterSunset(value)) }
    val h0 = remember { com.rimon.smartfamily.smart.DateText.hebrew(initial, sunset) }
    var hDay by remember { mutableStateOf(h0.day) }
    var hMonth by remember { mutableStateOf(h0.month) }
    var hYear by remember { mutableStateOf(h0.year.toString()) }
    var gYear by remember { mutableStateOf(initial.year.toString()) }
    val st = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    fun result(): LocalDate? = when (kind) {
        "greg" -> st.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
        "heb" -> hYear.toIntOrNull()?.takeIf { it in 5000..6500 }?.let { y -> com.rimon.smartfamily.smart.JewishDays.toGregorian(hDay, hMonth, y) }
        else -> gYear.toIntOrNull()?.takeIf { it in 1800..2300 }?.let { gy ->
            // the Hebrew year in which this day/month falls inside Gregorian year gy
            val guess = gy + 3760
            listOf(guess, guess + 1).map { com.rimon.smartfamily.smart.JewishDays.toGregorian(hDay, hMonth, it) }.firstOrNull { it.year == gy }
                ?: com.rimon.smartfamily.smart.JewishDays.toGregorian(hDay, hMonth, guess)
        }
    }?.let { if (kind != "greg" && sunset) it.minusDays(1) else it }
    fun save() { result()?.let { onChange(it.toString() + if (allowSunset && sunset) "~" else "") }; open = false }
    androidx.compose.ui.window.Dialog(onDismissRequest = { open = false }, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        androidx.compose.material3.Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp), modifier = Modifier.padding(16.dp)) {
            Column(Modifier.padding(16.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(label, style = MaterialTheme.typography.titleMedium)
                androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("greg" to R.string.dt_kind_greg, "heb" to R.string.dt_kind_heb, "hebg" to R.string.dt_kind_heb_greg).forEach { (k, l) ->
                        FilterChip(kind == k, { kind = k }, { Text(stringResource(l)) })
                    }
                }
                if (kind == "greg") DatePicker(st, title = null, headline = null, showModeToggle = true)
                else {
                    val yearNum = if (kind == "heb") hYear.toIntOrNull() ?: h0.year else (gYear.toIntOrNull() ?: initial.year) + 3760
                    val months = com.rimon.smartfamily.smart.JewishDays.monthsOfYear(yearNum.coerceIn(5000, 6500))
                    Text(stringResource(R.string.dt_month), style = MaterialTheme.typography.labelLarge)
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        months.forEach { m -> FilterChip(hMonth == m || (hMonth == 12 && m == 13 && 12 !in months), { hMonth = m },
                            { Text(com.rimon.smartfamily.smart.JewishDays.monthName(m, yearNum.coerceIn(5000, 6500))) }) }
                    }
                    Text(stringResource(R.string.dt_day), style = MaterialTheme.typography.labelLarge)
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        (1..30).forEach { d -> FilterChip(hDay == d, { hDay = d }, { Text(com.rimon.smartfamily.smart.JewishDays.dayLetters(d)) }) }
                    }
                    if (kind == "heb") OutlinedTextField(hYear, { v -> hYear = v.filter { it.isDigit() }.take(4) }, label = { Text(stringResource(R.string.dt_heb_year) +
                        (hYear.toIntOrNull()?.takeIf { it in 5000..6500 }?.let { " · " + com.rimon.smartfamily.smart.JewishDays.yearLetters(it) } ?: "")) },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    else OutlinedTextField(gYear, { v -> gYear = v.filter { it.isDigit() }.take(4) }, label = { Text(stringResource(R.string.dt_greg_year)) },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                }
                if (allowSunset) androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(sunset, { sunset = it }); Text(stringResource(R.string.dt_after_sunset), style = MaterialTheme.typography.bodyMedium)
                }
                result()?.let { r -> Text("= " + com.rimon.smartfamily.smart.DateText.gregText(r) + " · " + com.rimon.smartfamily.smart.DateText.hebrewText(r, allowSunset && sunset),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) }
                androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (value.isNotBlank()) TextButton(onClick = { onChange(""); open = false }) { Text(stringResource(R.string.delete)) }
                    TextButton(onClick = { open = false }) { Text(stringResource(R.string.cancel)) }
                    TextButton(enabled = result() != null, onClick = { save() }) { Text(stringResource(R.string.ok)) }
                }
            }
        }
    }
}

/** A stored date as text, in the user's display mode (Gregorian / Hebrew / both). */
@Composable
fun dateText(v: String): String = com.rimon.smartfamily.smart.DateText.format(androidx.compose.ui.platform.LocalContext.current, v)

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

/** SHR-2 copy mode: an icon that copies the current item into another family I belong to. */
@Composable
fun CopyToFamilyButton(currentFamilyId: String, userId: String, onPick: (familyId: String, name: String) -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val c = remember { (ctx.applicationContext as com.rimon.smartfamily.SmartFamilyApp).container }
    val fams by remember(userId) { c.db.families().observeForUser(userId) }.collectAsState(initial = emptyList())
    val others = fams.filter { it.id != currentFamilyId && it.deletedAt == null }
    if (others.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    androidx.compose.foundation.layout.Box {
        androidx.compose.material3.IconButton(onClick = { open = true }) {
            androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Outlined.ContentCopy, androidx.compose.ui.res.stringResource(com.rimon.smartfamily.R.string.copy_to_family))
        }
        androidx.compose.material3.DropdownMenu(open, { open = false }) {
            androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(com.rimon.smartfamily.R.string.copy_to_family),
                style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
            others.forEach { f -> androidx.compose.material3.DropdownMenuItem(text = { Text(f.name) }, onClick = { open = false; onPick(f.id, f.name) }) }
        }
    }
}
