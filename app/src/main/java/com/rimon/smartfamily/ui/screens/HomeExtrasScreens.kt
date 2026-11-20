package com.rimon.smartfamily.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rimon.smartfamily.R
import com.rimon.smartfamily.data.db.HomeEntryEntity
import com.rimon.smartfamily.data.db.KitItemEntity
import com.rimon.smartfamily.data.db.MeterReadingEntity
import com.rimon.smartfamily.data.db.RepairEntity
import com.rimon.smartfamily.ui.ExtrasViewModel
import com.rimon.smartfamily.ui.RecordsViewModel
import java.time.LocalDate

private val KITS = listOf("EMERGENCY", "LEAVE_HOME", "BEACH", "PARK", "CAMPING", "SHABBAT_AWAY", "SHABBAT_PREP")
private val KIT_TEMPLATES_HE = mapOf(
    "EMERGENCY" to listOf("מים (3 ליטר לאדם ליום)", "שימורים ואוכל יבש", "פנס וסוללות", "מטען נייד", "ערכת עזרה ראשונה", "תרופות קבועות", "רדיו על סוללות", "מסמכים חשובים (צילום)", "משחקים לילדים", "חיתולים / מטרנה"),
    "LEAVE_HOME" to listOf("לסגור גז", "לסגור חלונות", "לכבות דוד", "שעון שבת / תאורה", "להשקות עצים", "להוציא זבל", "לנתק מכשירים", "לנעול דלתות"),
    "BEACH" to listOf("בגדי ים", "מגבות", "קרם הגנה", "כובעים", "שמשייה", "צעצועי חול", "מים קרים", "פירות"),
    "PARK" to listOf("מים", "חטיפים", "מגבונים", "כדור", "שמיכת פיקניק", "קרם הגנה"),
    "CAMPING" to listOf("אוהל", "שקי שינה", "פנסי ראש", "גזייה", "צידנית", "כיסאות", "נייר טואלט", "שקיות זבל"),
    "SHABBAT_AWAY" to listOf("בגדי שבת", "נרות", "יין", "סידורים", "פלטה", "מתנה למארחים"),
    "SHABBAT_PREP" to listOf("פלטה", "מיחם", "שעון שבת לאורות", "נייר טואלט חתוך", "אור במקרר כבוי", "נרות מוכנים", "בגדים מוכנים", "שולחן ערוך"),
)
private val KIT_TEMPLATES_EN = mapOf(
    "EMERGENCY" to listOf("Water (3 L per person per day)", "Canned and dry food", "Flashlight and batteries", "Power bank", "First-aid kit", "Regular medicines", "Battery radio", "Copies of documents", "Games for the kids", "Diapers / formula"),
    "LEAVE_HOME" to listOf("Gas off", "Windows closed", "Water heater off", "Light timer", "Water the plants", "Take out the rubbish", "Unplug devices", "Lock the doors"),
    "BEACH" to listOf("Swimsuits", "Towels", "Sunscreen", "Hats", "Umbrella", "Sand toys", "Cold water", "Fruit"),
    "PARK" to listOf("Water", "Snacks", "Wipes", "Ball", "Picnic blanket", "Sunscreen"),
    "CAMPING" to listOf("Tent", "Sleeping bags", "Headlamps", "Camping stove", "Cooler", "Chairs", "Toilet paper", "Trash bags"),
    "SHABBAT_AWAY" to listOf("Shabbat clothes", "Candles", "Wine", "Siddurim", "Hot plate", "Gift for the hosts"),
    "SHABBAT_PREP" to listOf("Hot plate", "Urn", "Light timer", "Pre-torn tissues", "Fridge light off", "Candles ready", "Clothes ready", "Table set"),
)

@Composable
private fun kitLabel(k: String) = stringResource(when (k) {
    "EMERGENCY" -> R.string.kit_emergency; "LEAVE_HOME" -> R.string.kit_leave_home; "BEACH" -> R.string.trip_beach; "PARK" -> R.string.kit_park
    "CAMPING" -> R.string.trip_camping; "SHABBAT_AWAY" -> R.string.trip_shabbat; else -> R.string.kit_shabbat_prep
})

/** Home extras (2.0): repairs log, meter readings, ready-made kits, guest Wi-Fi QR and secret notes. */
@Composable
fun HomeExtrasScreen(vm: RecordsViewModel, extras: ExtrasViewModel, viewer: Viewer, hebrew: Boolean, startTab: Int, onBack: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(startTab) }
    val repairs by remember(viewer.familyId) { vm.repairs(viewer.familyId) }.collectAsState(initial = emptyList())
    val meters by remember(viewer.familyId) { vm.meters(viewer.familyId) }.collectAsState(initial = emptyList())
    val kits by remember(viewer.familyId) { vm.kits(viewer.familyId) }.collectAsState(initial = emptyList())
    val notes by remember(viewer.familyId) { extras.section(viewer.familyId, "KNOWLEDGE") }.collectAsState(initial = emptyList())
    var kit by rememberSaveable { mutableStateOf("EMERGENCY") }
    var meter by rememberSaveable { mutableStateOf("WATER") }
    var editRepair by remember { mutableStateOf<RepairEntity?>(null) }
    var editMeter by remember { mutableStateOf<MeterReadingEntity?>(null) }
    var editKit by remember { mutableStateOf<KitItemEntity?>(null) }
    var editSecret by remember { mutableStateOf<HomeEntryEntity?>(null) }
    val context = LocalContext.current
    val canEdit = !viewer.isChild
    val tabs = listOf(stringResource(R.string.hx_repairs), stringResource(R.string.hx_meters), stringResource(R.string.hx_kits), stringResource(R.string.hx_secrets))
    val today = LocalDate.now().toString()

    ModuleScaffold(stringResource(R.string.hx_title), R.string.help_home_extras, onBack, tabs, tab, { tab = it },
        fabLabel = if (canEdit) stringResource(R.string.budget_add) else null,
        onFab = {
            when (tab) {
                0 -> editRepair = RepairEntity(id = vm.id(), familyId = viewer.familyId, title = "", date = today)
                1 -> editMeter = MeterReadingEntity(id = vm.id(), familyId = viewer.familyId, meter = meter, value = 0.0, date = today)
                2 -> editKit = KitItemEntity(id = vm.id(), familyId = viewer.familyId, kit = kit, title = "", sort = 99)
                else -> editSecret = HomeEntryEntity(id = vm.id(), familyId = viewer.familyId, section = "KNOWLEDGE", kind = "WIFI", title = "", createdBy = viewer.userId)
            }
        }) {
        when (tab) {
            0 -> {
                if (repairs.isEmpty()) item { Text(stringResource(R.string.hx_repairs_empty)) }
                items(repairs.sortedByDescending { it.date }, key = { it.id }) { r ->
                    val warranty = runCatching { LocalDate.parse(r.warrantyUntil) }.getOrNull()
                    Card(onClick = { if (canEdit) editRepair = r }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(r.title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                                if (r.cost > 0) Text(shekels(r.cost), fontWeight = FontWeight.Bold)
                            }
                            Text(listOfNotNull(r.item.takeIf { it.isNotBlank() }, r.fixedBy.takeIf { it.isNotBlank() }?.let { "🔧 $it" + if (r.rating > 0) " " + "★".repeat(r.rating) else "" },
                                r.date.split('-').reversed().joinToString("/"),
                                warranty?.let { stringResource(if (it.isBefore(LocalDate.now())) R.string.hx_warranty_over else R.string.hx_warranty_until, "${it.dayOfMonth}/${it.monthValue}/${it.year}") }).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall)
                            if (r.phone.isNotBlank()) TextButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + r.phone))) } }) { Text("📞 " + r.phone) }
                        }
                    }
                }
            }
            1 -> {
                item { ChoiceIn(null, listOf("WATER" to stringResource(R.string.hx_water), "ELECTRIC" to stringResource(R.string.hx_electric), "GAS" to stringResource(R.string.hx_gas)), meter) { meter = it } }
                val list = meters.filter { it.meter == meter }.sortedBy { it.date }
                // usage per day between readings; a jump to over twice the average is flagged (a possible leak)
                val usage = list.zipWithNext().mapNotNull { (a, b) ->
                    val days = runCatching { java.time.temporal.ChronoUnit.DAYS.between(LocalDate.parse(a.date), LocalDate.parse(b.date)) }.getOrDefault(0L)
                    if (days > 0) b to (b.value - a.value) / days else null
                }
                val avg = usage.map { it.second }.dropLast(1).takeIf { it.isNotEmpty() }?.average()
                val last = usage.lastOrNull()
                if (list.size < 2) item { Text(stringResource(R.string.hx_meters_hint)) }
                if (avg != null && last != null && last.second > avg * 2) item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.hx_spike, String.format("%.1f", last.second), String.format("%.1f", avg)), Modifier.padding(12.dp))
                    }
                }
                if (usage.size >= 2) item {
                    val color = MaterialTheme.colorScheme.primary
                    Canvas(Modifier.fillMaxWidth().height(120.dp)) {
                        val max = usage.maxOf { it.second }.coerceAtLeast(0.001)
                        val step = size.width / (usage.size - 1).coerceAtLeast(1)
                        usage.zipWithNext().forEachIndexed { i, (a, b) ->
                            drawLine(color, Offset(i * step, size.height * (1 - (a.second / max).toFloat())), Offset((i + 1) * step, size.height * (1 - (b.second / max).toFloat())), strokeWidth = 5f)
                        }
                    }
                    Text(stringResource(R.string.hx_per_day), style = MaterialTheme.typography.labelSmall)
                }
                items(list.reversed(), key = { it.id }) { m ->
                    Row(Modifier.fillMaxWidth().clickable(enabled = canEdit) { editMeter = m }.padding(vertical = 6.dp)) {
                        Text(m.date.split('-').reversed().joinToString("/"), Modifier.weight(1f))
                        Text(String.format("%.1f", m.value), fontWeight = FontWeight.Bold)
                    }
                }
            }
            2 -> {
                item { ChoiceIn(null, KITS.map { it to kitLabel(it) }, kit) { kit = it } }
                val list = kits.filter { it.kit == kit }.sortedBy { it.sort }
                if (list.isEmpty() && canEdit) item {
                    FilledTonalButton(onClick = { vm.addKitTemplate(viewer.familyId, kit, (if (hebrew) KIT_TEMPLATES_HE else KIT_TEMPLATES_EN)[kit].orEmpty()) }) { Text(stringResource(R.string.kids_use_template)) }
                }
                if (list.isNotEmpty()) item { com.rimon.smartfamily.ui.components.ChecklistHeader(list.count { it.checked }, list.size) { vm.resetKit(viewer.familyId, kit, list) } }
                items(list, key = { it.id }) { k ->
                    val expired = runCatching { LocalDate.parse(k.expiresAt) }.getOrNull()?.isBefore(LocalDate.now().plusDays(30)) == true
                    com.rimon.smartfamily.ui.components.ChecklistRow(
                        k.title + if (k.qty > 1) " ×${k.qty}" else "", k.checked, { vm.save(k.copy(checked = !k.checked)) },
                        subtitle = if (k.expiresAt.isNotBlank()) stringResource(R.string.hb_expires, k.expiresAt.split('-').reversed().joinToString("/")) else null,
                        subtitleIsWarning = expired, onDelete = if (canEdit) ({ vm.delete(k) }) else null,
                    ) { if (canEdit) TextButton(onClick = { editKit = k }) { Text(stringResource(R.string.chat_edit)) } }
                }
            }
            else -> {
                val secrets = notes.filter { it.kind == "WIFI" || it.kind == "SECRET" }
                if (secrets.isEmpty()) item { Text(stringResource(R.string.hx_secrets_empty)) }
                items(secrets, key = { it.id }) { e ->
                    val plain by produceState<String?>(null, e.body) { value = extras.openSecret(e.body) }
                    var show by remember { mutableStateOf(false) }
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text((if (e.kind == "WIFI") "📶 " else "🔒 ") + e.title, style = MaterialTheme.typography.titleMedium)
                            if (e.body.startsWith("enc1:")) Text(stringResource(R.string.hx_encrypted), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            if (show && plain != null) {
                                if (e.kind == "WIFI") {
                                    fun esc(s: String) = s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace(":", "\\:")
                                    QrImage("WIFI:T:WPA;S:${esc(e.title)};P:${esc(plain!!)};;")
                                    Text(stringResource(R.string.hx_scan_wifi), style = MaterialTheme.typography.bodySmall)
                                }
                                Text(plain!!, style = MaterialTheme.typography.titleSmall)
                            } else if (plain == null && e.body.isNotBlank()) Text(stringResource(R.string.chat_encrypted))
                            Row {
                                TextButton(onClick = { show = !show }) { Text(stringResource(if (show) R.string.hx_hide else R.string.hx_show)) }
                                if (canEdit) TextButton(onClick = { editSecret = e }) { Text(stringResource(R.string.chat_edit)) }
                            }
                        }
                    }
                }
            }
        }
    }

    editRepair?.let { r ->
        var title by remember { mutableStateOf(r.title) }; var item by remember { mutableStateOf(r.item) }; var by by remember { mutableStateOf(r.fixedBy) }
        var phone by remember { mutableStateOf(r.phone) }; var cost by remember { mutableStateOf(if (r.cost > 0) r.cost.toString() else "") }
        var date by remember { mutableStateOf(r.date) }; var warranty by remember { mutableStateOf(r.warrantyUntil) }; var rating by remember { mutableIntStateOf(r.rating) }; var note by remember { mutableStateOf(r.note) }
        EditDialog(stringResource(R.string.hx_repairs), title.isNotBlank(), { editRepair = null }, {
            vm.save(r.copy(title = title.trim(), item = item.trim(), fixedBy = by.trim(), phone = phone.trim(), cost = cost.toDoubleOrNull() ?: 0.0, date = date, warrantyUntil = warranty, rating = rating, note = note.trim())); editRepair = null
        }, if (r.title.isNotBlank()) ({ vm.delete(r); editRepair = null }) else null) {
            TextIn(stringResource(R.string.hx_what_broke), title) { title = it }
            TextIn(stringResource(R.string.hx_item), item) { item = it }
            TextIn(stringResource(R.string.hx_fixed_by), by) { by = it }
            TextIn(stringResource(R.string.hb_phone), phone) { phone = it }
            NumIn(stringResource(R.string.hx_cost), cost) { cost = it }
            DateIn(stringResource(R.string.budget_date), date) { date = it }
            DateIn(stringResource(R.string.hx_warranty), warranty) { warranty = it }
            ChoiceIn(stringResource(R.string.hx_rating), (0..5).map { it.toString() to if (it == 0) "—" else "★".repeat(it) }, rating.toString()) { rating = it.toInt() }
            TextIn(stringResource(R.string.budget_note), note, multi = true) { note = it }
        }
    }
    editMeter?.let { m ->
        var value by remember { mutableStateOf(if (m.value > 0) m.value.toString() else "") }; var date by remember { mutableStateOf(m.date) }
        EditDialog(stringResource(R.string.hx_meters), value.toDoubleOrNull() != null, { editMeter = null }, { vm.save(m.copy(value = value.toDouble(), date = date)); editMeter = null },
            if (m.value > 0) ({ vm.delete(m); editMeter = null }) else null) {
            NumIn(stringResource(R.string.hx_reading), value) { value = it }
            DateIn(stringResource(R.string.budget_date), date) { date = it }
        }
    }
    editKit?.let { k ->
        var title by remember { mutableStateOf(k.title) }; var qty by remember { mutableStateOf(k.qty.toString()) }; var exp by remember { mutableStateOf(k.expiresAt) }
        EditDialog(kitLabel(k.kit), title.isNotBlank(), { editKit = null }, { vm.save(k.copy(title = title.trim(), qty = qty.toIntOrNull() ?: 1, expiresAt = exp)); editKit = null },
            if (k.title.isNotBlank()) ({ vm.delete(k); editKit = null }) else null) {
            TextIn(stringResource(R.string.trip_add_item), title) { title = it }
            NumIn(stringResource(R.string.hx_qty), qty) { qty = it }
            DateIn(stringResource(R.string.hb_expiry), exp) { exp = it }
        }
    }
    editSecret?.let { e ->
        var kind by remember { mutableStateOf(e.kind) }; var title by remember { mutableStateOf(e.title) }; var secret by remember { mutableStateOf("") }
        LaunchedEffect(e.id) { secret = extras.openSecret(e.body).orEmpty() }
        EditDialog(stringResource(R.string.hx_secrets), title.isNotBlank() && secret.isNotBlank(), { editSecret = null }, {
            extras.saveSecret(e.copy(kind = kind, title = title.trim()), secret); editSecret = null
        }, if (e.title.isNotBlank()) ({ extras.deleteHomeEntry(e); editSecret = null }) else null) {
            ChoiceIn(null, listOf("WIFI" to stringResource(R.string.hx_wifi), "SECRET" to stringResource(R.string.hx_secret)), kind) { kind = it }
            TextIn(stringResource(if (kind == "WIFI") R.string.hx_ssid else R.string.hb_title), title) { title = it }
            TextIn(stringResource(if (kind == "WIFI") R.string.hx_wifi_password else R.string.hx_secret_text), secret) { secret = it }
            Text(stringResource(R.string.hx_secret_note), style = MaterialTheme.typography.labelSmall)
        }
    }
}
