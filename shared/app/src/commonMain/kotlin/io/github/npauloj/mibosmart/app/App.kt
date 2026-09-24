package io.github.npauloj.mibosmart.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.npauloj.mibosmart.app.camera.LiveVideoScreen
import io.github.npauloj.mibosmart.app.devices.DeviceListScreen
import io.github.npauloj.mibosmart.app.lock.LockDestination
import io.github.npauloj.mibosmart.app.lock.LockScreen
import io.github.npauloj.mibosmart.app.resources.Res
import io.github.npauloj.mibosmart.app.resources.account_open
import io.github.npauloj.mibosmart.app.resources.session_ended
import io.github.npauloj.mibosmart.app.resources.session_expiring_soon
import io.github.npauloj.mibosmart.domain.device.Device
import io.github.npauloj.mibosmart.app.session.AccountScreen
import io.github.npauloj.mibosmart.app.session.TokenScreen
import io.github.npauloj.mibosmart.app.ui.AppTheme
import io.github.npauloj.mibosmart.domain.session.SessionEndReason
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Which screen the app opens on is decided by the stored session, not by a default (SPEC S5): a
 * cold start with one goes straight to the device list, a cold start without one to the token
 * screen.
 */
@Composable
fun App(viewModel: AppViewModel = koinViewModel()) {
    AppTheme {
        Surface(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            val state by viewModel.state.collectAsStateWithLifecycle()
            when (state.destination) {
                null -> Unit
                AppDestination.TokenEntry -> TokenEntryDestination(state.sessionEnded) {
                    TokenScreen(onAuthenticated = viewModel::onAuthenticated)
                }

                AppDestination.DeviceList -> SignedIn(
                    expiringSoon = state.expiringSoon,
                    onOpenAccount = viewModel::openAccount,
                )

                AppDestination.Account -> AccountScreen(
                    onBack = viewModel::closeAccount,
                    onSignedOut = viewModel::onSignedOut,
                    onRenewed = viewModel::onRenewed,
                )
            }
        }
    }
}

/**
 * The destinations reachable with a session: the device list, the lock screen, and the live
 * video of one camera.
 */
@Composable
private fun SignedIn(expiringSoon: Boolean, onOpenAccount: () -> Unit) {
    var lock: LockDestination? by remember { mutableStateOf(null) }
    var camera: Device? by remember { mutableStateOf(null) }

    val watching = camera
    val selected = lock
    when {
        watching != null -> LiveVideoScreen(camera = watching, onBack = { camera = null })
        selected != null -> LockScreen(destination = selected, onBack = { lock = null })
        else -> DeviceListDestination(expiringSoon, onOpenAccount) {
            DeviceListScreen(
                onOpenLiveVideo = { camera = it },
                onOpenLock = { device, address -> lock = LockDestination(device, address) },
            )
        }
    }
}

/**
 * The device list with the session banner above it, and the way to the account screen (SPEC S7,
 * S8).
 */
@Composable
internal fun DeviceListDestination(
    expiringSoon: Boolean,
    onOpenAccount: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onOpenAccount) { Text(stringResource(Res.string.account_open)) }
        }
        if (expiringSoon) SessionExpiryBanner()
        content()
    }
}

/** "Token expira em breve" — a strip above the list, and nothing else (SPEC S7). */
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

/**
 * The token screen, with the reason it was reopened when the user did not ask for it (SPEC U5).
 */
@Composable
internal fun TokenEntryDestination(reason: SessionEndReason?, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (reason != null) SessionEndedBanner(reason)
        content()
    }
}

/** Why the session ended, in the partner's words when it gave any (SPEC U6, ADR-012). */
@Composable
private fun SessionEndedBanner(reason: SessionEndReason) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = when (reason) {
                SessionEndReason.Expired -> stringResource(Res.string.session_ended)
                is SessionEndReason.StatedByPartner -> reason.message
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}
