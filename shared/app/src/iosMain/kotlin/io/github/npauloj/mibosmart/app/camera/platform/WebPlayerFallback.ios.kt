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

/** The monitor page in the system browser (SPEC V9). */
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
