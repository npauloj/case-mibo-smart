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
import androidx.compose.material3.FilterChip
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
import io.github.npauloj.mibosmart.app.resources.lock_enable_remote_open
import io.github.npauloj.mibosmart.app.resources.lock_enable_remote_open_consequence
import io.github.npauloj.mibosmart.app.resources.lock_enable_remote_open_failed
import io.github.npauloj.mibosmart.app.resources.lock_error_expired
import io.github.npauloj.mibosmart.app.resources.lock_error_failed
import io.github.npauloj.mibosmart.app.resources.lock_error_offline
import io.github.npauloj.mibosmart.app.resources.lock_error_rejected
import io.github.npauloj.mibosmart.app.resources.lock_error_unexpected
import io.github.npauloj.mibosmart.app.resources.lock_error_write_refused
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
import io.github.npauloj.mibosmart.app.resources.lock_volume_changing
import io.github.npauloj.mibosmart.app.resources.lock_volume_failed
import io.github.npauloj.mibosmart.app.resources.lock_volume_high
import io.github.npauloj.mibosmart.app.resources.lock_volume_label
import io.github.npauloj.mibosmart.app.resources.lock_volume_low
import io.github.npauloj.mibosmart.app.resources.lock_volume_medium
import io.github.npauloj.mibosmart.app.resources.lock_volume_mute
import io.github.npauloj.mibosmart.app.resources.lock_writes_disabled
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
 * Reading a lock (SPEC L1, L2, L5, L8) and its two writes (SPEC L2, L7): three parallel reads on
 * entry, then the door's state, a volume the user can change, and — when remote opening is off —
 * what that means plus the one action that grants it.
 *
 * Opening and closing are still L-02's. Every control here follows the partner rather than leading
 * it: nothing on screen moves until the call it stands for has answered.
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
        onChangeVolume = viewModel::changeVolume,
        onEnableRemoteOpen = viewModel::enableRemoteOpen,
        onBack = onBack,
        modifier = modifier,
    )
}

/** The screen as a pure function of its state, so every state has a preview and no ViewModel. */
@Composable
fun LockScreenContent(
    state: LockUiState,
    onRetry: () -> Unit,
    onChangeVolume: (VolumeLevel) -> Unit,
    onEnableRemoteOpen: () -> Unit,
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
            is LockUiState.Ready -> LockReadings(state, onChangeVolume, onEnableRemoteOpen)
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
private fun LockReadings(
    state: LockUiState.Ready,
    onChangeVolume: (VolumeLevel) -> Unit,
    onEnableRemoteOpen: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LabelledValue(
            label = stringResource(Res.string.lock_state_label),
            value = stringResource(
                if (state.lock.isOpen) Res.string.lock_state_unlocked else Res.string.lock_state_locked,
            ),
        )
        VolumeSelector(state, onChangeVolume)
        if (state.isRemoteOpenDisabled) {
            RemoteOpenDisabled(state, onEnableRemoteOpen)
        }
        if (!state.areWritesEnabled) {
            Text(
                text = stringResource(Res.string.lock_writes_disabled),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/**
 * SPEC L7: four levels, and the selected one is always the level the lock reported.
 *
 * The chips are disabled while a write is in flight and in a build that may not write at all, and
 * the selection never moves on a tap — it moves when `mudar-volume` answers. A selector that jumped
 * ahead of the hardware would be telling the user the door is quieter than it is.
 */
@Composable
private fun VolumeSelector(state: LockUiState.Ready, onChangeVolume: (VolumeLevel) -> Unit) {
    val changing = state.writeInFlight as? LockWrite.Volume
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = stringResource(Res.string.lock_volume_label), style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VolumeLevel.entries.forEach { level ->
                FilterChip(
                    selected = level == state.lock.volume,
                    onClick = { onChangeVolume(level) },
                    enabled = state.areWritesEnabled && state.writeInFlight == null,
                    label = { Text(stringResource(level.label)) },
                )
            }
        }
        if (changing != null) {
            Text(
                text = stringResource(Res.string.lock_volume_changing, stringResource(changing.level.label)),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        state.writeFailure?.takeIf { it.write is LockWrite.Volume }?.let { failure ->
            WriteFailureText(Res.string.lock_volume_failed, failure)
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
 * SPEC L2: the precondition explained, and the one action in the app that can grant it.
 *
 * The action is labelled and says what it does to the door before the user takes it. It is never
 * offered in passing, never implied by another control, and never reversed here: the app enables
 * remote opening and has no way to disable it again.
 *
 * In a build that may not write, the button is replaced by the explanation of what it would have
 * done. That is deliberate: a control that only looks disabled invites a second tap, a sentence
 * does not.
 */
@Composable
private fun RemoteOpenDisabled(state: LockUiState.Ready, onEnableRemoteOpen: () -> Unit) {
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
            Text(
                text = stringResource(Res.string.lock_enable_remote_open_consequence),
                style = MaterialTheme.typography.bodySmall,
            )
            if (state.areWritesEnabled) {
                Button(onClick = onEnableRemoteOpen, enabled = state.writeInFlight == null) {
                    Text(stringResource(Res.string.lock_enable_remote_open))
                }
            }
            state.writeFailure?.takeIf { it.write == LockWrite.RemoteOpen }?.let { failure ->
                WriteFailureText(Res.string.lock_enable_remote_open_failed, failure)
            }
        }
    }
}

/**
 * Why a write did not happen, beside the control it belongs to (SPEC U6).
 *
 * The partner's own sentence wins when there is one (SPEC S3.1); otherwise the category's message.
 */
@Composable
private fun WriteFailureText(template: StringResource, failure: WriteFailure) {
    Text(
        text = stringResource(template, failure.serverMessage ?: stringResource(failure.error.writeMessage)),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
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

/**
 * The same categories, as the half-sentence a failed **write** ends with (SPEC U6).
 *
 * Only [LockError.Failed] differs, and it has to: its reading sentence is "the lock could not be
 * read", which is the wrong story entirely when what failed was a command.
 */
private val LockError.writeMessage: StringResource
    get() = if (this == LockError.Failed) Res.string.lock_error_write_refused else message

/** One friendly sentence per category, never the server's own words (SPEC U6). */
private val LockError.message: StringResource
    get() = when (this) {
        LockError.TokenRejected -> Res.string.lock_error_rejected
        LockError.TokenExpired -> Res.string.lock_error_expired
        LockError.Offline -> Res.string.lock_error_offline
        LockError.UnexpectedResponse -> Res.string.lock_error_unexpected
        LockError.Failed -> Res.string.lock_error_failed
    }
