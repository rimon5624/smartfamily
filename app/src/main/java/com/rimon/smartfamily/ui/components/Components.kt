package com.rimon.smartfamily.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
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
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = stringResource(R.string.help))
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.close)) } },
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
