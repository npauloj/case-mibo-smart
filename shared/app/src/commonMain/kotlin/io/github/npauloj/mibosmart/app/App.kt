package io.github.npauloj.mibosmart.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.npauloj.mibosmart.app.camera.LiveVideoScreen
import io.github.npauloj.mibosmart.app.devices.DeviceListScreen
import io.github.npauloj.mibosmart.app.lock.LockDestination
import io.github.npauloj.mibosmart.app.lock.LockScreen
import io.github.npauloj.mibosmart.app.resources.Res
import io.github.npauloj.mibosmart.app.resources.session_expiring_soon
import io.github.npauloj.mibosmart.domain.device.Device
import io.github.npauloj.mibosmart.app.session.TokenScreen
import io.github.npauloj.mibosmart.app.ui.AppTheme
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Which screen the app opens on is decided by the stored session, not by a default (SPEC S5): a cold
 * start with one goes straight to the device list, a cold start without one to the token screen. Why
 * nothing is drawn until the answer is back is in [AppUiState.destination].
 */
@Composable
fun App(viewModel: AppViewModel = koinViewModel()) {
    AppTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            val state by viewModel.state.collectAsStateWithLifecycle()
            when (state.destination) {
                null -> Unit
                AppDestination.TokenEntry -> TokenScreen(onAuthenticated = viewModel::onAuthenticated)
                AppDestination.DeviceList -> SignedIn(expiringSoon = state.expiringSoon)
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
private fun SignedIn(expiringSoon: Boolean) {
    var lock: LockDestination? by remember { mutableStateOf(null) }
    var camera: Device? by remember { mutableStateOf(null) }

    val watching = camera
    val selected = lock
    when {
        watching != null -> LiveVideoScreen(camera = watching, onBack = { camera = null })
        selected != null -> LockScreen(destination = selected, onBack = { lock = null })
        else -> DeviceListDestination(expiringSoon) { DeviceListScreen() }
    }
}

/**
 * The device list with the session banner above it (SPEC S7).
 *
 * The banner sits here and not inside `DeviceListScreen` because it is about the session, not about
 * the devices: the list owns its own loading, empty and error states, and D-01b keeps owning them
 * while this stays a property of being signed in. It takes [content] so a preview can show the real
 * composition without a ViewModel.
 */
@Composable
internal fun DeviceListDestination(expiringSoon: Boolean, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (expiringSoon) SessionExpiryBanner()
        content()
    }
}

/**
 * "Token expira em breve" — a strip above the list, and nothing else (SPEC S7).
 *
 * Non-blocking is the whole requirement: it takes no touch, dims nothing and offers no action, so
 * every device stays reachable while it is up. "Renovar" lands on it in S-03; until then a warning
 * the user can act on by pasting a fresh token beats a dialog they have to dismiss first.
 */
@Composable
private fun SessionExpiryBanner() {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(Res.string.session_expiring_soon),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}
