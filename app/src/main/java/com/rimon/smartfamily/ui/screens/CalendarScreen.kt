package com.rimon.smartfamily.ui.screens

import com.rimon.smartfamily.ui.components.SfTopAppBar
import android.app.TimePickerDialog
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rimon.smartfamily.R
import com.rimon.smartfamily.data.db.EventEntity
import com.rimon.smartfamily.data.db.FamilyDateEntity
import com.rimon.smartfamily.data.db.TripEntity
import com.rimon.smartfamily.data.prefs.AppSettings
import com.rimon.smartfamily.smart.Cities
import com.rimon.smartfamily.smart.DayInfo
import com.rimon.smartfamily.smart.HebrewDateSearch
import com.rimon.smartfamily.smart.JewishDays
import com.rimon.smartfamily.smart.ZmanimCity
import com.rimon.smartfamily.ui.HomeLifeViewModel
import com.rimon.smartfamily.ui.components.HelpButton
import com.rimon.smartfamily.ui.components.SectionTitle
import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

// ============================================================================================
// Model: one thing shown on a calendar day
// ============================================================================================

enum class CalKind { EVENT, DATE, TRIP, HOLIDAY }

data class CalItem(val day: LocalDate, val title: String, val kind: CalKind, val color: Color, val time: Date? = null, val event: EventEntity? = null, val date: FamilyDateEntity? = null, val years: Int? = null)

private val dateTypes = listOf(
    "BIRTHDAY" to R.string.dt_birthday, "ANNIVERSARY" to R.string.dt_anniversary, "YAHRZEIT" to R.string.dt_yahrzeit,
    "BAR_MITZVAH" to R.string.dt_barmitzvah, "ALIYAH" to R.string.dt_aliyah, "CUSTOM" to R.string.dt_custom,
)
private val EVENT = Color(0xFF2C66B0)
private val BIRTHDAY = Color(0xFFD9467A)
private val YAHRZEIT = Color(0xFF6B6B6B)
private val TRIP = Color(0xFF2E8B57)
private val SHABBAT_TINT = Color(0xFF6C4BB3)
private val CHAG_TINT = Color(0xFFC77D12)

private fun dateColor(type: String) = when (type) { "YAHRZEIT" -> YAHRZEIT; "BIRTHDAY", "BAR_MITZVAH" -> BIRTHDAY; else -> Color(0xFF8E44AD) }

private fun sundayOf(d: LocalDate) = d.minusDays((d.dayOfWeek.value % 7).toLong())

private fun buildItems(vm: HomeLifeViewModel, events: List<EventEntity>, dates: List<FamilyDateEntity>, trips: List<TripEntity>, from: LocalDate, to: LocalDate): Map<LocalDate, List<CalItem>> {
    val out = HashMap<LocalDate, MutableList<CalItem>>()
    fun add(i: CalItem) { out.getOrPut(i.day) { mutableListOf() } += i }
    vm.expand(events, JewishDays.startMs(from), JewishDays.startMs(to.plusDays(1))).forEach { o ->
        var d = JewishDays.toLocal(o.start.time)
        val last = JewishDays.toLocal(maxOf(o.start.time, o.end.time - 1))
        var guard = 0
        while (!d.isAfter(last) && guard++ < 60) {
            if (!d.isBefore(from) && !d.isAfter(to)) add(CalItem(d, o.event.title, CalKind.EVENT, EVENT, if (o.event.allDay) null else o.start, event = o.event))
            d = d.plusDays(1)
        }
    }
    dates.forEach { dt -> vm.dateOccurrences(dt, from, to).forEach { (on, years) -> add(CalItem(on, dt.title, CalKind.DATE, dateColor(dt.type), date = dt, years = years)) } }
    trips.forEach { t ->
        val s = runCatching { LocalDate.parse(t.startDate) }.getOrNull() ?: return@forEach
        val e = runCatching { LocalDate.parse(t.endDate) }.getOrNull() ?: s
        var d = s
        while (!d.isAfter(e)) { if (!d.isBefore(from) && !d.isAfter(to)) add(CalItem(d, "🧳 " + t.title, CalKind.TRIP, TRIP)); d = d.plusDays(1) }
    }
    out.values.forEach { l -> l.sortWith(compareBy<CalItem>({ it.kind.ordinal }, { it.time?.time ?: 0L })) }
    return out
}

private fun gregMonthTitle(d: LocalDate) = d.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.getDefault()) + " " + d.year

private fun hebSpan(start: LocalDate, end: LocalDate): String {
    val a = JewishCalendar(JewishDays.noon(start)); val b = JewishCalendar(JewishDays.noon(end))
    val ma = JewishDays.monthName(a.jewishMonth, a.jewishYear); val mb = JewishDays.monthName(b.jewishMonth, b.jewishYear)
    return if (ma == mb) "$ma ${JewishDays.yearLetters(a.jewishYear)}" else if (a.jewishYear == b.jewishYear) "$ma–$mb ${JewishDays.yearLetters(b.jewishYear)}"
    else "$ma ${JewishDays.yearLetters(a.jewishYear)} – $mb ${JewishDays.yearLetters(b.jewishYear)}"
}

/** Title and subtitle of a month in the chosen basis (both calendars, like the Jewish calendar app). */
private fun monthTitles(start: LocalDate, heb: Boolean): Pair<String, String> =
    if (heb) {
        val end = JewishDays.addHebMonths(start, 1).minusDays(1)
        JewishDays.hebMonthTitle(start) to (if (start.month == end.month) gregMonthTitle(start) else
            start.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.getDefault()) + "–" + gregMonthTitle(end))
    } else gregMonthTitle(start) to hebSpan(start, start.withDayOfMonth(start.lengthOfMonth()))

private sealed class Row2 {
    data class Label(val start: LocalDate) : Row2()
    data class Week(val sunday: LocalDate, val month: LocalDate) : Row2()
}

// ============================================================================================
// Screen
// ============================================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(vm: HomeLifeViewModel, viewer: Viewer, settings: AppSettings?, trips: List<TripEntity>, onBack: () -> Unit) {
    val dates by remember(viewer.familyId) { vm.dates(viewer.familyId) }.collectAsState(initial = emptyList())
    val events by remember(viewer.familyId) { vm.events(viewer.familyId) }.collectAsState(initial = emptyList())
    val city = Cities.byKey(settings?.zmanimCity ?: "jerusalem")
    val hebFirst = settings?.calHebFirst ?: true
    val heb = (settings?.calBasis ?: "GREG") == "HEB"
    val view = settings?.calView ?: "MONTH"
    val showShabbat = settings?.calShabbatRow ?: true
    val today = remember { LocalDate.now(JewishDays.ZONE) }
    var selected by rememberSaveable { mutableStateOf(today.toString()) }
    val sel = LocalDate.parse(selected)
    var sheetDay by remember { mutableStateOf<LocalDate?>(null) }
    var editDate by remember { mutableStateOf<FamilyDateEntity?>(null) }
    var editEvent by remember { mutableStateOf<EventEntity?>(null) }
    var jump by remember { mutableStateOf(false) }
    var converter by remember { mutableStateOf(false) }
    var icsWizard by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var fab by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // ---- the continuous month list (±6 years around today), rebuilt when the basis changes
    val rows = remember(heb) {
        val starts = mutableListOf<LocalDate>()
        if (heb) {
            var s = JewishDays.addHebMonths(JewishDays.hebMonthStart(today), -72)
            repeat(150) { starts += s; s = JewishDays.addHebMonths(s, 1) }
        } else {
            var s = today.withDayOfMonth(1).minusMonths(72)
            repeat(150) { starts += s; s = s.plusMonths(1) }
        }
        val list = mutableListOf<Row2>()
        var w = sundayOf(starts.first())
        val end = starts.last()
        var monthIdx = 0
        while (!w.isAfter(end)) {
            val weekEnd = w.plusDays(6)
            while (monthIdx + 1 < starts.size && !starts[monthIdx + 1].isAfter(weekEnd)) {
                monthIdx++
                list += Row2.Label(starts[monthIdx])
            }
            if (list.isEmpty()) list += Row2.Label(starts[0])
            list += Row2.Week(w, starts[monthIdx])
            w = w.plusWeeks(1)
        }
        list
    }
    fun rowOf(d: LocalDate): Int {
        val ms = if (heb) JewishDays.hebMonthStart(d) else d.withDayOfMonth(1)
        return rows.indexOfFirst { it is Row2.Label && it.start == ms }.coerceAtLeast(0)
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = rowOf(sel))
    LaunchedEffect(heb) { listState.scrollToItem(rowOf(sel)) }
    val visibleMonth by remember(rows) {
        derivedStateOf {
            when (val r = rows.getOrNull(listState.firstVisibleItemIndex)) {
                is Row2.Label -> r.start; is Row2.Week -> r.month; null -> today
            }
        }
    }
    fun goTo(d: LocalDate) {
        selected = d.toString()
        if (view == "MONTH") scope.launch { listState.animateScrollToItem(rowOf(d)) }
    }

    // items for the loaded range (around the visible month / selected day)
    val center = if (view == "MONTH") visibleMonth else sel
    val rangeFrom = center.minusDays(50); val rangeTo = center.plusDays(100)
    val items = remember(events, dates, trips, rangeFrom, rangeTo) { buildItems(vm, events, dates, trips, rangeFrom, rangeTo) }
    val (title, subtitle) = when (view) {
        "MONTH" -> monthTitles(visibleMonth, heb)
        "YEAR" -> if (heb) JewishDays.yearLetters(JewishCalendar(JewishDays.noon(sel)).jewishYear) to "" else sel.year.toString() to ""
        else -> monthTitles(if (heb) JewishDays.hebMonthStart(sel) else sel.withDayOfMonth(1), heb)
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val icsPicker = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importIcs(uri, viewer.familyId, viewer.userId)
    }
    Scaffold(
        topBar = {
            SfTopAppBar(
                title = {
                    Column {
                        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) } },
                actions = {
                    IconButton(onClick = { goTo(today) }) { Icon(Icons.Outlined.Today, stringResource(R.string.cal_today)) }
                    IconButton(onClick = { jump = true }) { Icon(Icons.Outlined.CalendarMonth, stringResource(R.string.cal_jump)) }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, null) }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(if (heb) R.string.cal_basis_to_greg else R.string.cal_basis_to_heb)) },
                                leadingIcon = { Icon(Icons.Outlined.SwapHoriz, null) }, onClick = { menu = false; vm.setCalendar(basis = if (heb) "GREG" else "HEB") })
                            DropdownMenuItem(text = { Text(stringResource(if (hebFirst) R.string.cal_primary_greg else R.string.cal_primary_heb)) },
                                onClick = { menu = false; vm.setCalendar(hebFirst = !hebFirst) })
                            DropdownMenuItem(text = { Text(stringResource(if (showShabbat) R.string.cal_hide_shabbat else R.string.cal_show_shabbat)) },
                                onClick = { menu = false; vm.setCalendar(shabbatRow = !showShabbat) })
                            DropdownMenuItem(text = { Text(stringResource(R.string.cal_converter)) }, onClick = { menu = false; converter = true })
                            DropdownMenuItem(text = { Text(stringResource(R.string.ics_export)) }, leadingIcon = { Icon(Icons.Outlined.Share, null) }, onClick = {
                                menu = false
                                scope.launch { runCatching { context.startActivity(vm.exportIcs(viewer.familyId, events, dates)) } }
                            })
                            if (!viewer.isChild) DropdownMenuItem(text = { Text(stringResource(R.string.ics_import)) }, leadingIcon = { Icon(Icons.Outlined.FileOpen, null) }, onClick = {
                                menu = false; icsWizard = true
                            })
                        }
                    }
                    HelpButton(R.string.help_calendar)
                },
            )
        },
        floatingActionButton = {
            if (!viewer.isChild) Box {
                FloatingActionButton(onClick = { fab = true }) { Icon(Icons.Outlined.Add, null) }
                DropdownMenu(fab, { fab = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.cal_add_event)) }, leadingIcon = { Icon(Icons.Outlined.Event, null) },
                        onClick = { fab = false; editEvent = newEvent(viewer, sel) })
                    DropdownMenuItem(text = { Text(stringResource(R.string.cal_add_date)) }, leadingIcon = { Icon(Icons.Outlined.Cake, null) },
                        onClick = { fab = false; editDate = newDate(viewer, sel) })
                }
            }
        },
    ) { inner ->
        Column(Modifier.padding(inner).fillMaxSize()) {
            ViewSwitch(view, heb, onView = { vm.setCalendar(view = it) }, onBasis = { vm.setCalendar(basis = if (it) "HEB" else "GREG") })
            when (view) {
                "MONTH" -> {
                    WeekdayHeader()
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 4.dp, end = 4.dp, bottom = 96.dp)) {
                        items(rows.size, key = { i -> when (val r = rows[i]) { is Row2.Label -> "L" + r.start; is Row2.Week -> "W" + r.sunday } }) { i ->
                            when (val r = rows[i]) {
                                is Row2.Label -> MonthLabel(r.start, heb)
                                is Row2.Week -> WeekRow(r.sunday, today, sel, hebFirst, city, items, showShabbat,
                                    onTap = { selected = it.toString(); sheetDay = it }, onLong = { selected = it.toString(); sheetDay = it })
                            }
                        }
                    }
                }
                "WEEK" -> WeekView(sundayOf(sel), today, city, items, onStep = { goTo(sel.plusWeeks(it.toLong())) }, onOpen = { selected = it.toString(); sheetDay = it })
                "DAY" -> DayPage(sel, city, items[sel].orEmpty(), viewer, onStep = { goTo(sel.plusDays(it.toLong())) },
                    onEditEvent = { editEvent = it }, onEditDate = { editDate = it }, onNewEvent = { editEvent = newEvent(viewer, sel) }, onNewDate = { editDate = newDate(viewer, sel) })
                "AGENDA" -> AgendaView(sel, today, city, items, onOpen = { selected = it.toString(); sheetDay = it })
                "YEAR" -> YearView(sel, heb, today, items, onPickMonth = { vm.setCalendar(view = "MONTH"); goTo(it) })
            }
        }
    }

    sheetDay?.let { d ->
        ModalBottomSheet(onDismissRequest = { sheetDay = null }) {
            DaySheetContent(d, city, items[d].orEmpty(), viewer,
                onEditEvent = { sheetDay = null; editEvent = it }, onEditDate = { sheetDay = null; editDate = it },
                onNewEvent = { sheetDay = null; editEvent = newEvent(viewer, d) }, onNewDate = { sheetDay = null; editDate = newDate(viewer, d) },
                onOpenDay = { sheetDay = null; vm.setCalendar(view = "DAY") })
        }
    }
    if (jump) HebrewOrGregorianPicker(stringResource(R.string.cal_jump), sel, onDismiss = { jump = false }) { jump = false; goTo(it) }
    if (icsWizard) IcsImportWizard(vm, viewer, onFile = { icsWizard = false; icsPicker.launch(arrayOf("text/calendar", "text/*", "application/octet-stream")) }, onDismiss = { icsWizard = false })
    if (converter) ConverterDialog(onDismiss = { converter = false }, onGo = { converter = false; goTo(it) })
    editDate?.let { d -> DateEditDialog(d, onDismiss = { editDate = null }, onSave = { vm.saveDate(it); editDate = null }, onDelete = { vm.deleteDate(d.id); editDate = null }) }
    editEvent?.let { e -> EventEditDialog(e, onDismiss = { editEvent = null }, onSave = { vm.saveEvent(it); editEvent = null }, onDelete = { vm.deleteEvent(e.id); editEvent = null }) }
}

private fun newEvent(viewer: Viewer, d: LocalDate): EventEntity {
    val start = JewishDays.startMs(d) + 18 * 3_600_000L
    return EventEntity(id = UUID.randomUUID().toString(), familyId = viewer.familyId, title = "", startAt = start, endAt = start + 3_600_000L, ownerId = viewer.userId)
}

private fun newDate(viewer: Viewer, d: LocalDate): FamilyDateEntity =
    FamilyDateEntity(id = UUID.randomUUID().toString(), familyId = viewer.familyId, title = "", gregorian = d.toString())

// ============================================================================================
// Pieces
// ============================================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ViewSwitch(view: String, heb: Boolean, onView: (String) -> Unit, onBasis: (Boolean) -> Unit) {
    val views = listOf("MONTH" to R.string.cal_v_month, "WEEK" to R.string.cal_v_week, "DAY" to R.string.cal_v_day, "AGENDA" to R.string.cal_v_agenda, "YEAR" to R.string.cal_v_year)
    Column(Modifier.padding(horizontal = 8.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            views.forEachIndexed { i, (k, l) ->
                SegmentedButton(selected = view == k, onClick = { onView(k) }, shape = SegmentedButtonDefaults.itemShape(i, views.size), label = { Text(stringResource(l), maxLines = 1, fontSize = 12.sp) })
            }
        }
        if (view == "MONTH" || view == "YEAR") Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(heb, { onBasis(true) }, { Text(stringResource(R.string.cal_hebrew_months)) })
            FilterChip(!heb, { onBasis(false) }, { Text(stringResource(R.string.cal_greg_months)) })
        }
    }
}

@Composable
private fun WeekdayHeader() {
    val names = stringResource(R.string.cal_weekdays_short).split(',')
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp)) {
        names.forEachIndexed { i, n ->
            Text(n, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium,
                color = if (i == 6) SHABBAT_TINT else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun MonthLabel(start: LocalDate, heb: Boolean) {
    val (t, s) = monthTitles(start, heb)
    Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 14.dp, bottom = 4.dp), verticalAlignment = Alignment.Bottom) {
        Text(t, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.size(8.dp))
        Text(s, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}

private val timeFmt get() = SimpleDateFormat("HH:mm", Locale.getDefault()).apply { timeZone = JewishDays.TZ }

@Composable
private fun WeekRow(sunday: LocalDate, today: LocalDate, sel: LocalDate, hebFirst: Boolean, city: ZmanimCity, items: Map<LocalDate, List<CalItem>>, showShabbat: Boolean, onTap: (LocalDate) -> Unit, onLong: (LocalDate) -> Unit) {
    val days = (0..6).map { sunday.plusDays(it.toLong()) }
    val infos = days.map { JewishDays.info(it, city) }
    Column {
        Row(Modifier.fillMaxWidth()) {
            days.forEachIndexed { i, d -> DayCell(d, infos[i], items[d].orEmpty(), d == today, d == sel, hebFirst, Modifier.weight(1f), onTap, onLong) }
        }
        if (showShabbat) {
            val fri = infos[5]; val sat = infos[6]
            val candles = fri.candles; val hav = sat.havdalah ?: infos.firstNotNullOfOrNull { it.havdalah }
            val parsha = sat.parsha.ifBlank { sat.holidays.firstOrNull().orEmpty() }
            if (candles != null || hav != null || parsha.isNotBlank()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp).clip(RoundedCornerShape(8.dp))
                        .background(SHABBAT_TINT.copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (parsha.isNotBlank()) Text(parsha.removePrefix("פרשת "), style = MaterialTheme.typography.labelMedium, color = SHABBAT_TINT, fontWeight = FontWeight.SemiBold)
                    candles?.let { Text("🕯 " + timeFmt.format(it), style = MaterialTheme.typography.labelMedium, color = SHABBAT_TINT) }
                    hav?.let { Text("✨ " + timeFmt.format(it), style = MaterialTheme.typography.labelMedium, color = SHABBAT_TINT) }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DayCell(d: LocalDate, info: DayInfo, items: List<CalItem>, isToday: Boolean, isSel: Boolean, hebFirst: Boolean, modifier: Modifier, onTap: (LocalDate) -> Unit, onLong: (LocalDate) -> Unit) {
    val haptic = LocalHapticFeedback.current
    val bg = when {
        isSel -> MaterialTheme.colorScheme.primaryContainer
        info.yomTov -> CHAG_TINT.copy(alpha = 0.14f)
        info.shabbat -> SHABBAT_TINT.copy(alpha = 0.10f)
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }
    val holidayTitles = info.holidays.take(1)
    Column(
        modifier.padding(1.dp).heightIn(min = 86.dp).clip(RoundedCornerShape(6.dp)).background(bg)
            .then(if (isToday) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp)) else Modifier)
            .combinedClickable(onClick = { onTap(d) }, onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onLong(d) })
            .padding(horizontal = 3.dp, vertical = 2.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Text(if (hebFirst) info.hDayLetters else d.dayOfMonth.toString(), fontWeight = FontWeight.Bold, fontSize = 14.sp,
                color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            Text(if (hebFirst) d.dayOfMonth.toString() else info.hDayLetters, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        holidayTitles.forEach { h ->
            Text(h, fontSize = 9.sp, lineHeight = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, color = if (info.yomTov || info.fast) CHAG_TINT else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items.take(3).forEach { i ->
            Text(i.title, fontSize = 9.sp, lineHeight = 11.sp, maxLines = 1, overflow = TextOverflow.Clip, color = Color.White,
                modifier = Modifier.fillMaxWidth().padding(top = 1.dp).clip(RoundedCornerShape(3.dp)).background(i.color).padding(horizontal = 2.dp))
        }
        if (items.size > 3) Text("+${items.size - 3}", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DayHeader(d: LocalDate, info: DayInfo) {
    val greg = d.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()) + ", " + d.dayOfMonth + " " + d.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) + " " + d.year
    Text(greg, style = MaterialTheme.typography.titleMedium)
    Text(info.hFull, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    info.holidays.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = CHAG_TINT) }
    if (info.parsha.isNotBlank()) Text(info.parsha, style = MaterialTheme.typography.bodyMedium, color = SHABBAT_TINT)
    if (info.omer > 0) Text(stringResource(R.string.cal_omer, info.omer), style = MaterialTheme.typography.bodySmall)
    info.candles?.let { Text(stringResource(R.string.cal_candles, timeFmt.format(it)), color = SHABBAT_TINT) }
    info.havdalah?.let { Text(stringResource(R.string.cal_havdalah, timeFmt.format(it)), color = SHABBAT_TINT) }
}

@Composable
private fun ItemRows(items: List<CalItem>, viewer: Viewer, onEditEvent: (EventEntity) -> Unit, onEditDate: (FamilyDateEntity) -> Unit) {
    if (items.isEmpty()) Text(stringResource(R.string.cal_nothing), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 6.dp))
    items.forEach { i ->
        Card(
            onClick = { if (!viewer.isChild) { i.event?.let(onEditEvent); i.date?.let(onEditDate) } },
            colors = CardDefaults.cardColors(containerColor = i.color.copy(alpha = 0.14f)),
            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        ) {
            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(i.color))
                Spacer(Modifier.size(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(i.title, style = MaterialTheme.typography.titleSmall)
                    val sub = listOfNotNull(
                        i.time?.let { timeFmt.format(it) },
                        i.event?.location?.takeIf { it.isNotBlank() },
                        i.years?.takeIf { it > 0 }?.let { stringResource(R.string.date_years, it) },
                        i.date?.note?.takeIf { it.isNotBlank() }?.let { "🎁 $it" },
                    )
                    if (sub.isNotEmpty()) Text(sub.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun DaySheetContent(d: LocalDate, city: ZmanimCity, items: List<CalItem>, viewer: Viewer, onEditEvent: (EventEntity) -> Unit, onEditDate: (FamilyDateEntity) -> Unit, onNewEvent: () -> Unit, onNewDate: () -> Unit, onOpenDay: () -> Unit) {
    val info = JewishDays.info(d, city)
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
        DayHeader(d, info)
        Spacer(Modifier.height(8.dp))
        ItemRows(items, viewer, onEditEvent, onEditDate)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!viewer.isChild) {
                FilledTonalButton(onClick = onNewEvent) { Text(stringResource(R.string.cal_add_event)) }
                OutlinedButton(onClick = onNewDate) { Text(stringResource(R.string.cal_add_date)) }
            }
            TextButton(onClick = onOpenDay) { Text(stringResource(R.string.cal_zmanim)) }
        }
    }
}

private val zmanLabels = mapOf(
    "alos" to R.string.z_alos, "sunrise" to R.string.z_sunrise, "shma" to R.string.z_shma, "tfila" to R.string.z_tfila,
    "chatzos" to R.string.z_chatzos, "mincha" to R.string.z_mincha, "plag" to R.string.z_plag, "sunset" to R.string.z_sunset, "tzais" to R.string.z_tzais,
)

@Composable
private fun DayPage(d: LocalDate, city: ZmanimCity, items: List<CalItem>, viewer: Viewer, onStep: (Int) -> Unit, onEditEvent: (EventEntity) -> Unit, onEditDate: (FamilyDateEntity) -> Unit, onNewEvent: () -> Unit, onNewDate: () -> Unit) {
    val info = JewishDays.info(d, city)
    LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onStep(-1) }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null) }
                Column(Modifier.weight(1f)) { DayHeader(d, info) }
                IconButton(onClick = { onStep(1) }) { Icon(Icons.AutoMirrored.Outlined.ArrowForward, null) }
            }
            ItemRows(items, viewer, onEditEvent, onEditDate)
            if (!viewer.isChild) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onNewEvent) { Text(stringResource(R.string.cal_add_event)) }
                OutlinedButton(onClick = onNewDate) { Text(stringResource(R.string.cal_add_date)) }
            }
            SectionTitle(stringResource(R.string.cal_zmanim) + " · " + city.he)
        }
        items(JewishDays.zmanim(d, city)) { (k, t) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(stringResource(zmanLabels[k] ?: R.string.cal_zmanim), Modifier.weight(1f))
                Text(t?.let { timeFmt.format(it) } ?: "—", fontWeight = FontWeight.SemiBold)
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun WeekView(sunday: LocalDate, today: LocalDate, city: ZmanimCity, items: Map<LocalDate, List<CalItem>>, onStep: (Int) -> Unit, onOpen: (LocalDate) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(12.dp, 4.dp, 12.dp, 96.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onStep(-1) }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null) }
                Text(hebSpan(sunday, sunday.plusDays(6)), Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleSmall)
                IconButton(onClick = { onStep(1) }) { Icon(Icons.AutoMirrored.Outlined.ArrowForward, null) }
            }
        }
        items((0..6).map { sunday.plusDays(it.toLong()) }) { d ->
            val info = JewishDays.info(d, city)
            val list = items[d].orEmpty()
            Card(
                onClick = { onOpen(d) },
                colors = CardDefaults.cardColors(containerColor = when { info.yomTov -> CHAG_TINT.copy(alpha = 0.12f); info.shabbat -> SHABBAT_TINT.copy(alpha = 0.10f); else -> MaterialTheme.colorScheme.surfaceContainerLow }),
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).then(if (d == today) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, CardDefaults.shape) else Modifier),
            ) {
                Row(Modifier.padding(10.dp)) {
                    Column(Modifier.size(width = 56.dp, height = 44.dp)) {
                        Text(d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()), style = MaterialTheme.typography.labelMedium)
                        Text("${d.dayOfMonth} · ${info.hDayLetters}", fontWeight = FontWeight.Bold)
                    }
                    Column(Modifier.weight(1f)) {
                        info.holidays.forEach { Text(it, color = CHAG_TINT, style = MaterialTheme.typography.bodySmall) }
                        if (info.parsha.isNotBlank()) Text(info.parsha, color = SHABBAT_TINT, style = MaterialTheme.typography.bodySmall)
                        info.candles?.let { Text("🕯 " + timeFmt.format(it), color = SHABBAT_TINT, style = MaterialTheme.typography.bodySmall) }
                        info.havdalah?.let { Text("✨ " + timeFmt.format(it), color = SHABBAT_TINT, style = MaterialTheme.typography.bodySmall) }
                        list.forEach { i -> Text((i.time?.let { timeFmt.format(it) + " " } ?: "") + i.title, color = i.color, style = MaterialTheme.typography.bodyMedium) }
                        if (list.isEmpty() && info.holidays.isEmpty()) Text("—", color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
    }
}

@Composable
private fun AgendaView(from: LocalDate, today: LocalDate, city: ZmanimCity, items: Map<LocalDate, List<CalItem>>, onOpen: (LocalDate) -> Unit) {
    val days = remember(from, items) { (0L..90L).map { from.plusDays(it) }.filter { d -> items[d].orEmpty().isNotEmpty() || JewishDays.info(d, city).holidays.isNotEmpty() } }
    LazyColumn(contentPadding = PaddingValues(12.dp, 4.dp, 12.dp, 96.dp)) {
        if (days.isEmpty()) item { Text(stringResource(R.string.cal_nothing)) }
        items(days) { d ->
            val info = JewishDays.info(d, city)
            Column(Modifier.fillMaxWidth().clickable { onOpen(d) }.padding(vertical = 6.dp)) {
                Text(
                    (if (d == today) stringResource(R.string.cal_today) + " · " else "") + d.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()) +
                        " ${d.dayOfMonth}/${d.monthValue} · " + info.hFull,
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                )
                info.holidays.forEach { Text(it, color = CHAG_TINT) }
                items[d].orEmpty().forEach { i -> Text((i.time?.let { timeFmt.format(it) + "  " } ?: "") + i.title, color = i.color) }
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun YearView(sel: LocalDate, heb: Boolean, today: LocalDate, items: Map<LocalDate, List<CalItem>>, onPickMonth: (LocalDate) -> Unit) {
    val starts = remember(sel, heb) {
        if (heb) {
            val y = JewishCalendar(JewishDays.noon(sel)).jewishYear
            JewishDays.monthsOfYear(y).map { JewishDays.toGregorian(1, it, y) }
        } else (1..12).map { LocalDate.of(sel.year, it, 1) }
    }
    LazyVerticalGrid(GridCells.Fixed(3), contentPadding = PaddingValues(8.dp, 4.dp, 8.dp, 96.dp)) {
        items(starts) { s ->
            val end = if (heb) JewishDays.addHebMonths(s, 1).minusDays(1) else s.withDayOfMonth(s.lengthOfMonth())
            Column(Modifier.padding(4.dp).clip(RoundedCornerShape(8.dp)).clickable { onPickMonth(s) }.padding(4.dp)) {
                Text(if (heb) JewishCalendar(JewishDays.noon(s)).let { JewishDays.monthName(it.jewishMonth, it.jewishYear) } else s.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.getDefault()),
                    fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                val lead = s.dayOfWeek.value % 7
                val cells = List(lead) { null } + (0..ChronoUnit.DAYS.between(s, end)).map { s.plusDays(it) }
                cells.chunked(7).forEach { w ->
                    Row {
                        (0 until 7).forEach { idx ->
                            val d = w.getOrNull(idx)
                            Text(
                                d?.let { if (heb) JewishDays.dayLetters(JewishCalendar(JewishDays.noon(it)).jewishDayOfMonth) else it.dayOfMonth.toString() } ?: "",
                                Modifier.weight(1f).then(if (d != null && items[d].orEmpty().isNotEmpty()) Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)) else Modifier),
                                fontSize = 8.sp, textAlign = TextAlign.Center,
                                color = when { d == today -> MaterialTheme.colorScheme.primary; d?.dayOfWeek == DayOfWeek.SATURDAY -> SHABBAT_TINT; else -> MaterialTheme.colorScheme.onSurface },
                                fontWeight = if (d == today) FontWeight.Bold else null,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================================
// Hebrew date picking: dropdowns, free-text search and conversion both ways
// ============================================================================================

@Composable
fun HebrewDateFields(initial: LocalDate, allowUnknownYear: Boolean = false, onChange: (day: Int, month: Int, year: Int, greg: LocalDate) -> Unit) {
    val j0 = remember(initial) { JewishCalendar(JewishDays.noon(initial)) }
    var year by rememberSaveable { mutableIntStateOf(j0.jewishYear) }
    var month by rememberSaveable { mutableIntStateOf(j0.jewishMonth) }
    var day by rememberSaveable { mutableIntStateOf(j0.jewishDayOfMonth) }
    var query by rememberSaveable { mutableStateOf("") }
    var mOpen by remember { mutableStateOf(false) }
    var dOpen by remember { mutableStateOf(false) }
    val months = JewishDays.monthsOfYear(year)
    val effMonth = if (month in months) month else if (month == 13) 12 else months.first()
    val dim = JewishDays.daysInMonth(effMonth, year)
    val effDay = day.coerceAtMost(dim)
    val greg = JewishDays.toGregorian(effDay, effMonth, year)
    LaunchedEffect(effDay, effMonth, year) { onChange(effDay, effMonth, year, greg) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(query, { q ->
            query = q
            HebrewDateSearch.parse(q, year)?.let { r -> year = r.year; month = r.month; day = r.day.coerceAtMost(JewishDays.daysInMonth(r.month, r.year)) }
        }, label = { Text(stringResource(R.string.cal_heb_search)) }, placeholder = { Text("ט״ו בשבט תשפ״ז") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                OutlinedButton(onClick = { dOpen = true }) { Text(JewishDays.dayLetters(effDay)) }
                DropdownMenu(dOpen, { dOpen = false }) { (1..dim).forEach { d -> DropdownMenuItem(text = { Text(JewishDays.dayLetters(d) + "  ($d)") }, onClick = { day = d; dOpen = false }) } }
            }
            Box {
                OutlinedButton(onClick = { mOpen = true }) { Text(JewishDays.monthName(effMonth, year)) }
                DropdownMenu(mOpen, { mOpen = false }) { months.forEach { m -> DropdownMenuItem(text = { Text(JewishDays.monthName(m, year)) }, onClick = { month = m; mOpen = false }) } }
            }
            IconButton(onClick = { year-- }) { Text("−") }
            Text(JewishDays.yearLetters(year), fontWeight = FontWeight.Bold)
            IconButton(onClick = { year++ }) { Text("+") }
        }
        Text(stringResource(R.string.cal_is_greg, greg.dayOfMonth.toString() + "/" + greg.monthValue + "/" + greg.year + " · " + greg.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())),
            color = MaterialTheme.colorScheme.primary)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HebrewOrGregorianPicker(title: String, initial: LocalDate, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var hebResult by remember { mutableStateOf(initial) }
    val gState = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                TabRow(tab) {
                    Tab(tab == 0, { tab = 0 }, text = { Text(stringResource(R.string.cal_hebrew_date)) })
                    Tab(tab == 1, { tab = 1 }, text = { Text(stringResource(R.string.cal_greg_date)) })
                }
                Spacer(Modifier.height(8.dp))
                if (tab == 0) HebrewDateFields(initial) { _, _, _, g -> hebResult = g }
                else Box(Modifier.heightIn(max = 460.dp)) { DatePicker(gState, title = null, headline = null, showModeToggle = true) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (tab == 0) onPick(hebResult)
                else gState.selectedDateMillis?.let { onPick(java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).toLocalDate()) }
            }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConverterDialog(onDismiss: () -> Unit, onGo: (LocalDate) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var result by remember { mutableStateOf(LocalDate.now(JewishDays.ZONE)) }
    var afterSunset by rememberSaveable { mutableStateOf(false) }
    val gState = rememberDatePickerState()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cal_converter)) },
        text = {
            Column {
                TabRow(tab) {
                    Tab(tab == 0, { tab = 0 }, text = { Text(stringResource(R.string.cal_heb_to_greg)) })
                    Tab(tab == 1, { tab = 1 }, text = { Text(stringResource(R.string.cal_greg_to_heb)) })
                }
                Spacer(Modifier.height(8.dp))
                if (tab == 0) HebrewDateFields(result) { _, _, _, g -> result = g }
                else {
                    Box(Modifier.heightIn(max = 420.dp)) { DatePicker(gState, title = null, headline = null, showModeToggle = true) }
                    val g = gState.selectedDateMillis?.let { java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).toLocalDate() }
                    if (g != null) {
                        LaunchedEffect(g) { result = g }
                        SwitchLine(stringResource(R.string.date_after_sunset), afterSunset) { afterSunset = it }
                        val info = JewishDays.info(if (afterSunset) g.plusDays(1) else g, Cities.byKey("jerusalem"))
                        Text(info.hFull, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onGo(result) }) { Text(stringResource(R.string.cal_show_in_calendar)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

// ============================================================================================
// Editors
// ============================================================================================

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun DateEditDialog(d: FamilyDateEntity, onDismiss: () -> Unit, onSave: (FamilyDateEntity) -> Unit, onDelete: () -> Unit) {
    var title by remember { mutableStateOf(d.title) }
    var person by remember { mutableStateOf(d.personName) }
    var type by remember { mutableStateOf(d.type) }
    var byHebrew by remember { mutableStateOf(d.drive == "HEBREW" && d.hebrewDay > 0 || d.drive == "HEBREW") }
    var gregorian by remember { mutableStateOf(d.gregorian) }
    var afterSunset by remember { mutableStateOf(d.afterSunset) }
    var hDay by remember { mutableIntStateOf(d.hebrewDay) }
    var hMonth by remember { mutableIntStateOf(d.hebrewMonth) }
    var hYear by remember { mutableIntStateOf(d.hebrewYear) }
    var yearKnown by remember { mutableStateOf(d.hebrewYear > 0 || d.gregorian.isNotBlank()) }
    var drive by remember { mutableStateOf(d.drive) }
    var remind by remember { mutableStateOf(d.remindDays.split(',').mapNotNull { it.trim().toIntOrNull() }.toSet()) }
    var note by remember { mutableStateOf(d.note) }
    var picking by remember { mutableStateOf(false) }
    val gDate = gregorian.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val hebFromGreg = gDate?.let { JewishDays.info(if (afterSunset) it.plusDays(1) else it, Cities.byKey("jerusalem")) }
    val initialForHeb = remember { if (d.hebrewDay > 0 && d.hebrewYear > 0) JewishDays.toGregorian(d.hebrewDay, d.hebrewMonth, d.hebrewYear) else gDate ?: LocalDate.now(JewishDays.ZONE) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (d.title.isEmpty()) R.string.cal_add_date else R.string.cal_dates)) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { OutlinedTextField(title, { title = it }, label = { Text(stringResource(R.string.date_title)) }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(person, { person = it }, label = { Text(stringResource(R.string.date_person)) }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                item {
                    Text(stringResource(R.string.date_type), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { dateTypes.forEach { (k, l) -> FilterChip(type == k, { type = k; if (k == "YAHRZEIT") { drive = "HEBREW" } }, { Text(stringResource(l)) }) } }
                }
                item {
                    Text(stringResource(R.string.date_enter_by), style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(!byHebrew, { byHebrew = false }, { Text(stringResource(R.string.cal_greg_date)) })
                        FilterChip(byHebrew, { byHebrew = true; drive = "HEBREW" }, { Text(stringResource(R.string.cal_hebrew_date)) })
                    }
                }
                if (byHebrew) item {
                    HebrewDateFields(initialForHeb) { dd, mm, yy, g -> hDay = dd; hMonth = mm; hYear = yy; gregorian = g.toString(); afterSunset = false }
                    SwitchLine(stringResource(R.string.date_year_known), yearKnown) { yearKnown = it }
                } else item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.date_gregorian) + ": " + (gDate?.let { "${it.dayOfMonth}/${it.monthValue}/${it.year}" } ?: "—"))
                            hebFromGreg?.let { Text(stringResource(R.string.date_hebrew) + ": " + it.hFull, color = MaterialTheme.colorScheme.primary) }
                        }
                        TextButton(onClick = { picking = true }) { Text(stringResource(R.string.date_pick)) }
                    }
                    SwitchLine(stringResource(R.string.date_after_sunset), afterSunset) { afterSunset = it }
                }
                item {
                    Text(stringResource(R.string.date_drive), style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(drive == "GREGORIAN", { drive = "GREGORIAN" }, { Text(stringResource(R.string.date_by_gregorian)) })
                        FilterChip(drive == "HEBREW", { drive = "HEBREW" }, { Text(stringResource(R.string.date_by_hebrew)) })
                    }
                }
                item {
                    Text(stringResource(R.string.date_remind), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(14 to R.string.rem_14, 7 to R.string.rem_7, 3 to R.string.rem_3, 1 to R.string.rem_1, 0 to R.string.rem_0).forEach { (n, l) ->
                            FilterChip(n in remind, { remind = if (n in remind) remind - n else remind + n }, { Text(stringResource(l)) })
                        }
                    }
                }
                item { OutlinedTextField(note, { note = it }, label = { Text(stringResource(R.string.date_gift_note)) }, modifier = Modifier.fillMaxWidth()) }
                if (d.title.isNotEmpty()) item { TextButton(onClick = onDelete) { Text(stringResource(R.string.date_delete), color = MaterialTheme.colorScheme.error) } }
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank() && (gregorian.isNotBlank() || (byHebrew && hDay > 0)), onClick = {
                val saved = if (byHebrew) d.copy(
                    hebrewDay = hDay, hebrewMonth = hMonth, hebrewYear = if (yearKnown) hYear else 0,
                    gregorian = if (yearKnown) gregorian else "", afterSunset = false,
                ) else {
                    val h = gDate?.let { JewishCalendar(JewishDays.noon(if (afterSunset) it.plusDays(1) else it)) }
                    d.copy(gregorian = gregorian, afterSunset = afterSunset, hebrewDay = h?.jewishDayOfMonth ?: 0, hebrewMonth = h?.jewishMonth ?: 0, hebrewYear = h?.jewishYear ?: 0)
                }
                onSave(saved.copy(title = title.trim(), personName = person.trim(), type = type, drive = drive,
                    remindDays = remind.sortedDescending().joinToString(",").ifBlank { "0" }, note = note.trim()))
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
    if (picking) {
        val state = rememberDatePickerState(initialSelectedDateMillis = (gDate ?: LocalDate.now()).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli(), yearRange = 1900..2100)
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { gregorian = java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString() }
                    picking = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(state = state) }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun EventEditDialog(e: EventEntity, onDismiss: () -> Unit, onSave: (EventEntity) -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(e.title) }
    var day by remember { mutableStateOf(JewishDays.toLocal(e.startAt)) }
    fun minutesOf(ms: Long): Int { val c = Calendar.getInstance(JewishDays.TZ).apply { timeInMillis = ms }; return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE) }
    var start by remember { mutableIntStateOf(minutesOf(e.startAt)) }
    var lengthDays by remember { mutableIntStateOf(((JewishDays.toLocal(e.endAt).toEpochDay() - JewishDays.toLocal(e.startAt).toEpochDay()).toInt()).coerceAtLeast(0)) }
    var end by remember { mutableIntStateOf(minutesOf(e.endAt)) }
    var allDay by remember { mutableStateOf(e.allDay) }
    var repeat by remember { mutableStateOf(e.recurrence) }
    var location by remember { mutableStateOf(e.location) }
    var note by remember { mutableStateOf(e.note) }
    var remind by remember { mutableIntStateOf(e.remindMinutes) }
    var picking by remember { mutableStateOf(false) }
    fun hhmm(m: Int) = "%02d:%02d".format(m / 60, m % 60)
    fun pickTime(cur: Int, set: (Int) -> Unit) = TimePickerDialog(context, { _, h, mi -> set(h * 60 + mi) }, cur / 60, cur % 60, true).show()
    val info = JewishDays.info(day, Cities.byKey("jerusalem"))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (e.title.isEmpty()) R.string.cal_add_event else R.string.cal_events)) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { OutlinedTextField(title, { title = it }, label = { Text(stringResource(R.string.event_title)) }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${day.dayOfMonth}/${day.monthValue}/${day.year} · " + day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()))
                            Text(info.hFull, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { picking = true }) { Text(stringResource(R.string.date_pick)) }
                    }
                }
                item { SwitchLine(stringResource(R.string.event_all_day), allDay) { allDay = it } }
                if (!allDay) item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { pickTime(start) { start = it; if (end <= it && lengthDays == 0) end = (it + 60).coerceAtMost(23 * 60 + 59) } }, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.event_start) + " " + hhmm(start))
                        }
                        OutlinedButton(onClick = { pickTime(end) { end = it } }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.event_end) + " " + hhmm(end)) }
                    }
                }
                item {
                    Text(stringResource(R.string.event_days_long), style = MaterialTheme.typography.labelLarge)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { lengthDays = (lengthDays - 1).coerceAtLeast(0) }) { Text("−") }
                        Text(if (lengthDays == 0) stringResource(R.string.event_one_day) else stringResource(R.string.event_n_days, lengthDays + 1))
                        IconButton(onClick = { lengthDays = (lengthDays + 1).coerceAtMost(60) }) { Text("+") }
                    }
                }
                item {
                    Text(stringResource(R.string.event_repeat), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("NONE" to R.string.rep_none, "WEEKLY" to R.string.rep_weekly, "MONTHLY" to R.string.rep_monthly, "YEARLY" to R.string.rep_yearly,
                            "MONTHLY_HEBREW" to R.string.rep_monthly_heb, "YEARLY_HEBREW" to R.string.rep_yearly_heb).forEach { (k, l) ->
                            FilterChip(repeat == k, { repeat = k }, { Text(stringResource(l)) })
                        }
                    }
                }
                item { OutlinedTextField(location, { location = it }, label = { Text(stringResource(R.string.event_location)) }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(note, { note = it }, label = { Text(stringResource(R.string.date_note)) }, modifier = Modifier.fillMaxWidth()) }
                item {
                    Text(stringResource(R.string.event_remind), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0, 15, 60, 180, 1440).forEach { m ->
                            FilterChip(remind == m, { remind = m }, { Text(when (m) { 0 -> stringResource(R.string.rem_none); 1440 -> stringResource(R.string.rem_1); else -> stringResource(R.string.rem_min, m) }) })
                        }
                    }
                }
                if (e.title.isNotEmpty()) item { TextButton(onClick = onDelete) { Text(stringResource(R.string.event_delete), color = MaterialTheme.colorScheme.error) } }
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank(), onClick = {
                val base = JewishDays.startMs(day)
                val s = if (allDay) base else base + start * 60_000L
                val lastDay = JewishDays.startMs(day.plusDays(lengthDays.toLong()))
                val en = if (allDay) lastDay + 86_399_000L else (lastDay + end * 60_000L).let { if (it <= s) s + 3_600_000L else it }
                onSave(e.copy(title = title.trim(), startAt = s, endAt = en, allDay = allDay, recurrence = repeat, location = location.trim(), note = note.trim(), remindMinutes = remind))
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
    if (picking) HebrewOrGregorianPicker(stringResource(R.string.date_pick), day, onDismiss = { picking = false }) { day = it; picking = false }
}


/** 2.2 calendar import wizard: from a file, or from a secret iCal address that stays up to date. */
@Composable
private fun IcsImportWizard(vm: com.rimon.smartfamily.ui.HomeLifeViewModel, viewer: Viewer, onFile: () -> Unit, onDismiss: () -> Unit) {
    var url by remember { mutableStateOf("") }
    var every by remember { mutableStateOf(24) }
    var source by remember { mutableStateOf("google") }
    val test by vm.icsTest.collectAsState()
    var subs by remember { mutableStateOf(vm.icsSubscriptions()) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ics_import)) },
        text = {
            Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onFile, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.icsw_from_file)) }
                Text(stringResource(R.string.icsw_or_link), style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("google" to "Google", "outlook" to "Outlook", "other" to stringResource(R.string.icsw_other)).forEach { (k, l) ->
                        androidx.compose.material3.FilterChip(selected = source == k, onClick = { source = k }, label = { Text(l) })
                    }
                }
                Text(stringResource(when (source) { "google" -> R.string.icsw_google; "outlook" -> R.string.icsw_outlook; else -> R.string.icsw_other_body }), style = MaterialTheme.typography.bodySmall)
                when (source) {
                    "google" -> com.rimon.smartfamily.ui.components.OpenSiteButton(stringResource(R.string.icsw_open_settings), "https://calendar.google.com/calendar/r/settings")
                    "outlook" -> com.rimon.smartfamily.ui.components.OpenSiteButton(stringResource(R.string.icsw_open_settings), "https://outlook.live.com/calendar/0/options/calendar/SharedCalendars")
                }
                OutlinedTextField(url, { url = it.trim() }, label = { Text(stringResource(R.string.icsw_address)) }, modifier = Modifier.fillMaxWidth())
                OutlinedButton(enabled = url.length > 10, onClick = { vm.testIcsUrl(url) }) { Text(stringResource(R.string.ai_test)) }
                when {
                    test == "…" -> com.rimon.smartfamily.ui.components.TestResult(null, stringResource(R.string.nc_sending))
                    test?.startsWith("ok:") == true -> com.rimon.smartfamily.ui.components.TestResult(true, stringResource(R.string.icsw_ok, test!!.removePrefix("ok:")))
                    test == "err:offline" -> com.rimon.smartfamily.ui.components.TestResult(false, stringResource(R.string.ai_check_offline))
                    test == "err:https" -> com.rimon.smartfamily.ui.components.TestResult(false, stringResource(R.string.icsw_https))
                    test?.startsWith("err:") == true -> com.rimon.smartfamily.ui.components.TestResult(false, stringResource(R.string.icsw_bad))
                }
                Text(stringResource(R.string.icsw_every), style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(6, 24, 168).forEach { h ->
                        androidx.compose.material3.FilterChip(selected = every == h, onClick = { every = h }, label = { Text(stringResource(when (h) { 6 -> R.string.icsw_6h; 24 -> R.string.icsw_day; else -> R.string.icsw_week })) })
                    }
                }
                if (subs.isNotEmpty()) {
                    Text(stringResource(R.string.icsw_current), style = MaterialTheme.typography.titleSmall)
                    subs.forEach { sub ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(runCatching { android.net.Uri.parse(sub.url).host.orEmpty() }.getOrDefault("") + " · …" + sub.url.takeLast(6), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { vm.removeIcs(sub.url); subs = vm.icsSubscriptions() }) { Text(stringResource(R.string.delete)) }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = test?.startsWith("ok:") == true, onClick = { vm.subscribeIcs(url, viewer.familyId, viewer.userId, every); onDismiss() }) { Text(stringResource(R.string.icsw_import)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
