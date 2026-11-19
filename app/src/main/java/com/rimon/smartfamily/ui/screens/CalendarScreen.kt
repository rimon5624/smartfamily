package com.rimon.smartfamily.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rimon.smartfamily.R
import com.rimon.smartfamily.data.db.EventEntity
import com.rimon.smartfamily.data.db.FamilyDateEntity
import com.rimon.smartfamily.smart.Zmanim
import com.rimon.smartfamily.ui.HomeLifeViewModel
import com.rimon.smartfamily.ui.components.HelpButton
import com.rimon.smartfamily.ui.components.SectionTitle
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

private val dateTypes = listOf(
    "BIRTHDAY" to R.string.dt_birthday, "ANNIVERSARY" to R.string.dt_anniversary, "YAHRZEIT" to R.string.dt_yahrzeit,
    "BAR_MITZVAH" to R.string.dt_barmitzvah, "ALIYAH" to R.string.dt_aliyah, "CUSTOM" to R.string.dt_custom,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(vm: HomeLifeViewModel, viewer: Viewer, onBack: () -> Unit) {
    val dates by remember(viewer.familyId) { vm.dates(viewer.familyId) }.collectAsState(initial = emptyList())
    val events by remember(viewer.familyId) { vm.events(viewer.familyId) }.collectAsState(initial = emptyList())
    var month by rememberSaveable { mutableLongStateOf(monthStart(System.currentTimeMillis())) }
    var selected by rememberSaveable { mutableLongStateOf(startOfDay()) }
    var fabMenu by remember { mutableStateOf(false) }
    var editDate by remember { mutableStateOf<FamilyDateEntity?>(null) }
    var editEvent by remember { mutableStateOf<EventEntity?>(null) }

    val monthEnd = Calendar.getInstance().apply { timeInMillis = month; add(Calendar.MONTH, 1) }.timeInMillis
    val occurrences = remember(events, month) { vm.expand(events, month - 7 * 86_400_000L, monthEnd + 7 * 86_400_000L) }
    val dateOcc = remember(dates, month) {
        // Family dates that fall in the visible month (this year's occurrence of each).
        dates.mapNotNull { d ->
            val o = vm.nextOccurrence(d) ?: return@mapNotNull null
            listOf(o).filter { it.on.time in month until monthEnd }.firstOrNull()
        }
    }
    val upcoming = remember(dates) { dates.mapNotNull { vm.nextOccurrence(it) }.filter { it.daysAway <= 60 }.sortedBy { it.daysAway } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.cal_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) } },
                actions = {
                    TextButton(onClick = { month = monthStart(System.currentTimeMillis()); selected = startOfDay() }) { Text(stringResource(R.string.cal_today)) }
                    HelpButton(R.string.help_calendar)
                },
            )
        },
        floatingActionButton = {
            if (!viewer.isChild) {
                Box {
                    FloatingActionButton(onClick = { fabMenu = true }) { Icon(Icons.Outlined.Add, null) }
                    DropdownMenu(expanded = fabMenu, onDismissRequest = { fabMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.cal_add_date)) }, leadingIcon = { Icon(Icons.Outlined.Cake, null) },
                            onClick = { fabMenu = false; editDate = FamilyDateEntity(id = UUID.randomUUID().toString(), familyId = viewer.familyId, title = "") },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.cal_add_event)) }, leadingIcon = { Icon(Icons.Outlined.Event, null) },
                            onClick = {
                                fabMenu = false
                                val start = selected + 18 * 3_600_000L
                                editEvent = EventEntity(id = UUID.randomUUID().toString(), familyId = viewer.familyId, title = "", startAt = start, endAt = start + 3_600_000L, ownerId = viewer.userId)
                            },
                        )
                    }
                }
            }
        },
    ) { inner ->
        LazyColumn(contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = inner.calculateTopPadding(), bottom = 96.dp)) {
            item {
                MonthHeader(month, onPrev = { month = addMonths(month, -1) }, onNext = { month = addMonths(month, 1) })
                MonthGrid(
                    month = month,
                    selected = selected,
                    marks = { day ->
                        val end = day + 86_400_000L
                        occurrences.any { it.start.time < end && it.end.time >= day } to dateOcc.any { it.on.time in day until end }
                    },
                    onSelect = { selected = it },
                )
            }
            // The selected day
            item {
                val d = Date(selected)
                Spacer(Modifier.size(8.dp))
                Text(DateFormat.getDateInstance(DateFormat.FULL).format(d), style = MaterialTheme.typography.titleMedium)
                Text(Zmanim.hebrewDate(d), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                Zmanim.holidayName(d).takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary) }
            }
            val dayEnd = selected + 86_400_000L
            val todaysDates = dateOcc.filter { it.on.time in selected until dayEnd }
            val todaysEvents = occurrences.filter { it.start.time < dayEnd && it.end.time >= selected }
            if (todaysDates.isEmpty() && todaysEvents.isEmpty()) {
                item { Text(stringResource(R.string.cal_nothing), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp)) }
            }
            items(todaysDates, key = { "d" + it.date.id }) { o ->
                DateCard(o.date.title, o.years, o.daysAway) { if (!viewer.isChild) editDate = o.date }
            }
            items(todaysEvents, key = { "e" + it.event.id + it.start.time }) { o ->
                val t = DateFormat.getTimeInstance(DateFormat.SHORT)
                Card(
                    onClick = { if (!viewer.isChild) editEvent = o.event },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(o.event.title, style = MaterialTheme.typography.titleSmall)
                        Text(
                            (if (o.event.allDay) stringResource(R.string.event_all_day) else t.format(o.start) + "–" + t.format(o.end)) +
                                if (o.event.location.isNotBlank()) " · " + o.event.location else "",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            item { SectionTitle(stringResource(R.string.cal_upcoming)) }
            if (dates.isEmpty()) item { Text(stringResource(R.string.cal_no_dates), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(upcoming, key = { "u" + it.date.id }) { o ->
                DateCard(o.date.title, o.years, o.daysAway) { if (!viewer.isChild) editDate = o.date }
            }
        }
    }

    editDate?.let { d -> DateEditDialog(d, onDismiss = { editDate = null }, onSave = { vm.saveDate(it); editDate = null }, onDelete = { vm.deleteDate(d.id); editDate = null }) }
    editEvent?.let { e -> EventEditDialog(e, onDismiss = { editEvent = null }, onSave = { vm.saveEvent(it); editEvent = null }, onDelete = { vm.deleteEvent(e.id); editEvent = null }) }
}

@Composable
private fun DateCard(title: String, years: Int?, daysAway: Int, onClick: () -> Unit) {
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Cake, null)
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                val whenText = when (daysAway) {
                    0 -> stringResource(R.string.date_today)
                    1 -> stringResource(R.string.date_tomorrow)
                    else -> stringResource(R.string.date_in_days, daysAway)
                }
                Text(whenText + (years?.let { " · " + stringResource(R.string.date_years, it) } ?: ""), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun monthStart(ms: Long) = Calendar.getInstance().apply {
    timeInMillis = ms; set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun addMonths(ms: Long, n: Int) = Calendar.getInstance().apply { timeInMillis = ms; add(Calendar.MONTH, n) }.timeInMillis

@Composable
private fun MonthHeader(month: Long, onPrev: () -> Unit, onNext: () -> Unit) {
    val d = Date(month)
    val last = Date(addMonths(month, 1) - 86_400_000L)
    val hebrew = listOf(Zmanim.hebrewMonthName(d), Zmanim.hebrewMonthName(last)).distinct().joinToString("–")
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        IconButton(onClick = onPrev) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, null) }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(SimpleDateFormat("LLLL yyyy", Locale.getDefault()).format(d), style = MaterialTheme.typography.titleLarge)
            Text(hebrew, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        IconButton(onClick = onNext) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null) }
    }
}

private val weekdayLabels = listOf(R.string.wd_sun, R.string.wd_mon, R.string.wd_tue, R.string.wd_wed, R.string.wd_thu, R.string.wd_fri, R.string.wd_sat)

@Composable
private fun MonthGrid(month: Long, selected: Long, marks: (Long) -> Pair<Boolean, Boolean>, onSelect: (Long) -> Unit) {
    val first = Calendar.getInstance().apply { timeInMillis = month }
    val offset = first.get(Calendar.DAY_OF_WEEK) - 1
    val days = first.getActualMaximum(Calendar.DAY_OF_MONTH)
    val today = startOfDay()
    val cs = MaterialTheme.colorScheme
    Column {
        Row {
            weekdayLabels.forEach { Text(stringResource(it), Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant) }
        }
        val cells = offset + days
        val rows = (cells + 6) / 7
        for (r in 0 until rows) {
            Row {
                for (col in 0 until 7) {
                    val idx = r * 7 + col
                    val dayNum = idx - offset + 1
                    Box(Modifier.weight(1f).aspectRatio(0.86f).padding(2.dp)) {
                        if (dayNum in 1..days) {
                            val dayMs = Calendar.getInstance().apply { timeInMillis = month; set(Calendar.DAY_OF_MONTH, dayNum) }.timeInMillis
                            val date = Date(dayMs + 12 * 3_600_000L)
                            val isSel = dayMs == selected
                            val isToday = dayMs == today
                            val holiday = Zmanim.holidayName(date).isNotBlank()
                            val (hasEvent, hasDate) = marks(dayMs)
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSel) cs.primaryContainer else if (col == 6 || holiday) cs.surfaceContainer else cs.surface)
                                    .then(if (isToday) Modifier.border(1.5.dp, cs.primary, RoundedCornerShape(12.dp)) else Modifier)
                                    .clickable { onSelect(dayMs) }
                                    .padding(vertical = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(dayNum.toString(), style = MaterialTheme.typography.titleSmall, fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium)
                                Text(Zmanim.hebrewDayLetters(date), style = MaterialTheme.typography.labelSmall, color = if (holiday) cs.tertiary else cs.onSurfaceVariant)
                                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                    if (hasEvent) Box(Modifier.size(5.dp).clip(CircleShape).background(cs.secondary))
                                    if (hasDate) Box(Modifier.size(5.dp).clip(CircleShape).background(cs.tertiary))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private val ymd = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("Asia/Jerusalem") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateEditDialog(d: FamilyDateEntity, onDismiss: () -> Unit, onSave: (FamilyDateEntity) -> Unit, onDelete: () -> Unit) {
    var title by remember { mutableStateOf(d.title) }
    var type by remember { mutableStateOf(d.type) }
    var gregorian by remember { mutableStateOf(d.gregorian) }
    var afterSunset by remember { mutableStateOf(d.afterSunset) }
    var drive by remember { mutableStateOf(d.drive) }
    var remind by remember { mutableStateOf(d.remindDays) }
    var note by remember { mutableStateOf(d.note) }
    var picking by remember { mutableStateOf(false) }
    val hebrewPreview = gregorian.takeIf { it.isNotBlank() }?.let { g -> ymd.parse(g)?.let { date ->
        val c = Calendar.getInstance().apply { time = date; add(Calendar.HOUR_OF_DAY, 12); if (afterSunset) add(Calendar.DAY_OF_YEAR, 1) }
        Zmanim.hebrewDate(c.time)
    } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (d.title.isEmpty()) R.string.cal_add_date else R.string.cal_dates)) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { OutlinedTextField(title, { title = it }, label = { Text(stringResource(R.string.date_title)) }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                item { Text(stringResource(R.string.date_type), style = MaterialTheme.typography.labelLarge); ChipRow(dateTypes, type) { type = it } }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.date_gregorian) + ": " + gregorian.ifBlank { "—" })
                            hebrewPreview?.let { Text(stringResource(R.string.date_hebrew) + ": " + it, color = MaterialTheme.colorScheme.primary) }
                        }
                        TextButton(onClick = { picking = true }) { Text(stringResource(R.string.date_pick)) }
                    }
                }
                item { SwitchLine(stringResource(R.string.date_after_sunset), afterSunset) { afterSunset = it } }
                item {
                    Text(stringResource(R.string.date_drive), style = MaterialTheme.typography.labelLarge)
                    ChipRow(listOf("GREGORIAN" to R.string.date_by_gregorian, "HEBREW" to R.string.date_by_hebrew), drive) { drive = it }
                }
                item { OutlinedTextField(remind, { remind = it }, label = { Text(stringResource(R.string.date_remind)) }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(note, { note = it }, label = { Text(stringResource(R.string.date_note)) }, modifier = Modifier.fillMaxWidth()) }
                if (d.title.isNotEmpty()) item { TextButton(onClick = onDelete) { Text(stringResource(R.string.date_delete), color = MaterialTheme.colorScheme.error) } }
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank() && gregorian.isNotBlank(), onClick = {
                // Recompute the Hebrew date whenever the Gregorian date or sunset flag changed.
                val reset = gregorian != d.gregorian || afterSunset != d.afterSunset
                onSave(
                    d.copy(
                        title = title.trim(), type = type, gregorian = gregorian, afterSunset = afterSunset, drive = drive,
                        remindDays = remind.ifBlank { "7,1,0" }, note = note,
                        hebrewDay = if (reset) 0 else d.hebrewDay, hebrewMonth = if (reset) 0 else d.hebrewMonth, hebrewYear = if (reset) 0 else d.hebrewYear,
                    )
                )
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
    if (picking) {
        val initial = gregorian.takeIf { it.isNotBlank() }?.let { ymd.parse(it)?.time?.plus(12 * 3_600_000L) } ?: System.currentTimeMillis()
        val state = rememberDatePickerState(initialSelectedDateMillis = initial, yearRange = 1900..2100)
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        gregorian = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(ms))
                    }
                    picking = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(state = state) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventEditDialog(e: EventEntity, onDismiss: () -> Unit, onSave: (EventEntity) -> Unit, onDelete: () -> Unit) {
    var title by remember { mutableStateOf(e.title) }
    var day by remember { mutableLongStateOf(startOfDay(e.startAt)) }
    val tf = SimpleDateFormat("HH:mm", Locale.US)
    var start by remember { mutableStateOf(tf.format(Date(e.startAt))) }
    var end by remember { mutableStateOf(tf.format(Date(e.endAt))) }
    var allDay by remember { mutableStateOf(e.allDay) }
    var repeat by remember { mutableStateOf(e.recurrence) }
    var location by remember { mutableStateOf(e.location) }
    var remind by remember { mutableStateOf(e.remindMinutes.toString()) }
    var picking by remember { mutableStateOf(false) }
    fun at(hhmm: String): Long {
        val p = hhmm.split(':')
        val h = p.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 0
        val m = p.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
        return day + h * 3_600_000L + m * 60_000L
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (e.title.isEmpty()) R.string.cal_add_event else R.string.cal_events)) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { OutlinedTextField(title, { title = it }, label = { Text(stringResource(R.string.event_title)) }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(DateFormat.getDateInstance().format(Date(day)) + " · " + Zmanim.hebrewDate(Date(day + 43_200_000L)), Modifier.weight(1f))
                        TextButton(onClick = { picking = true }) { Text(stringResource(R.string.date_pick)) }
                    }
                }
                item { SwitchLine(stringResource(R.string.event_all_day), allDay) { allDay = it } }
                if (!allDay) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(start, { start = it }, label = { Text(stringResource(R.string.event_start)) }, singleLine = true, modifier = Modifier.weight(1f))
                            OutlinedTextField(end, { end = it }, label = { Text(stringResource(R.string.event_end)) }, singleLine = true, modifier = Modifier.weight(1f))
                        }
                    }
                }
                item {
                    Text(stringResource(R.string.event_repeat), style = MaterialTheme.typography.labelLarge)
                    ChipRow(listOf("NONE" to R.string.rep_none, "WEEKLY" to R.string.rep_weekly, "MONTHLY" to R.string.rep_monthly, "YEARLY" to R.string.rep_yearly), repeat) { repeat = it }
                }
                item { OutlinedTextField(location, { location = it }, label = { Text(stringResource(R.string.event_location)) }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                item { NumberField(stringResource(R.string.event_remind), remind) { remind = it } }
                if (e.title.isNotEmpty()) item { TextButton(onClick = onDelete) { Text(stringResource(R.string.event_delete), color = MaterialTheme.colorScheme.error) } }
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank(), onClick = {
                val s = if (allDay) day else at(start)
                val en = if (allDay) day + 86_399_000L else at(end).let { if (it <= s) s + 3_600_000L else it }
                onSave(e.copy(title = title.trim(), startAt = s, endAt = en, allDay = allDay, recurrence = repeat, location = location.trim(), remindMinutes = remind.toIntOrNull() ?: 60))
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
    if (picking) {
        val state = rememberDatePickerState(initialSelectedDateMillis = day + 43_200_000L)
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = ms }
                        day = Calendar.getInstance().apply {
                            set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH), 0, 0, 0); set(Calendar.MILLISECOND, 0)
                        }.timeInMillis
                    }
                    picking = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(state = state) }
    }
}
