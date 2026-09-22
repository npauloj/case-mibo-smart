package io.github.npauloj.mibosmart.app.camera.platform

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.viewinterop.AndroidView

/**
 * The monitor page in a `WebView` inside the app (SPEC V9).
 *
 * In-app rather than in the browser because on Android this is a *fallback*, not a handover: the user
 * asked to watch this camera, and sending them to another app to do it would lose the screen they
 * came from — and with it the "Tentar novamente" that may still work.
 *
 * JavaScript is on because the page is the partner's own MSE player and is nothing without it; the
 * gesture requirement is off for the same reason the native surface autoplays — a live stream inside
 * a 16:9 frame must play there, not wait for a tap. The web view is given no bridge to the app: it
 * loads one partner url and holds no interface Kotlin exposes.
 *
 * `onClose` is unused here: the page stays in this frame, so the screen's own action is what leaves
 * it. iOS, which hands the url to another app, is the actual that needs it.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
actual fun WebPlayerFallback(
    url: String,
    @Suppress("UNUSED_PARAMETER") onClose: () -> Unit,
    modifier: Modifier,
) {
    if (LocalInspectionMode.current) {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant))
        return
    }
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                webViewClient = WebViewClient()
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                loadUrl(url)
            }
        },
        modifier = modifier,
        // A web view that is only removed from the layout keeps its page — and its video — alive.
        onRelease = {
            it.loadUrl(BLANK_PAGE)
            it.destroy()
        },
    )
}

private const val BLANK_PAGE = "about:blank"
