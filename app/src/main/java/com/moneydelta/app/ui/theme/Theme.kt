package com.moneydelta.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle

@Immutable
data class MoneyDeltaSemanticColors(
    val positive: Color,
    val negative: Color,
    val positiveContainer: Color,
    val negativeContainer: Color
)

private val LightSemanticColors = MoneyDeltaSemanticColors(
    positive = Color(0xFF1B6E3A),
    negative = Color(0xFFB3261E),
    positiveContainer = Color(0xFFE4F4EA),
    negativeContainer = Color(0xFFFCE8E6)
)

private val DarkSemanticColors = MoneyDeltaSemanticColors(
    positive = Color(0xFF7AD896),
    negative = Color(0xFFF28B82),
    positiveContainer = Color(0xFF153822),
    negativeContainer = Color(0xFF421C1A)
)

val LocalSemanticColors = staticCompositionLocalOf { LightSemanticColors }

private val FallbackLightColorScheme = lightColorScheme(
    primary = Color(0xFF1E4D3B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD3EBDD),
    onPrimaryContainer = Color(0xFF0A2A1D),
    secondary = Color(0xFF4D6357),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD0E8D9),
    onSecondaryContainer = Color(0xFF0B1F16),
    background = Color(0xFFFAFBF9),
    onBackground = Color(0xFF191C1A),
    surface = Color(0xFFFAFBF9),
    onSurface = Color(0xFF191C1A),
    surfaceVariant = Color(0xFFECEFE9),
    onSurfaceVariant = Color(0xFF444944),
    outlineVariant = Color(0xFFD8DDD7)
)

private val FallbackDarkColorScheme = darkColorScheme(
    primary = Color(0xFF8CD3AF),
    onPrimary = Color(0xFF003824),
    primaryContainer = Color(0xFF104F37),
    onPrimaryContainer = Color(0xFFD3EBDD),
    secondary = Color(0xFFB4CCB9),
    onSecondary = Color(0xFF203529),
    secondaryContainer = Color(0xFF364B3F),
    onSecondaryContainer = Color(0xFFD0E8D9),
    background = Color(0xFF111412),
    onBackground = Color(0xFFE1E3DF),
    surface = Color(0xFF111412),
    onSurface = Color(0xFFE1E3DF),
    surfaceVariant = Color(0xFF1E2320),
    onSurfaceVariant = Color(0xFFC2C8C2),
    outlineVariant = Color(0xFF2E3530)
)

private val AppTypography = Typography()

fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")

@Composable
fun MoneyDeltaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> FallbackDarkColorScheme
        else -> FallbackLightColorScheme
    }
    val semanticColors = if (darkTheme) DarkSemanticColors else LightSemanticColors

    CompositionLocalProvider(LocalSemanticColors provides semanticColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            content = content
        )
    }
}
