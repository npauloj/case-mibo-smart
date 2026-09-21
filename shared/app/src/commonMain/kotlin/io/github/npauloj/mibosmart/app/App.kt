package io.github.npauloj.mibosmart.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.npauloj.mibosmart.app.resources.Res
import io.github.npauloj.mibosmart.app.resources.device_list_placeholder
import io.github.npauloj.mibosmart.app.resources.device_list_title
import io.github.npauloj.mibosmart.app.lock.LockDestination
import io.github.npauloj.mibosmart.app.lock.LockScreen
import io.github.npauloj.mibosmart.app.session.TokenScreen
import io.github.npauloj.mibosmart.app.ui.AppTheme
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Startup routing (stored token, expiry guard) is the session slice that follows; here the app always
 * starts on the token screen and the accepted token opens the destination below. Where the flag lives,
 * and why it is not `rememberSaveable`, is in [AppViewModel].
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
 * The edge is the missing half on purpose — picking a lock means a row of the real device list, and
 * that list is the device slice's (L-01a non-goals). The destination is defined here so the row has
 * somewhere to go and so [LockDestination] states, in one place, what it has to hand over: the
 * device it was opened from and the composite address of `docs/api-contract.md` §5.
 */
@Composable
private fun SignedIn() {
    var lock: LockDestination? by remember { mutableStateOf(null) }

    val selected = lock
    if (selected == null) {
        DeviceListPlaceholder()
    } else {
        LockScreen(destination = selected, onBack = { lock = null })
    }
}

/** Stands in for the device list until the device slice replaces it. */
@Composable
private fun DeviceListPlaceholder() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = stringResource(Res.string.device_list_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = stringResource(Res.string.device_list_placeholder),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
