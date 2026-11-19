package com.rimon.smartfamily.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rimon.smartfamily.data.prefs.ThemeMode

// Brand: pomegranate red with a warm neutral base and a teal accent.
private val Pomegranate = Color(0xFF9C2B3A)
private val PomegranateDark = Color(0xFFFFB3B8)
private val Teal = Color(0xFF00696E)
private val TealDark = Color(0xFF4FD8DE)

private val LightColors = lightColorScheme(
    primary = Pomegranate,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDADC),
    onPrimaryContainer = Color(0xFF40000D),
    secondary = Teal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCF2F3),
    onSecondaryContainer = Color(0xFF002021),
    tertiary = Color(0xFF7A5900),
    tertiaryContainer = Color(0xFFFFDEA6),
    background = Color(0xFFFFFBF8),
    surface = Color(0xFFFFFBF8),
    surfaceVariant = Color(0xFFF4DDDE),
    surfaceContainerLow = Color(0xFFFFF4F2),
    surfaceContainer = Color(0xFFFCEDEB),
    surfaceContainerHigh = Color(0xFFF6E7E5),
    outline = Color(0xFF857374),
)

private val DarkColors = darkColorScheme(
    primary = PomegranateDark,
    onPrimary = Color(0xFF67001C),
    primaryContainer = Color(0xFF7E1426),
    onPrimaryContainer = Color(0xFFFFDADC),
    secondary = TealDark,
    onSecondary = Color(0xFF003739),
    secondaryContainer = Color(0xFF004F53),
    onSecondaryContainer = Color(0xFFCCF2F3),
    tertiary = Color(0xFFF5BF48),
    tertiaryContainer = Color(0xFF5C4200),
    background = Color(0xFF1A1112),
    surface = Color(0xFF1A1112),
    surfaceVariant = Color(0xFF524344),
    surfaceContainerLow = Color(0xFF231819),
    surfaceContainer = Color(0xFF271C1D),
    surfaceContainerHigh = Color(0xFF322627),
    outline = Color(0xFFA08C8D),
)

private val AppTypography = Typography().run {
    copy(
        displaySmall = displaySmall.copy(fontWeight = FontWeight.SemiBold),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
        bodyLarge = bodyLarge.copy(lineHeight = 24.sp),
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun SmartFamilyTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, typography = AppTypography, shapes = AppShapes, content = content)
}

/** Accent colours used for module cards, one per module family. */
object ModuleTints {
    val palette = listOf(
        Color(0xFF9C2B3A), Color(0xFF00696E), Color(0xFF7A5900), Color(0xFF4A5C92),
        Color(0xFF6B5778), Color(0xFF3B6939), Color(0xFF8C4A2F),
    )
    fun forIndex(i: Int) = palette[i % palette.size]
}

