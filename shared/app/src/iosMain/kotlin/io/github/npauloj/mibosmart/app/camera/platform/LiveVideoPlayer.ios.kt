package io.github.npauloj.mibosmart.app.camera.platform

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.npauloj.mibosmart.app.resources.Res
import io.github.npauloj.mibosmart.app.resources.live_ios_placeholder
import org.jetbrains.compose.resources.stringResource

/**
 * The iOS surface, as a placeholder (**V-01b** replaces it with the WKWebView on `monitor_url`).
 *
 * It exists in this slice because an `expect` without an `actual` for a declared target does not
 * compile, and the macOS job does not run on pull requests: a missing actual would merge green and
 * break `main`. It plays nothing, opens nothing and — the part that matters for the shared account —
 * spends no streaming quota, because the session is created by the ViewModel before any surface is
 * composed and this one reports no event that could ask for another.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
actual fun LiveVideoPlayer(url: String, onEvent: (PlayerEvent) -> Unit, modifier: Modifier) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant).padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(Res.string.live_ios_placeholder),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}
