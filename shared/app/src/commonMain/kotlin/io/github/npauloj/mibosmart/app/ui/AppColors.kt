package io.github.npauloj.mibosmart.app.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** The one colour role Material 3 does not have, and this app cannot do without: **waiting**. */
@Immutable
internal data class AppColors(
    val waiting: Color,
    val onWaiting: Color,
    val waitingContainer: Color,
    val onWaitingContainer: Color,
)

internal val LightAppColors = AppColors(
    waiting = Color(0xFF8A5B00),
    onWaiting = Color(0xFFFFFFFF),
    waitingContainer = Color(0xFFFFDEA6),
    onWaitingContainer = Color(0xFF2B1700),
)

internal val DarkAppColors = AppColors(
    waiting = Color(0xFFF0BC5C),
    onWaiting = Color(0xFF452B00),
    waitingContainer = Color(0xFF624000),
    onWaitingContainer = Color(0xFFFFDEA6),
)

/**
 * Defaults to the light set so a composable previewed outside [AppTheme] still draws something
 * readable rather than throwing — a preview that crashes teaches nothing.
 */
internal val LocalAppColors = staticCompositionLocalOf { LightAppColors }
