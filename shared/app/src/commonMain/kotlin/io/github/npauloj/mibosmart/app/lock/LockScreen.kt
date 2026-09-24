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
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.npauloj.mibosmart.app.resources.Res
import io.github.npauloj.mibosmart.app.resources.lock_back
import io.github.npauloj.mibosmart.app.resources.lock_command_check_failed
import io.github.npauloj.mibosmart.app.resources.lock_command_checking
import io.github.npauloj.mibosmart.app.resources.lock_command_close
import io.github.npauloj.mibosmart.app.resources.lock_command_expired
import io.github.npauloj.mibosmart.app.resources.lock_command_failed
import io.github.npauloj.mibosmart.app.resources.lock_command_open
import io.github.npauloj.mibosmart.app.resources.lock_command_sent
import io.github.npauloj.mibosmart.app.resources.lock_command_verify
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
import io.github.npauloj.mibosmart.app.resources.lock_tab_history
import io.github.npauloj.mibosmart.app.resources.lock_tab_lock
import io.github.npauloj.mibosmart.app.resources.lock_volume_changing
import io.github.npauloj.mibosmart.app.resources.lock_volume_failed
import io.github.npauloj.mibosmart.app.resources.lock_volume_high
import io.github.npauloj.mibosmart.app.resources.lock_volume_label
import io.github.npauloj.mibosmart.app.resources.lock_volume_unavailable
import io.github.npauloj.mibosmart.app.resources.lock_volume_low
import io.github.npauloj.mibosmart.app.resources.lock_volume_medium
import io.github.npauloj.mibosmart.app.resources.lock_volume_mute
import io.github.npauloj.mibosmart.app.resources.lock_writes_disabled
import io.github.npauloj.mibosmart.app.ui.StateNotice
import io.github.npauloj.mibosmart.app.ui.StateRail
import io.github.npauloj.mibosmart.app.ui.StateTone
import io.github.npauloj.mibosmart.domain.device.Device
import io.github.npauloj.mibosmart.domain.lock.LockAddress
import io.github.npauloj.mibosmart.domain.lock.LockCommand
import io.github.npauloj.mibosmart.domain.lock.VolumeLevel
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * What the device list hands the lock destination: the row it was opened from — which is where
 * the lock's name and its online/offline presence come from (SPEC U3) — and how the partner
 * addresses that lock (`docs/api-contract.md` §5).
 */
data class LockDestination(val device: Device, val address: LockAddress)

/** The two halves of one lock: what it is doing now, and what it has been doing. */
private enum class LockTab { Lock, History }

/**
 * The lock screen (SPEC L1–L10): three parallel reads on entry, then the door's state with the
 * control that opens and closes it, a volume the user can change, — when remote opening is off
 * — what that means plus the one action that grants it, and the history of its openings on a
 * tab.
 */
@Composable
fun LockScreen(
    destination: LockDestination,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LockViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(destination) { viewModel.open(destination) }

    var tab by remember { mutableStateOf(LockTab.Lock) }
    Column(modifier = modifier.fillMaxSize()) {
        LockTabs(selected = tab, onSelect = { tab = it })
        when (tab) {
            LockTab.Lock -> LockScreenContent(
                state = state,
                onRetry = viewModel::retry,
                onCommand = viewModel::command,
                onVerify = viewModel::verify,
                onChangeVolume = viewModel::changeVolume,
                onEnableRemoteOpen = viewModel::enableRemoteOpen,
                onBack = onBack,
                modifier = Modifier.weight(1f),
            )

            LockTab.History -> OpeningHistoryScreen(
                address = destination.address,
                onBack = onBack,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Which half of the lock is on screen (SPEC L9). */
@Composable
private fun LockTabs(selected: LockTab, onSelect: (LockTab) -> Unit) {
    PrimaryTabRow(selectedTabIndex = selected.ordinal) {
        LockTab.entries.forEach { tab ->
            Tab(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                text = { Text(stringResource(tab.label)) },
            )
        }
    }
}

private val LockTab.label: StringResource
    get() = when (this) {
        LockTab.Lock -> Res.string.lock_tab_lock
        LockTab.History -> Res.string.lock_tab_history
    }

/** The screen as a pure function of its state, so every state has a preview and no ViewModel. */
@Composable
fun LockScreenContent(
    state: LockUiState,
    onRetry: () -> Unit,
    onCommand: (LockCommand) -> Unit,
    onVerify: () -> Unit,
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

        StateRail(tone = state.tone()) {
            when (state) {
                is LockUiState.Loading -> LoadingRow(Res.string.lock_loading)
                is LockUiState.Ready ->
                    LockReadings(state, null, onCommand, onVerify, onChangeVolume, onEnableRemoteOpen)

                is LockUiState.Commanding ->
                    LockReadings(state.before, state, onCommand, onVerify, onChangeVolume, onEnableRemoteOpen)

                is LockUiState.Failed -> ErrorSection(state, onRetry)
            }
        }
    }
}

/** A spinner and the sentence that says what is being waited for. */
@Composable
internal fun LoadingRow(label: StringResource) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Text(text = stringResource(label), style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * The device list reported the lock offline, so what follows is the last thing the hub knew
 * about it and its age (SPEC L5, U3) — shown above the readings rather than instead of them.
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

/**
 * Everything read off the lock, plus whatever a command is doing to it right now.
 * @param commanding the unsettled command, when there is one.
 */
@Composable
private fun LockReadings(
    state: LockUiState.Ready,
    commanding: LockUiState.Commanding?,
    onCommand: (LockCommand) -> Unit,
    onVerify: () -> Unit,
    onChangeVolume: (VolumeLevel) -> Unit,
    onEnableRemoteOpen: () -> Unit,
) {
    val isCommandInFlight = commanding is LockUiState.CommandSent
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DoorState(state)
        CommandControl(state, commanding, isCommandInFlight, onCommand, onVerify)
        VolumeSelector(state, !isCommandInFlight, onChangeVolume)
        if (state.isRemoteOpenDisabled) {
            RemoteOpenDisabled(state, !isCommandInFlight, onEnableRemoteOpen)
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
 * SPEC L3–L6: one button for the command the door is not in, and one line for where that
 * command is.
 */
@Composable
private fun CommandControl(
    state: LockUiState.Ready,
    commanding: LockUiState.Commanding?,
    isCommandInFlight: Boolean,
    onCommand: (LockCommand) -> Unit,
    onVerify: () -> Unit,
) {
    val command = LockCommand.toggling(state.lock)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = { onCommand(command) },
            enabled = state.canCommand && !isCommandInFlight,
        ) {
            Text(stringResource(command.label))
        }
        when (commanding) {
            null -> Unit
            is LockUiState.CommandSent -> CommandNotice(Res.string.lock_command_sent, StateTone.Waiting)
            is LockUiState.CommandExpired -> UnconfirmedCommand(commanding, onVerify)
            is LockUiState.CommandFailed -> CommandNotice(
                template = Res.string.lock_command_failed,
                tone = StateTone.Failed,
                cause = commanding.serverMessage ?: stringResource(commanding.error.writeMessage),
            )
        }
    }
}

/**
 * SPEC L4: the state this slice exists for — the command was taken and the device never agreed.
 */
@Composable
private fun UnconfirmedCommand(state: LockUiState.CommandExpired, onVerify: () -> Unit) {
    CommandNotice(Res.string.lock_command_expired, StateTone.Waiting)
    TextButton(onClick = onVerify, enabled = !state.isChecking) {
        Text(
            stringResource(
                if (state.isChecking) Res.string.lock_command_checking else Res.string.lock_command_verify,
            ),
        )
    }
    state.checkFailure?.let { error ->
        CommandNotice(
            template = Res.string.lock_command_check_failed,
            tone = StateTone.Failed,
            cause = stringResource(error.message),
        )
    }
}

/** One sentence about where the command is, in the colour its news deserves. */
@Composable
private fun CommandNotice(
    template: StringResource,
    tone: StateTone,
    cause: String? = null,
) {
    StateNotice(
        tone = tone,
        text = if (cause == null) stringResource(template) else stringResource(template, cause),
    )
}

/** Where the door is, as the biggest thing on the screen. */
@Composable
private fun DoorState(state: LockUiState.Ready) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = stringResource(Res.string.lock_state_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(
                if (state.lock.isOpen) Res.string.lock_state_unlocked else Res.string.lock_state_locked,
            ),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = state.lastSeen.asText(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The tone the rail carries. Only what the app does not know, or could not do, is coloured. */
private fun LockUiState.tone(): StateTone = when (this) {
    is LockUiState.Loading -> StateTone.Settled
    is LockUiState.Ready -> StateTone.Settled
    is LockUiState.CommandSent -> StateTone.Waiting
    is LockUiState.CommandExpired -> StateTone.Waiting
    is LockUiState.CommandFailed -> StateTone.Failed
    is LockUiState.Failed -> StateTone.Failed
}

/** SPEC L7: four levels, and the selected one is always the level the lock reported. */
@Composable
private fun VolumeSelector(
    state: LockUiState.Ready,
    isEnabled: Boolean,
    onChangeVolume: (VolumeLevel) -> Unit,
) {
    val changing = state.writeInFlight as? LockWrite.Volume
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = stringResource(Res.string.lock_volume_label), style = MaterialTheme.typography.bodyMedium)
        val isKnown = state.lock.volume != null
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VolumeLevel.entries.forEach { level ->
                FilterChip(
                    selected = level == state.lock.volume,
                    onClick = { onChangeVolume(level) },
                    enabled = isKnown && isEnabled && state.areWritesEnabled && state.writeInFlight == null,
                    label = { Text(stringResource(level.label)) },
                )
            }
        }
        if (!isKnown) {
            StateNotice(tone = StateTone.Waiting, text = stringResource(Res.string.lock_volume_unavailable))
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

/** SPEC L2: the precondition explained, and the one action in the app that can grant it. */
@Composable
private fun RemoteOpenDisabled(
    state: LockUiState.Ready,
    isEnabled: Boolean,
    onEnableRemoteOpen: () -> Unit,
) {
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
                Button(onClick = onEnableRemoteOpen, enabled = isEnabled && state.writeInFlight == null) {
                    Text(stringResource(Res.string.lock_enable_remote_open))
                }
            }
            state.writeFailure?.takeIf { it.write == LockWrite.RemoteOpen }?.let { failure ->
                WriteFailureText(Res.string.lock_enable_remote_open_failed, failure)
            }
        }
    }
}

/** Why a write did not happen, beside the control it belongs to (SPEC U6). */
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

/** The command as the label of the button that sends it (SPEC L3). */
private val LockCommand.label: StringResource
    get() = when (this) {
        LockCommand.Open -> Res.string.lock_command_open
        LockCommand.Close -> Res.string.lock_command_close
    }

/** The 0..3 the partner speaks, as the words the user reads (SPEC L7). */
private val VolumeLevel.label: StringResource
    get() = when (this) {
        VolumeLevel.Mute -> Res.string.lock_volume_mute
        VolumeLevel.Low -> Res.string.lock_volume_low
        VolumeLevel.Medium -> Res.string.lock_volume_medium
        VolumeLevel.High -> Res.string.lock_volume_high
    }

/** The same categories, as the half-sentence a failed **write** ends with (SPEC U6). */
private val LockError.writeMessage: StringResource
    get() = if (this == LockError.Failed) Res.string.lock_error_write_refused else message

/** One friendly sentence per category, never the server's own words (SPEC U6). */
internal val LockError.message: StringResource
    get() = when (this) {
        LockError.TokenRejected -> Res.string.lock_error_rejected
        LockError.TokenExpired -> Res.string.lock_error_expired
        LockError.Offline -> Res.string.lock_error_offline
        LockError.UnexpectedResponse -> Res.string.lock_error_unexpected
        LockError.Failed -> Res.string.lock_error_failed
    }
