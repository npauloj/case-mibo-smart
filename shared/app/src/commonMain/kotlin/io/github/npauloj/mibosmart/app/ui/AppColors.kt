package io.github.npauloj.mibosmart.app.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The one colour role Material 3 does not have, and this app cannot do without: **waiting**.
 *
 * Material gives `primary`, `error` and little in between. That is enough for an app whose states are
 * "fine" and "broken". This app's hardest state is neither: a lock command that was *sent* and not yet
 * *confirmed* — the API acknowledges the command, never the hardware (ADR-021). Painting that red
 * would call a working door a failure; leaving it neutral would hide the one thing the user needs to
 * know. It is its own category and it gets its own colour.
 *
 * The same amber carries every "the app does not know yet": reconnecting to a camera, a session about
 * to expire, a command whose confirmation window ran out.
 *
 * Deliberately **not** green: with the brand green taken by identity, green is no longer available to
 * mean "settled", and settled states are drawn in plain ink instead. That is a better answer anyway —
 * on this app's lock screen, "Aberta" and "Fechada" are both legitimate, and colouring either one
 * would turn a fact into an alarm.
 */
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
