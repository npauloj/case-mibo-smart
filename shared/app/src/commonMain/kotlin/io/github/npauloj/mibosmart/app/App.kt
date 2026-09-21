package io.github.npauloj.mibosmart.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
 * Which screen the app opens on is decided by the stored session, not by a default (SPEC S5): a cold
 * start with one goes straight to the device list, a cold start without one to the token screen. Why
 * nothing is drawn until the answer is back is in [AppUiState.destination].
 *
 * It is also where a session *ends*: the guard of SPEC S6 routes here, which is why the reason it
 * carries is rendered above the token screen rather than inside it — the token screen owns its own
 * validation errors, and an expiry is not one of them.
 */
@Composable
fun App(viewModel: AppViewModel = koinViewModel()) {
    AppTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
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
                )
            }
        }
    }
}

/**
 * The destinations reachable with a session: the device list, the lock screen, and the live video of
 * one camera.
 *
 * The list → live edge is the one SPEC U2 measures: tapping a camera row puts the picture on screen
 * with nothing in between. The lock edge is still missing on purpose — it belongs to the lock
 * slice — and each destination states in one place what it has to be handed: [LockDestination] the
 * composite address of `docs/api-contract.md` §5, and the camera its own [Device], whose `ns` is
 * what `criar-fluxo-video` addresses and whose `status` decides SPEC V7 without a request.
 *
 * Leaving the live screen returns to the list *without* rebuilding it: the ViewModel behind
 * `DeviceListScreen` survives, so coming back costs no request (SPEC D7).
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
            DeviceListScreen(onOpenLiveVideo = { camera = it })
        }
    }
}

/**
 * The device list with the session banner above it, and the way to the account screen (SPEC S7, S8).
 *
 * Both sit here and not inside `DeviceListScreen` because both are about the session, not about the
 * devices: the list owns its own loading, empty and error states, and D-01b keeps owning them while
 * these stay properties of being signed in. It takes [content] so a preview can show the real
 * composition without a ViewModel.
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

/**
 * The token screen, with the reason it was reopened when the user did not ask for it (SPEC U5).
 *
 * [reason] is `null` on a cold start and after "Sair": an entry screen the user walked to needs no
 * explanation, and a banner that is always there explains nothing.
 */
@Composable
internal fun TokenEntryDestination(reason: SessionEndReason?, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (reason != null) SessionEndedBanner(reason)
        content()
    }
}

/**
 * Why the session ended, in the partner's words when it gave any (SPEC U6, ADR-012).
 *
 * A 403 answers "Token expirado, por favor gere um novo token", which already says what to do; every
 * other refusal gets the app's own sentence, which names the 2 h the user could not see coming.
 */
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
