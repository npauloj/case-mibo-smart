package io.github.npauloj.mibosmart.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.npauloj.mibosmart.app.camera.LiveVideoScreen
import io.github.npauloj.mibosmart.app.devices.DeviceListScreen
import io.github.npauloj.mibosmart.app.lock.LockDestination
import io.github.npauloj.mibosmart.app.lock.LockScreen
import io.github.npauloj.mibosmart.domain.device.Device
import io.github.npauloj.mibosmart.app.session.TokenScreen
import io.github.npauloj.mibosmart.app.ui.AppTheme
import org.koin.compose.viewmodel.koinViewModel

/**
 * Startup routing (stored token, expiry guard) is the session slice that follows; here the app always
 * starts on the token screen and the accepted token opens the device list. Where the flag lives, and
 * why it is not `rememberSaveable`, is in [AppViewModel].
 */
@Composable
fun App(viewModel: AppViewModel = koinViewModel()) {
    AppTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            val authenticated by viewModel.authenticated.collectAsStateWithLifecycle()
            if (authenticated) {
                SignedIn()
            } else {
                TokenScreen(onAuthenticated = viewModel::onAuthenticated)
            }
        }
    }
}

/**
 * The destinations reachable with a session: the device list, the lock screen, and the live video of
 * one camera.
 *
 * The edges from a row are still missing on purpose — making a row tappable is the device slice's
 * follow-up (D-02), not this one's. The destinations stay declared here so the rows have somewhere to
 * go, and so each states in one place what it has to hand over: [LockDestination] the composite
 * address of `docs/api-contract.md` §5, and the camera its own [Device], whose `ns` is what
 * `criar-fluxo-video` addresses and whose `status` decides SPEC V7 without a request.
 */
@Composable
private fun SignedIn() {
    var lock: LockDestination? by remember { mutableStateOf(null) }
    var camera: Device? by remember { mutableStateOf(null) }

    val watching = camera
    val selected = lock
    when {
        watching != null -> LiveVideoScreen(camera = watching, onBack = { camera = null })
        selected != null -> LockScreen(destination = selected, onBack = { lock = null })
        else -> DeviceListScreen()
    }
}
