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

/** The monitor page in a `WebView` inside the app (SPEC V9). */
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
        onRelease = {
            it.loadUrl(BLANK_PAGE)
            it.destroy()
        },
    )
}

private const val BLANK_PAGE = "about:blank"
