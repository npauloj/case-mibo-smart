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
import io.github.npauloj.mibosmart.app.devices.DeviceListScreen
import io.github.npauloj.mibosmart.app.lock.LockDestination
import io.github.npauloj.mibosmart.app.lock.LockScreen
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
 * The destinations reachable with a session: the device list, and the lock screen it opens.
 *
 * The edge from a row to [LockScreen] is still missing on purpose — making a lock row tappable is the
 * device slice's follow-up (D-02), not this one's. The destination stays declared here so the row has
 * somewhere to go, and so [LockDestination] states in one place what it has to hand over: the device
 * it was opened from and the composite address of `docs/api-contract.md` §5.
 */
@Composable
private fun SignedIn() {
    var lock: LockDestination? by remember { mutableStateOf(null) }

    val selected = lock
    if (selected == null) {
        DeviceListScreen()
    } else {
        LockScreen(destination = selected, onBack = { lock = null })
    }
}
