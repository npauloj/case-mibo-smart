package io.github.npauloj.mibosmart.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

/**
 * The app's single theme. Light or dark follows the system — and, inside a preview, the
 * `uiMode` of the preview annotation, which is why the dark variants need no second screen
 * body.
 */
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    CompositionLocalProvider(LocalAppColors provides if (dark) DarkAppColors else LightAppColors) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = AppTypography,
            content = content,
        )
    }
}

/**
 * The brand green, used where a fill needs no text on top of it (a badge, an indicator, a
 * rail).
 */
private val Brand = Color(0xFF00A335)

/** The same green, deepened until white text on it measures 6.3:1. This is what buttons are made of. */
private val BrandDeep = Color(0xFF00702A)

/** Light. */
private val LightColors = lightColorScheme(
    primary = BrandDeep,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFA4F2BC),
    onPrimaryContainer = Color(0xFF00210D),

    secondary = Color(0xFF4E6355),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD1E8D6),
    onSecondaryContainer = Color(0xFF0C1F14),

    tertiary = Brand,
    onTertiary = Color(0xFF00250D),
    tertiaryContainer = Color(0xFFC6F7D3),
    onTertiaryContainer = Color(0xFF00210C),

    error = Color(0xFF9B3B2F),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD4),
    onErrorContainer = Color(0xFF3F0A04),

    background = Color(0xFFF8FAF8),
    onBackground = Color(0xFF101A14),
    surface = Color(0xFFF8FAF8),
    onSurface = Color(0xFF101A14),
    surfaceVariant = Color(0xFFDDE5DC),
    onSurfaceVariant = Color(0xFF414942),

    outline = Color(0xFF717971),
    outlineVariant = Color(0xFFC1C9C0),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFF2D322D),
    inverseOnSurface = Color(0xFFEEF2EC),
    inversePrimary = Color(0xFF5BD48C),
)

/** Dark. */
private val DarkColors = darkColorScheme(
    primary = Color(0xFF5BD48C),
    onPrimary = Color(0xFF00391B),
    primaryContainer = Color(0xFF005229),
    onPrimaryContainer = Color(0xFFA4F2BC),

    secondary = Color(0xFFB6CCBA),
    onSecondary = Color(0xFF223528),
    secondaryContainer = Color(0xFF384B3E),
    onSecondaryContainer = Color(0xFFD1E8D6),

    tertiary = Color(0xFF35C46B),
    onTertiary = Color(0xFF003A18),
    tertiaryContainer = Color(0xFF005226),
    onTertiaryContainer = Color(0xFFA9F5C0),

    error = Color(0xFFFFB4A6),
    onError = Color(0xFF5F1400),
    errorContainer = Color(0xFF7E2A1B),
    onErrorContainer = Color(0xFFFFDAD4),

    background = Color(0xFF0E1511),
    onBackground = Color(0xFFDFE5DE),
    surface = Color(0xFF0E1511),
    onSurface = Color(0xFFDFE5DE),
    surfaceVariant = Color(0xFF414942),
    onSurfaceVariant = Color(0xFFC1C9C0),

    outline = Color(0xFF8B938B),
    outlineVariant = Color(0xFF414942),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFFDFE5DE),
    inverseOnSurface = Color(0xFF2D322D),
    inversePrimary = BrandDeep,
)
