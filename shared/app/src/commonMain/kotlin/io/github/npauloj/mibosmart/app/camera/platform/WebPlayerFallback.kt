package io.github.npauloj.mibosmart.app.camera.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The partner's own player page, when the native player could not show the stream (SPEC V9, ADR-005).
 *
 * It is the kill switch of the native player, and it is **per platform** on purpose: an in-app
 * WebView on Android, where the app decodes the stream itself and the page is the second opinion;
 * the **system browser** on iOS, where the player surface already *is* a `WKWebView` on this very url
 * (SPEC V10) — loading it a second time in the same app would only fail the same way.
 *
 * @param url the session's `monitor_url`. The screen offers this surface only when it has one:
 *   `monitor_url` is nullable and no probe has ever seen a real one (ADR-005, ADR-006).
 * @param onClose the way back to the screen that offered it. A platform that hands the url to another
 *   app calls it as soon as it has done so, so the user is never left on an empty frame.
 *
 * Under `LocalInspectionMode` an actual renders a placeholder: previews are this project's visual
 * evidence and must never open a socket — or, on iOS, another app.
 */
@Composable
expect fun WebPlayerFallback(
    url: String,
    onClose: () -> Unit,
    modifier: Modifier,
)
