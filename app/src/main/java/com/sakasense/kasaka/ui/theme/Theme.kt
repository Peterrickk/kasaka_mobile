package com.sakasense.kasaka.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sakasense.kasaka.data.ThemeMode

private val DarkScheme = darkColorScheme(
    primary = Brand.Primary,
    onPrimary = Brand.OnPrimary,
    primaryContainer = Color(0xFF0F3322),
    onPrimaryContainer = Brand.PrimaryGlow,
    secondary = Brand.Accent,
    onSecondary = Brand.OnPrimary,
    secondaryContainer = Brand.Surface2,
    onSecondaryContainer = Brand.Text,
    tertiary = Brand.Info,
    background = Brand.Bg,
    onBackground = Brand.Text,
    surface = Brand.Bg,
    onSurface = Brand.Text,
    surfaceVariant = Brand.Surface2,
    onSurfaceVariant = Brand.TextMuted,
    surfaceContainerLowest = Brand.Bg,
    surfaceContainerLow = Color(0xFF08110C),
    surfaceContainer = Brand.Surface,
    surfaceContainerHigh = Brand.Surface2,
    surfaceContainerHighest = Color(0xFF172B21),
    outline = Color(0xFF2C4537),
    outlineVariant = Color(0xFF1A2E23),
    error = Brand.Danger,
    onError = Brand.OnPrimary,
    inverseSurface = Brand.Text,
    inverseOnSurface = Brand.Bg,
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF0E9F55),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3F6E2),
    onPrimaryContainer = Color(0xFF04331B),
    secondary = Color(0xFF5E8C0F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE9F2EC),
    onSecondaryContainer = Color(0xFF0B1611),
    tertiary = Color(0xFF0284C7),
    background = Color(0xFFF4F8F5),
    onBackground = Color(0xFF0B1611),
    surface = Color(0xFFF4F8F5),
    onSurface = Color(0xFF0B1611),
    surfaceVariant = Color(0xFFE9F2EC),
    onSurfaceVariant = Color(0xFF55695E),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF9FCFA),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFEEF5F0),
    surfaceContainerHighest = Color(0xFFE3EDE6),
    outline = Color(0xFFB9C9BF),
    outlineVariant = Color(0xFFDCE6DF),
    error = Color(0xFFDC2626),
    onError = Color.White,
)

private val KasakaShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun KasakaTheme(themeMode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    CompositionLocalProvider(LocalKasakaColors provides if (dark) DarkKasakaColors else LightKasakaColors) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            typography = KasakaTypography,
            shapes = KasakaShapes,
            content = content,
        )
    }
}

object KasakaTheme {
    val colors: KasakaColors
        @Composable @ReadOnlyComposable get() = LocalKasakaColors.current
}
