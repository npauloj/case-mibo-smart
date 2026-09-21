package io.github.npauloj.mibosmart.app.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.npauloj.mibosmart.app.resources.Res
import io.github.npauloj.mibosmart.app.resources.lock_back
import io.github.npauloj.mibosmart.app.resources.lock_error_expired
import io.github.npauloj.mibosmart.app.resources.lock_error_failed
import io.github.npauloj.mibosmart.app.resources.lock_error_offline
import io.github.npauloj.mibosmart.app.resources.lock_error_rejected
import io.github.npauloj.mibosmart.app.resources.lock_error_unexpected
import io.github.npauloj.mibosmart.app.resources.lock_last_update_days
import io.github.npauloj.mibosmart.app.resources.lock_last_update_hours
import io.github.npauloj.mibosmart.app.resources.lock_last_update_minutes
import io.github.npauloj.mibosmart.app.resources.lock_last_update_moments
import io.github.npauloj.mibosmart.app.resources.lock_last_update_never
import io.github.npauloj.mibosmart.app.resources.lock_loading
import io.github.npauloj.mibosmart.app.resources.lock_offline
import io.github.npauloj.mibosmart.app.resources.lock_remote_open_disabled_explanation
import io.github.npauloj.mibosmart.app.resources.lock_remote_open_disabled_title
import io.github.npauloj.mibosmart.app.resources.lock_retry
import io.github.npauloj.mibosmart.app.resources.lock_state_label
import io.github.npauloj.mibosmart.app.resources.lock_state_locked
import io.github.npauloj.mibosmart.app.resources.lock_state_unlocked
import io.github.npauloj.mibosmart.app.resources.lock_volume_high
import io.github.npauloj.mibosmart.app.resources.lock_volume_label
import io.github.npauloj.mibosmart.app.resources.lock_volume_low
import io.github.npauloj.mibosmart.app.resources.lock_volume_medium
import io.github.npauloj.mibosmart.app.resources.lock_volume_mute
import io.github.npauloj.mibosmart.domain.device.Device
import io.github.npauloj.mibosmart.domain.lock.LockAddress
import io.github.npauloj.mibosmart.domain.lock.VolumeLevel
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * What the device list hands the lock destination: the row it was opened from — which is where the
 * lock's name and its online/offline presence come from (SPEC U3) — and how the partner addresses
 * that lock (`docs/api-contract.md` §5).
 *
 * The edge that selects a lock belongs to the device list, which is a slice of its own; this slice
 * defines the destination and what it needs.
 */
data class LockDestination(val device: Device, val address: LockAddress)

/**
 * Reading a lock (SPEC L1, L2, L5, L8): three parallel reads on entry, then the door's state, the
 * volume, and — when remote opening is off — what that means.
 *
 * Nothing here changes the lock. Every write is L-01b's and L-02's, which is why this screen has one
 * action: asking again after a failure.
 */
@Composable
fun LockScreen(
    destination: LockDestination,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LockViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // The read runs on the ViewModel's scope, not the composition's: a rotation must not cancel three
    // requests the account already paid for (ADR-003, ADR-006). Entering the same destination twice
    // reads nothing again — the ViewModel keeps that guard.
    LaunchedEffect(destination) { viewModel.open(destination) }

    LockScreenContent(
        state = state,
        onRetry = viewModel::retry,
        onBack = onBack,
        modifier = modifier,
    )
}

/** The screen as a pure function of its state, so every state has a preview and no ViewModel. */
@Composable
fun LockScreenContent(
    state: LockUiState,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TextButton(onClick = onBack) { Text(stringResource(Res.string.lock_back)) }
        Text(text = state.deviceName, style = MaterialTheme.typography.headlineSmall)

        if (state.isOffline) {
            OfflineNotice(state.lastSeen)
        }

        when (state) {
            is LockUiState.Loading -> LoadingRow()
            is LockUiState.Ready -> LockReadings(state)
            is LockUiState.Failed -> ErrorSection(state, onRetry)
        }
    }
}

@Composable
private fun LoadingRow() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Text(text = stringResource(Res.string.lock_loading), style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * The device list reported the lock offline, so what follows is the last thing the hub knew about it
 * and its age (SPEC L5, U3) — shown above the readings rather than instead of them.
 */
@Composable
private fun OfflineNotice(lastSeen: LastSeen?) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(Res.string.lock_offline),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Text(text = lastSeen.asText(), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun LockReadings(state: LockUiState.Ready) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LabelledValue(
            label = stringResource(Res.string.lock_state_label),
            value = stringResource(
                if (state.lock.isOpen) Res.string.lock_state_unlocked else Res.string.lock_state_locked,
            ),
        )
        LabelledValue(
            label = stringResource(Res.string.lock_volume_label),
            value = stringResource(state.lock.volume.label),
        )
        if (state.isRemoteOpenDisabled) {
            RemoteOpenDisabledExplanation()
        }
    }
}

@Composable
private fun LabelledValue(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(text = value, style = MaterialTheme.typography.titleMedium)
    }
}

/**
 * SPEC L2, read half: the precondition is explained, and that is all.
 *
 * There is deliberately no control here. Enabling remote opening changes what a physical door will
 * do on a stranger's tap, so it is a labelled, deliberate action of its own — L-01b's — never
 * something this screen offers in passing.
 */
@Composable
private fun RemoteOpenDisabledExplanation() {
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(Res.string.lock_remote_open_disabled_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(Res.string.lock_remote_open_disabled_explanation),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** One named cause and one action, never a status code or the server's raw words (SPEC U6). */
@Composable
private fun ErrorSection(state: LockUiState.Failed, onRetry: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // The server's sentence wins when there is one (SPEC S3.1): on a 403 the partner already says
        // what to do, and rewording it would only make it vaguer.
        Text(
            text = state.serverMessage ?: stringResource(state.error.message),
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(onClick = onRetry) { Text(stringResource(Res.string.lock_retry)) }
    }
}

@Composable
private fun LastSeen?.asText(): String = when (this) {
    null, LastSeen.Never -> stringResource(Res.string.lock_last_update_never)
    LastSeen.Moments -> stringResource(Res.string.lock_last_update_moments)
    is LastSeen.Minutes -> stringResource(Res.string.lock_last_update_minutes, value.toString())
    is LastSeen.Hours -> stringResource(Res.string.lock_last_update_hours, value.toString())
    is LastSeen.Days -> stringResource(Res.string.lock_last_update_days, value.toString())
}

/** The 0..3 the partner speaks, as the words the user reads (SPEC L7). */
private val VolumeLevel.label: StringResource
    get() = when (this) {
        VolumeLevel.Mute -> Res.string.lock_volume_mute
        VolumeLevel.Low -> Res.string.lock_volume_low
        VolumeLevel.Medium -> Res.string.lock_volume_medium
        VolumeLevel.High -> Res.string.lock_volume_high
    }

/** One friendly sentence per category, never the server's own words (SPEC U6). */
private val LockError.message: StringResource
    get() = when (this) {
        LockError.TokenRejected -> Res.string.lock_error_rejected
        LockError.TokenExpired -> Res.string.lock_error_expired
        LockError.Offline -> Res.string.lock_error_offline
        LockError.UnexpectedResponse -> Res.string.lock_error_unexpected
        LockError.Failed -> Res.string.lock_error_failed
    }
