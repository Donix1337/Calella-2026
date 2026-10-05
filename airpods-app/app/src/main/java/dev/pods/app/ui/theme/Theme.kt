package dev.pods.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** iOS system colors, light and dark. */
@Immutable
data class PodsColors(
    val background: Color,
    val card: Color,
    val cardPressed: Color,
    val elevated: Color,
    val label: Color,
    val secondaryLabel: Color,
    val tertiaryLabel: Color,
    val separator: Color,
    val switchOff: Color,
    val fill: Color,
    val green: Color,
    val red: Color,
    val orange: Color,
    val yellow: Color,
    val blue: Color,
    val indigo: Color,
    val purple: Color,
    val pink: Color,
    val teal: Color,
    val gray: Color,
    val isDark: Boolean,
)

val LightPodsColors = PodsColors(
    background = Color(0xFFF2F2F7),
    card = Color(0xFFFFFFFF),
    cardPressed = Color(0xFFD1D1D6),
    elevated = Color(0xFFFFFFFF),
    label = Color(0xFF000000),
    secondaryLabel = Color(0x993C3C43),
    tertiaryLabel = Color(0x4D3C3C43),
    separator = Color(0x4A3C3C43),
    switchOff = Color(0xFFE9E9EB),
    fill = Color(0x1F767680),
    green = Color(0xFF34C759),
    red = Color(0xFFFF3B30),
    orange = Color(0xFFFF9500),
    yellow = Color(0xFFFFCC00),
    blue = Color(0xFF007AFF),
    indigo = Color(0xFF5856D6),
    purple = Color(0xFFAF52DE),
    pink = Color(0xFFFF2D55),
    teal = Color(0xFF30B0C7),
    gray = Color(0xFF8E8E93),
    isDark = false,
)

val DarkPodsColors = PodsColors(
    background = Color(0xFF000000),
    card = Color(0xFF1C1C1E),
    cardPressed = Color(0xFF3A3A3C),
    elevated = Color(0xFF2C2C2E),
    label = Color(0xFFFFFFFF),
    secondaryLabel = Color(0x99EBEBF5),
    tertiaryLabel = Color(0x4DEBEBF5),
    separator = Color(0xA6545458),
    switchOff = Color(0xFF39393D),
    fill = Color(0x3D767680),
    green = Color(0xFF30D158),
    red = Color(0xFFFF453A),
    orange = Color(0xFFFF9F0A),
    yellow = Color(0xFFFFD60A),
    blue = Color(0xFF0A84FF),
    indigo = Color(0xFF5E5CE6),
    purple = Color(0xFFBF5AF2),
    pink = Color(0xFFFF375F),
    teal = Color(0xFF40C8E0),
    gray = Color(0xFF8E8E93),
    isDark = true,
)

val LocalPodsColors = staticCompositionLocalOf { LightPodsColors }

object Pods {
    val colors: PodsColors
        @Composable get() = LocalPodsColors.current
}

@Composable
fun PodsTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = if (dark) DarkPodsColors else LightPodsColors
    val scheme = if (dark) {
        darkColorScheme(
            primary = colors.blue,
            background = colors.background,
            surface = colors.card,
            onBackground = colors.label,
            onSurface = colors.label,
        )
    } else {
        lightColorScheme(
            primary = colors.blue,
            background = colors.background,
            surface = colors.card,
            onBackground = colors.label,
            onSurface = colors.label,
        )
    }
    CompositionLocalProvider(LocalPodsColors provides colors) {
        MaterialTheme(colorScheme = scheme, typography = PodsMaterialTypography, content = content)
    }
}
