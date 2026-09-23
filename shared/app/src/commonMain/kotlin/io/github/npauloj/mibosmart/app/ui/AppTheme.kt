package io.github.npauloj.mibosmart.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

/**
 * The app's single theme. Light or dark follows the system — and, inside a preview, the `uiMode` of
 * the preview annotation, which is why the dark variants need no second screen body.
 *
 * Until now this was `lightColorScheme()` / `darkColorScheme()` with no arguments: Material 3's
 * defaults, which are purple. Every screenshot recorded before this change shows that purple, on a
 * product whose brand is green.
 *
 * **The whole scheme is spelled out rather than partially overridden.** Ten roles are read by name in
 * this codebase, but Material's own components reach for many more — a `Button` wants `primary` and
 * `onPrimary`, a `TextField` wants `outline` and `surfaceVariant`, a `Card` wants `surface`. Leaving
 * any of them to the default means one purple edge surviving in a screen nobody thought to check.
 */
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    // `waiting` rides along in a CompositionLocal because Material 3 has no role for it and this app
    // cannot describe a lock without one (see AppColors).
    CompositionLocalProvider(LocalAppColors provides if (dark) DarkAppColors else LightAppColors) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = AppTypography,
            content = content,
        )
    }
}

/**
 * The brand green, used where a fill needs no text on top of it (a badge, an indicator, a rail).
 *
 * It is **not** `primary`: white on this green measures 3.4:1, which clears the 3:1 that WCAG asks of
 * a UI component or large text, and falls short of the 4.5:1 a button label needs. So the interactive
 * fill is [BrandDeep], and the pure colour keeps the jobs where nothing is written over it.
 */
private val Brand = Color(0xFF00A335)

/** The same green, deepened until white text on it measures 6.3:1. This is what buttons are made of. */
private val BrandDeep = Color(0xFF00702A)

/**
 * Light.
 *
 * The neutrals carry a slight green bias rather than being pure grey — a pure mid-grey beside a
 * saturated green reads as two unrelated decisions.
 *
 * `error` is a clay red, not the Material default: at 7:1 on white it is legible, and it sits with the
 * green instead of vibrating against it. It matters more here than in most apps — the lock screen
 * shows "não foi possível enviar o comando" over a physical door.
 */
private val LightColors = lightColorScheme(
    primary = BrandDeep,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFA4F2BC),
    onPrimaryContainer = Color(0xFF00210D),

    secondary = Color(0xFF4E6355),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD1E8D6),
    onSecondaryContainer = Color(0xFF0C1F14),

    // The brand itself lives here: a role for accents that carry no label of their own.
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

/**
 * Dark.
 *
 * Not an inversion: the brand green is lifted to [Color] `0xFF5BD48C`, because `#00702A` on a dark
 * ground is a smudge. The ground itself is a near-black with the same green bias as the light
 * neutrals, so the two themes read as one family rather than as two palettes.
 */
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
