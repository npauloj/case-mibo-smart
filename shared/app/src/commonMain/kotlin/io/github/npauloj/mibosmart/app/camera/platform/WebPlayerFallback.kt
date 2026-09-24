package io.github.npauloj.mibosmart.app.camera.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The partner's own player page, when the native player could not show the stream (SPEC V9,
 * ADR-005).
 * @param url the session's `monitor_url`.
 * @param onClose the way back to the screen that offered it.
 */
@Composable
expect fun WebPlayerFallback(
    url: String,
    onClose: () -> Unit,
    modifier: Modifier,
)
