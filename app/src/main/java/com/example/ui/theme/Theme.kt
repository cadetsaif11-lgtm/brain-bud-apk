package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = CobaltPrimary,
    onPrimary = Color.White,
    primaryContainer = CobaltContainer,
    onPrimaryContainer = OnCobaltContainer,
    secondary = TealSecondary,
    onSecondary = Color.White,
    secondaryContainer = TealContainer,
    onSecondaryContainer = OnTealContainer,
    tertiary = AmberGold,
    onTertiary = Color.White,
    tertiaryContainer = AmberContainer,
    onTertiaryContainer = OnAmberContainer,
    background = WarmIvoryBackground,
    onBackground = InkDark,
    surface = WarmPaperSurface,
    onSurface = InkDark,
    surfaceVariant = WarmSandVariant,
    onSurfaceVariant = InkMuted,
    outline = WarmBorder,
    outlineVariant = Color(0xFFE7E3DA),
    error = CoralWeak,
    onError = Color.White,
    errorContainer = CoralWeakContainer,
    onErrorContainer = Color(0xFF881337)
)

private val DarkColorScheme = darkColorScheme(
    primary = CobaltPrimaryLight,
    onPrimary = Color(0xFF0A1B6E),
    primaryContainer = CobaltContainerDark,
    onPrimaryContainer = OnCobaltContainerDark,
    secondary = TealSecondaryLight,
    onSecondary = Color(0xFF042F2E),
    secondaryContainer = TealContainerDark,
    onSecondaryContainer = OnTealContainerDark,
    tertiary = AmberGoldBright,
    onTertiary = Color(0xFF451A03),
    tertiaryContainer = AmberContainerDark,
    onTertiaryContainer = OnAmberContainerDark,
    background = MidnightBackground,
    onBackground = InkLight,
    surface = MidnightSurface,
    onSurface = InkLight,
    surfaceVariant = MidnightSurfaceHigh,
    onSurfaceVariant = InkLightMuted,
    outline = MidnightBorder,
    outlineVariant = Color(0xFF232B3B),
    error = Color(0xFFFF7A90),
    onError = Color(0xFF4C0519),
    errorContainer = CoralWeakDarkContainer,
    onErrorContainer = Color(0xFFFFCCD5)
)

val BrainBuddyShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun BrainBuddyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = BrainBuddyTypography,
        shapes = BrainBuddyShapes,
        content = content
    )
}
