package io.github.npauloj.mibosmart.app.camera.platform

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import platform.Foundation.NSURL
import platform.UIKit.UIApplication

/**
 * The monitor page in the system browser (SPEC V9).
 *
 * Safari and not a `WKWebView` inside the app, and that asymmetry with Android is the whole point:
 * on this platform the player surface **already is** a `WKWebView` on this same url (SPEC V10,
 * ADR-005). A second one in the same app would be the same web view failing the same way. Safari is
 * a different process, with its own media pipeline and the user's own credentials — the only thing
 * left that might play where the app could not.
 *
 * It hands the url over and closes itself at once, so the user comes back to a screen that still
 * offers "Tentar novamente" rather than to an empty frame behind the browser.
 */
@Composable
actual fun WebPlayerFallback(
    url: String,
    onClose: () -> Unit,
    modifier: Modifier,
) {
    if (LocalInspectionMode.current) {
        Box(modifier)
        return
    }
    val latestOnClose by rememberUpdatedState(onClose)
    LaunchedEffect(url) {
        NSURL.URLWithString(url)?.let {
            UIApplication.sharedApplication.openURL(it, options = emptyMap<Any?, Any>(), completionHandler = null)
        }
        latestOnClose()
    }
    Box(modifier)
}
