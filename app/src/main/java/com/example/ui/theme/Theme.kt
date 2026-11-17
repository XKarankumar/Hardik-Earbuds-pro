package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Immutable
data class LuxuryExtendedColors(
    val glassSurface: Color,
    val glassSurfaceElevated: Color,
    val glassBorderTop: Color,
    val glassBorderBottom: Color,
    val metallicStart: Color,
    val metallicEnd: Color,
    val statusPositive: Color,
    val statusWarning: Color,
    val statusCritical: Color,
    val telemetryAccent: Color,
    val isDark: Boolean
)

private val DarkExtendedColors = LuxuryExtendedColors(
    glassSurface = Color(0xCC12151E),
    glassSurfaceElevated = Color(0xE61B202E),
    glassBorderTop = Color(0x55FFFFFF),
    glassBorderBottom = Color(0x18FFFFFF),
    metallicStart = Color(0xFFF6F8FC),
    metallicEnd = Color(0xFF8B95A8),
    statusPositive = EmeraldCharge,
    statusWarning = AmberWarning,
    statusCritical = CrimsonAlert,
    telemetryAccent = AcousticIceCyan,
    isDark = true
)

private val LightExtendedColors = LuxuryExtendedColors(
    glassSurface = Color(0xDCFFFFFF),
    glassSurfaceElevated = Color(0xF2F8FAFC),
    glassBorderTop = Color(0xFFFFFFFF),
    glassBorderBottom = Color(0x409BA5B8),
    metallicStart = Color(0xFF2B3242),
    metallicEnd = Color(0xFF6B758A),
    statusPositive = EmeraldChargeDark,
    statusWarning = AmberWarningDark,
    statusCritical = CrimsonAlertDark,
    telemetryAccent = AcousticCobalt,
    isDark = false
)

val LocalLuxuryColors = staticCompositionLocalOf { DarkExtendedColors }

private val DarkColorScheme = darkColorScheme(
    primary = PlatinumSilver,
    onPrimary = ObsidianVoid,
    primaryContainer = AnodizedTitanium,
    onPrimaryContainer = ChromeHighlight,
    secondary = GunmetalSilver,
    onSecondary = ObsidianVoid,
    secondaryContainer = BrushedGraphite,
    onSecondaryContainer = PlatinumSilver,
    tertiary = AcousticIceCyan,
    onTertiary = ObsidianVoid,
    background = ObsidianVoid,
    onBackground = ChromeHighlight,
    surface = CarbonSlate,
    onSurface = ChromeHighlight,
    surfaceVariant = BrushedGraphite,
    onSurfaceVariant = GunmetalSilver,
    outline = HairlineChrome,
    outlineVariant = Color(0xFF202531),
    error = CrimsonAlert,
    onError = ObsidianVoid
)

private val LightColorScheme = lightColorScheme(
    primary = ObsidianInk,
    onPrimary = PorcelainGlass,
    primaryContainer = AluminumContainer,
    onPrimaryContainer = ObsidianInk,
    secondary = SlateTitanium,
    onSecondary = PorcelainGlass,
    secondaryContainer = SilverMist,
    onSecondaryContainer = ObsidianInk,
    tertiary = AcousticCobalt,
    onTertiary = PorcelainGlass,
    background = FrostedPearl,
    onBackground = ObsidianInk,
    surface = PorcelainGlass,
    onSurface = ObsidianInk,
    surfaceVariant = SilverMist,
    onSurfaceVariant = SlateTitanium,
    outline = LightHairline,
    outlineVariant = Color(0xFFE1E6EF),
    error = CrimsonAlertDark,
    onError = PorcelainGlass
)

val BudsProShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(34.dp)
)

enum class ThemeMode {
    SYSTEM,
    DARK,
    LIGHT
}

@Composable
fun HardikBudsProTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val extendedColors = if (darkTheme) DarkExtendedColors else LightExtendedColors

    CompositionLocalProvider(LocalLuxuryColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = BudsProTypography,
            shapes = BudsProShapes,
            content = content
        )
    }
}
