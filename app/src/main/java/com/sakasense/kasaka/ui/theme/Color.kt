package com.sakasense.kasaka.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Brand tokens taken from sakasense.com (--color-*), so the app and website feel like one product.
object Brand {
    val Bg = Color(0xFF050B08)
    val Surface = Color(0xFF0B1611)
    val Surface2 = Color(0xFF11211A)
    val Border = Color(0x1F86EFAC)
    val Primary = Color(0xFF22E07A)
    val PrimaryGlow = Color(0xFF5BFFA6)
    val OnPrimary = Color(0xFF04130B)
    val Accent = Color(0xFFB8F35A)
    val Text = Color(0xFFE8F5EC)
    val TextMuted = Color(0xFF8FA89A)
    val Danger = Color(0xFFF87171)
    val Warning = Color(0xFFFBBF24)
    val Info = Color(0xFF38BDF8)
    val Soil = Color(0xFF8B5E3C)
}

/** Colors Material 3 has no slot for: status, nutrients, borders. */
@Immutable
data class KasakaColors(
    val surface2: Color,
    val border: Color,
    val muted: Color,
    val healthy: Color,
    val attention: Color,
    val action: Color,
    val info: Color,
    val ph: Color,
    val nitrogen: Color,
    val phosphorus: Color,
    val potassium: Color,
    val soil: Color,
    val glow: Color,
)

val DarkKasakaColors = KasakaColors(
    surface2 = Brand.Surface2,
    border = Brand.Border,
    muted = Brand.TextMuted,
    healthy = Brand.Primary,
    attention = Brand.Warning,
    action = Brand.Danger,
    info = Brand.Info,
    ph = Brand.Info,
    nitrogen = Brand.Primary,
    phosphorus = Color(0xFFFBBF24),
    potassium = Color(0xFFC084FC),
    soil = Brand.Soil,
    glow = Brand.PrimaryGlow,
)

// Light theme: same hues, darkened for contrast in direct sunlight.
val LightKasakaColors = KasakaColors(
    surface2 = Color(0xFFE9F2EC),
    border = Color(0x1A0B1611),
    muted = Color(0xFF55695E),
    healthy = Color(0xFF0E9F55),
    attention = Color(0xFFB7791F),
    action = Color(0xFFDC2626),
    info = Color(0xFF0284C7),
    ph = Color(0xFF0284C7),
    nitrogen = Color(0xFF0E9F55),
    phosphorus = Color(0xFFC27C0E),
    potassium = Color(0xFF9333EA),
    soil = Brand.Soil,
    glow = Color(0xFF22E07A),
)

val LocalKasakaColors = staticCompositionLocalOf { DarkKasakaColors }
