package com.rimon.smartfamily.ui.components

import androidx.compose.foundation.background
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material.icons.outlined.LocalLaundryService
import androidx.compose.material.icons.outlined.Luggage
import androidx.compose.material.icons.outlined.LunchDining
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Poll
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.material.icons.outlined.Diversity3
import androidx.compose.material.icons.outlined.ChildCare
import androidx.compose.material.icons.outlined.PriceCheck
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.TipsAndUpdates
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rimon.smartfamily.R
import com.rimon.smartfamily.model.AppModule
import com.rimon.smartfamily.ui.theme.ModuleTints

fun AppModule.icon(): ImageVector = when (this) {
    AppModule.SHOPPING -> Icons.Outlined.ShoppingCart
    AppModule.PRICES -> Icons.Outlined.PriceCheck
    AppModule.INVENTORY -> Icons.Outlined.Kitchen
    AppModule.APPLIANCES -> Icons.Outlined.LocalLaundryService
    AppModule.RECIPES -> Icons.Outlined.MenuBook
    AppModule.PACKED_MEALS -> Icons.Outlined.LunchDining
    AppModule.CHORES -> Icons.Outlined.CleaningServices
    AppModule.TRAVEL -> Icons.Outlined.Luggage
    AppModule.CHAT -> Icons.Outlined.ChatBubbleOutline
    AppModule.CALENDAR -> Icons.Outlined.CalendarMonth
    AppModule.BUDGET -> Icons.Outlined.AccountBalanceWallet
    AppModule.HOME_BOOK -> Icons.Outlined.Inventory2
    AppModule.KNOWLEDGE -> Icons.Outlined.TipsAndUpdates
    AppModule.POLLS -> Icons.Outlined.Poll
    AppModule.KIDS -> Icons.Outlined.ChildCare
    AppModule.FAMILY -> Icons.Outlined.Diversity3
    AppModule.SHABBAT -> Icons.Outlined.WbTwilight
}

fun AppModule.tint(): Color = ModuleTints.forIndex(ordinal)

@Composable
fun ModuleBadge(module: AppModule, size: Dp = 44.dp) {
    val tint = module.tint()
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size / 3))
            .background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(module.icon(), contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.55f))
    }
}

@Composable
fun Avatar(name: String, size: Dp = 40.dp, color: Color = MaterialTheme.colorScheme.primary) {
    Box(
        modifier = Modifier.size(size).clip(CircleShape).background(color.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.trim().take(1).uppercase().ifEmpty { "?" },
            color = color,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = 20.dp, bottom = 8.dp),
    )
}

/** UX: every screen has a Help button in its top bar. */
@Composable
fun HelpButton(textRes: Int) {
    // UX: the first time a module opens its help shows by itself (a short, skippable intro)
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("sf_tips", android.content.Context.MODE_PRIVATE) }
    val key = remember(textRes) { runCatching { context.resources.getResourceEntryName(textRes) }.getOrDefault(textRes.toString()) }
    var open by remember { mutableStateOf(!prefs.getBoolean(key, false) && !prefs.getBoolean("off", false)) }
    androidx.compose.runtime.LaunchedEffect(open) { if (open) prefs.edit().putBoolean(key, true).apply() }
    IconButton(onClick = { open = true }) {
        Icon(Icons.Outlined.HelpOutline, contentDescription = stringResource(R.string.help))
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.close)) } },
            dismissButton = { TextButton(onClick = { prefs.edit().putBoolean("off", true).apply(); open = false }) { Text(stringResource(R.string.tips_off)) } },
            title = { Text(stringResource(R.string.help)) },
            text = { Text(stringResource(textRes)) },
        )
    }
}

@Composable
fun InfoRow(title: String, subtitle: String? = null, leading: @Composable (() -> Unit)? = null, trailing: @Composable (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        trailing?.invoke()
    }
}


/** A short confetti burst for finished lists and completed boards (UX-5), drawn on the phone. */
@Composable
fun Celebration(trigger: Any?, modifier: Modifier = Modifier) {
    if (trigger == null) return
    val anim = remember(trigger) { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(trigger) { anim.snapTo(0f); anim.animateTo(1f, androidx.compose.animation.core.tween(2200)) }
    if (anim.value >= 1f) return
    val colors = listOf(Color(0xFFE53935), Color(0xFFFDD835), Color(0xFF43A047), Color(0xFF1E88E5), Color(0xFF8E24AA), Color(0xFFFB8C00))
    val parts = remember(trigger) { List(80) { Triple(Math.random().toFloat(), Math.random().toFloat() * 0.6f + 0.4f, colors[it % colors.size]) } }
    androidx.compose.foundation.Canvas(modifier.fillMaxSize()) {
        val t = anim.value
        parts.forEachIndexed { i, (x, speed, c) ->
            val px = size.width * (x + 0.15f * kotlin.math.sin((t * 6 + i).toDouble()).toFloat())
            val py = size.height * (t * speed * 1.4f) - 40f
            drawCircle(c.copy(alpha = 1f - t * 0.6f), radius = 9f + (i % 4) * 2f, center = androidx.compose.ui.geometry.Offset(px, py))
        }
    }
}

// ---------------- 2.1 navigation building blocks ----------------

/** True when a screen is shown inside an area (hub) that already has its own title bar and section tabs. */
val LocalEmbedded = androidx.compose.runtime.compositionLocalOf { false }

/** When an area shows one tab of a multi-tab screen as its own section, the screen hides its tab row. */
val LocalFixedTab = androidx.compose.runtime.compositionLocalOf<Int?> { null }

/** The family chat entry shown at the top of every screen (when the user chose "icon on every screen"). */
data class ChatEntry(val show: Boolean = false, val unread: Int = 0, val open: () -> Unit = {})
val LocalChatEntry = androidx.compose.runtime.compositionLocalOf { ChatEntry() }

@Composable
fun ChatTopIcon() {
    val e = LocalChatEntry.current
    if (!e.show) return
    androidx.compose.material3.IconButton(onClick = e.open) {
        androidx.compose.material3.BadgedBox(badge = { if (e.unread > 0) androidx.compose.material3.Badge { Text(if (e.unread > 99) "99+" else e.unread.toString()) } }) {
            Icon(androidx.compose.material.icons.Icons.Outlined.ChatBubbleOutline, stringResource(R.string.mod_chat))
        }
    }
}

/**
 * The app's top bar. On its own it is a normal bar with the chat icon added; inside an area it shrinks to just the
 * screen's own action buttons (the area shows the title, back is not needed).
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SfTopAppBar(
    title: @Composable () -> Unit,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
) {
    if (LocalEmbedded.current) {
        androidx.compose.foundation.layout.Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            content = actions,
        )
    } else {
        androidx.compose.material3.TopAppBar(title = title, navigationIcon = navigationIcon, actions = { actions(); ChatTopIcon() })
    }
}

/**
 * One checklist row for every kind of list (shopping, trip packing, kits, Shabbat preparation, holiday packs):
 * tap or swipe right to tick, swipe left to delete with a few seconds to undo, the same look everywhere.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ChecklistRow(
    title: String, checked: Boolean, onToggle: () -> Unit,
    subtitle: String? = null, subtitleIsWarning: Boolean = false,
    onDelete: (() -> Unit)? = null, trailing: @Composable () -> Unit = {},
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    var pendingDelete by remember { mutableStateOf(false) }
    if (pendingDelete && onDelete != null) {
        androidx.compose.runtime.LaunchedEffect(Unit) { kotlinx.coroutines.delay(4000); onDelete() }
        androidx.compose.foundation.layout.Row(
            Modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.list_deleted, title), Modifier.weight(1f), style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
            androidx.compose.material3.TextButton(onClick = { pendingDelete = false }) { Text(stringResource(R.string.undo)) }
        }
        return
    }
    val toggle = { haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress); onToggle() }
    val state = androidx.compose.material3.rememberSwipeToDismissBoxState(confirmValueChange = { v ->
        when (v) {
            androidx.compose.material3.SwipeToDismissBoxValue.StartToEnd -> { toggle(); false }
            androidx.compose.material3.SwipeToDismissBoxValue.EndToStart -> { if (onDelete != null) pendingDelete = true; false }
            else -> false
        }
    })
    androidx.compose.material3.SwipeToDismissBox(
        state, enableDismissFromEndToStart = onDelete != null,
        backgroundContent = {
            val del = state.dismissDirection == androidx.compose.material3.SwipeToDismissBoxValue.EndToStart
            androidx.compose.foundation.layout.Row(
                Modifier.fillMaxSize().background(if (del) androidx.compose.material3.MaterialTheme.colorScheme.errorContainer else androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer).padding(horizontal = 16.dp),
                horizontalArrangement = if (del) androidx.compose.foundation.layout.Arrangement.End else androidx.compose.foundation.layout.Arrangement.Start,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) { Icon(if (del) androidx.compose.material.icons.Icons.Outlined.Delete else androidx.compose.material.icons.Icons.Outlined.CheckCircle, null) }
        },
    ) {
        androidx.compose.foundation.layout.Row(
            Modifier.fillMaxWidth().background(androidx.compose.material3.MaterialTheme.colorScheme.surface).clickable { toggle() }.padding(vertical = 2.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            androidx.compose.material3.Checkbox(checked, { toggle() })
            androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                Text(title, textDecoration = if (checked) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                    color = if (checked) androidx.compose.material3.MaterialTheme.colorScheme.outline else Color.Unspecified)
                if (!subtitle.isNullOrBlank()) Text(subtitle, style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    color = if (subtitleIsWarning) androidx.compose.material3.MaterialTheme.colorScheme.error else androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
            }
            trailing()
        }
    }
}

/** The progress line every checklist shares: "5 of 8", a bar, and "start again". Confetti when everything is ticked. */
@Composable
fun ChecklistHeader(done: Int, total: Int, onReset: (() -> Unit)?) {
    if (total == 0) return
    androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth()) {
        androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(stringResource(R.string.trip_progress, done, total), Modifier.weight(1f), style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
            if (onReset != null && done > 0) androidx.compose.material3.TextButton(onClick = onReset) { Text(stringResource(R.string.trip_unpack_all)) }
        }
        androidx.compose.material3.LinearProgressIndicator(progress = { done / total.toFloat() }, modifier = Modifier.fillMaxWidth())
        if (done == total) androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().height(120.dp)) { Celebration(total) }
    }
}
