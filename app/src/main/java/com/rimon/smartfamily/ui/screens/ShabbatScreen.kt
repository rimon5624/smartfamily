package com.rimon.smartfamily.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rimon.smartfamily.R
import com.rimon.smartfamily.smart.Cities
import com.rimon.smartfamily.smart.JewishDays
import com.rimon.smartfamily.smart.ShabbatTimes
import com.rimon.smartfamily.ui.ExtrasViewModel
import com.rimon.smartfamily.ui.HolidayPack
import com.rimon.smartfamily.ui.HomeLifeViewModel
import com.rimon.smartfamily.ui.KitchenViewModel
import com.rimon.smartfamily.ui.RecordsViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.Locale

private val SHABBAT = Color(0xFF6C4BB3)

/** Shabbat & holidays (2.0): countdown, prep checklist, meals, the cooking timeline, guests and holiday packs. */
@Composable
fun ShabbatScreen(vm: RecordsViewModel, life: HomeLifeViewModel, kitchen: KitchenViewModel, extras: ExtrasViewModel, viewer: Viewer, cityKey: String, hebrew: Boolean,
                  onBack: () -> Unit, onKitchen: () -> Unit, onPolls: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val city = Cities.byKey(cityKey)
    val times by produceState<ShabbatTimes?>(null, cityKey) { value = runCatching { life.shabbat(cityKey) }.getOrNull() }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = System.currentTimeMillis() } }
    val kits by remember(viewer.familyId) { vm.kits(viewer.familyId) }.collectAsState(initial = emptyList())
    val today = LocalDate.now(JewishDays.ZONE)
    val friday = if (today.dayOfWeek == DayOfWeek.SATURDAY) today.minusDays(1) else today.with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY))
    val plan by remember(viewer.familyId, friday) { kitchen.plan(viewer.familyId, friday.toString(), friday.plusDays(1).toString()) }.collectAsState(initial = emptyList())
    val tf = SimpleDateFormat("HH:mm", Locale.getDefault()).apply { timeZone = JewishDays.TZ }
    val df = SimpleDateFormat("EEEE HH:mm", Locale.getDefault()).apply { timeZone = JewishDays.TZ }
    var confirmPack by remember { mutableStateOf<HolidayPack?>(null) }
    val tabs = listOf(stringResource(R.string.shabbat_this_week), stringResource(R.string.shabbat_holidays))

    ModuleScaffold(stringResource(R.string.mod_shabbat), R.string.help_shabbat, onBack, tabs, tab, { tab = it }) {
        if (tab == 0) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = SHABBAT.copy(alpha = 0.14f)), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        val t = times
                        if (t != null) {
                            Text(t.holiday.ifBlank { t.parasha }, style = MaterialTheme.typography.titleLarge, color = SHABBAT, fontWeight = FontWeight.Bold)
                            val left = t.candleLighting.time - now
                            if (left > 0) {
                                val h = left / 3_600_000; val m = (left / 60_000) % 60
                                Text(if (h >= 24) stringResource(R.string.shabbat_in_days, (h / 24).toInt(), (h % 24).toInt()) else stringResource(R.string.shabbat_in_hours, h.toInt(), m.toInt()),
                                    style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                            } else if (now < t.havdalah.time) Text(stringResource(R.string.shabbat_shalom), style = MaterialTheme.typography.headlineMedium)
                            Text("🕯 " + df.format(t.candleLighting) + "   ✨ " + df.format(t.havdalah), color = SHABBAT)
                            Text(city.he, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            // RCP-12 / CHR-4: the cooking and preparation timeline, scheduled backwards from candle lighting
            times?.let { t ->
                item { Text(stringResource(R.string.shabbat_timeline), style = MaterialTheme.typography.titleMedium) }
                val c = t.candleLighting.time
                val steps = listOf(
                    -30 * 3_600_000L to R.string.tl_shop, -20 * 3_600_000L to R.string.tl_cook_start, -6 * 3_600_000L to R.string.tl_cook_main,
                    -2 * 3_600_000L to R.string.tl_hotplate, -90 * 60_000L to R.string.tl_showers, -45 * 60_000L to R.string.tl_table,
                    -30 * 60_000L to R.string.tl_last, 0L to R.string.tl_candles,
                )
                items(steps, key = { it.first }) { (off, label) ->
                    val at = c + off
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text(df.format(java.util.Date(at)), Modifier.padding(end = 12.dp), color = if (at < now) MaterialTheme.colorScheme.outline else SHABBAT, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(label), color = if (at < now) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.shabbat_meals), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    OutlinedButton(onClick = onKitchen) { Text(stringResource(R.string.shabbat_plan_meals)) }
                }
                if (plan.isEmpty()) Text(stringResource(R.string.shabbat_no_meals), style = MaterialTheme.typography.bodySmall)
                plan.forEach { p -> Text("🍽 " + p.title + " · " + p.date.split('-').reversed().take(2).joinToString("/")) }
            }
            val prep = kits.filter { it.kit == "SHABBAT_PREP" }.sortedBy { it.sort }
            item {
                Text(stringResource(R.string.kit_shabbat_prep), style = MaterialTheme.typography.titleMedium)
                if (prep.isEmpty() && !viewer.isChild) FilledTonalButton(onClick = {
                    vm.addKitTemplate(viewer.familyId, "SHABBAT_PREP", if (hebrew) listOf("פלטה", "מיחם", "שעון שבת לאורות", "נייר טואלט חתוך", "אור במקרר כבוי", "נרות מוכנים", "בגדים מוכנים", "שולחן ערוך")
                    else listOf("Hot plate", "Urn", "Light timer", "Pre-torn tissues", "Fridge light off", "Candles ready", "Clothes ready", "Table set"))
                }) { Text(stringResource(R.string.kids_use_template)) }
            }
            if (prep.isNotEmpty()) item { com.rimon.smartfamily.ui.components.ChecklistHeader(prep.count { it.checked }, prep.size) { vm.resetKit(viewer.familyId, "SHABBAT_PREP", prep) } }
            items(prep, key = { it.id }) { k ->
                com.rimon.smartfamily.ui.components.ChecklistRow(k.title, k.checked, { vm.save(k.copy(checked = !k.checked)) },
                    onDelete = if (!viewer.isChild) ({ vm.delete(k) }) else null)
            }
            item {
                Text(stringResource(R.string.shabbat_guests), style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        extras.createPoll(viewer.familyId, if (hebrew) "מי בא אלינו לשבת?" else "Who is coming for Shabbat?", if (hebrew) listOf("מגיעים לליל שבת", "מגיעים לצהריים", "לא השבוע") else listOf("Friday night", "Shabbat lunch", "Not this week"), true, false, 0L)
                        onPolls()
                    }) { Text(stringResource(R.string.shabbat_poll_guests)) }
                    OutlinedButton(onClick = {
                        extras.createPoll(viewer.familyId, if (hebrew) "איפה עושים שבת?" else "Where do we spend Shabbat?", if (hebrew) listOf("אצלנו", "אצל סבא וסבתא", "אצל האחים") else listOf("At ours", "At the grandparents'", "At the siblings'"), false, false, 0L)
                        onPolls()
                    }) { Text(stringResource(R.string.shabbat_poll_host)) }
                }
            }
        } else {
            item { Text(stringResource(R.string.shabbat_holidays_intro)) }
            items(HolidayPack.entries.toList(), key = { it.name }) { h ->
                val next = remember(h) { (0L..380L).map { today.plusDays(it) }.firstOrNull { d -> JewishDays.info(d, city).holidays.any { it.contains(h.keyHe) } } }
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(if (hebrew) h.he else h.en, style = MaterialTheme.typography.titleMedium)
                            next?.let { Text(stringResource(R.string.shabbat_holiday_on, "${it.dayOfMonth}/${it.monthValue}/${it.year}", java.time.temporal.ChronoUnit.DAYS.between(today, it).toInt()), style = MaterialTheme.typography.bodySmall) }
                        }
                        if (!viewer.isChild && next != null) FilledTonalButton(onClick = { confirmPack = h }) { Text(stringResource(R.string.shabbat_prepare)) }
                    }
                }
            }
        }
    }
    confirmPack?.let { h ->
        val next = (0L..380L).map { today.plusDays(it) }.firstOrNull { d -> JewishDays.info(d, city).holidays.any { it.contains(h.keyHe) } } ?: today
        EditDialog(if (hebrew) h.he else h.en, true, { confirmPack = null }, { vm.createHolidayPack(viewer.familyId, h, hebrew, next); confirmPack = null }) {
            Text(stringResource(R.string.shabbat_pack_body))
            (if (hebrew) h.tasksHe else h.tasksEn).forEach { (d, t) -> Text("• $t (" + stringResource(R.string.shabbat_days_before, d) + ")", style = MaterialTheme.typography.bodySmall) }
        }
    }
}
