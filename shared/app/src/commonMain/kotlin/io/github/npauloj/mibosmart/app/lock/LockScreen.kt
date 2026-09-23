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
 * What the device list hands the lock destination: the row it was opened from — which is where the
 * lock's name and its online/offline presence come from (SPEC U3) — and how the partner addresses
 * that lock (`docs/api-contract.md` §5).
 *
 * The edge that selects a lock belongs to the device list, which is a slice of its own; this slice
 * defines the destination and what it needs.
 */
data class LockDestination(val device: Device, val address: LockAddress)

/**
 * The two halves of one lock: what it is doing now, and what it has been doing.
 *
 * They are tabs and not two destinations because they are two views of the same device — and
 * because each has its own request budget to keep: switching tabs must never re-read either side
 * (SPEC L9, ADR-006), which is what makes the two ViewModels behind them worth having.
 */
private enum class LockTab { Lock, History }

/**
 * The lock screen (SPEC L1–L10): three parallel reads on entry, then the door's state with the
 * control that opens and closes it, a volume the user can change, — when remote opening is off —
 * what that means plus the one action that grants it, and the history of its openings on a tab.
 *
 * Every control here follows the partner rather than leading it: nothing on screen moves until the
 * call it stands for has answered, and a command moves it only once `status-abertura` has agreed
 * (SPEC L3). The screen never asks twice on its own — "Verificar" is the only second read, and it
 * takes a tap (SPEC L4, ADR-006).
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

            // SPEC L9: entering this tab is what spends the one `historico-abertura` request, and
            // coming back to it spends none — the guard is in `OpeningHistoryViewModel`.
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

        // The rail is the command's tone, not the door's: "Fechada" and "Aberta" are equally
        // legitimate and neither is coloured. What earns a colour is the app not knowing — a command
        // sent and unconfirmed — or a failure (ADR-021).
        StateRail(tone = state.tone()) {
            when (state) {
                is LockUiState.Loading -> LoadingRow(Res.string.lock_loading)
                is LockUiState.Ready ->
                    LockReadings(state, null, onCommand, onVerify, onChangeVolume, onEnableRemoteOpen)

                // A command in flight, unconfirmed or refused is drawn *over* the readings it is
                // happening to (SPEC L3–L5): the same screen, plus one line saying where the door is.
                is LockUiState.Commanding ->
                    LockReadings(state.before, state, onCommand, onVerify, onChangeVolume, onEnableRemoteOpen)

                is LockUiState.Failed -> ErrorSection(state, onRetry)
            }
        }
    }
}

/**
 * A spinner and the sentence that says what is being waited for.
 *
 * [label] is a parameter because both tabs of this screen wait on the partner and neither may say
 * the other's sentence: "Lendo o estado da fechadura" while the history loads would name the wrong
 * request (SPEC L1, L9).
 */
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

/**
 * Everything read off the lock, plus whatever a command is doing to it right now.
 *
 * @param commanding the unsettled command, when there is one. It is passed in rather than read off
 *   [state] because the readings and the command are two different facts about the same lock, and
 *   only one of them is what the partner last confirmed.
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
    // SPEC L6: while the door is being commanded nothing else on the screen may be asked for either
    // — one write at a time to one lock.
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
 * SPEC L3–L6: one button for the command the door is not in, and one line for where that command is.
 *
 * The button is the whole control — a door has two states and the screen already says which one it
 * is in, so a second button that does nothing would only be there to be tapped by mistake. It is
 * disabled whenever [LockUiState.Ready.canCommand] says the command cannot be sent (no remote
 * opening, an offline lock, a build that does not write) and while one is already in flight, which
 * is the affordance half of the re-entrancy guard; the guard itself is in the ViewModel.
 *
 * The line below it never claims the door moved. `CommandSent` says the command is out,
 * `CommandExpired` says it was not confirmed and offers the only honest next step, and
 * `CommandFailed` says it did not leave at all.
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
                // The partner's own sentence wins when it sent one (SPEC S3.1).
                cause = commanding.serverMessage ?: stringResource(commanding.error.writeMessage),
            )
        }
    }
}

/**
 * SPEC L4: the state this slice exists for — the command was taken and the device never agreed.
 *
 * "Verificar" is the only thing in the app that reads a lock a second time, and it is a tap: a
 * screen that checked on its own would spend the account's budget on a door the user has already
 * walked away from (ADR-006).
 */
@Composable
private fun UnconfirmedCommand(state: LockUiState.CommandExpired, onVerify: () -> Unit) {
    // `Waiting`, not `Failed`, and this is the distinction the whole screen exists to make: the
    // command left, the door never answered, and the app does not know. Calling that a failure would
    // claim the door refused — which is exactly what nobody can tell from here (ADR-021). It read as
    // an error until a golden showed the rail amber beside a red notice, disagreeing about one state.
    CommandNotice(Res.string.lock_command_expired, StateTone.Waiting)
    TextButton(onClick = onVerify, enabled = !state.isChecking) {
        Text(
            stringResource(
                if (state.isChecking) Res.string.lock_command_checking else Res.string.lock_command_verify,
            ),
        )
    }
    // The check is a read, so it ends in a read's sentence: "a fechadura recusou o comando" would be
    // the wrong story for a request that never asked the door for anything.
    state.checkFailure?.let { error ->
        // The *check* did fail — that is a request that did not come back, not an unconfirmed door.
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

/**
 * Where the door is, as the biggest thing on the screen.
 *
 * It used to be a label/value row, the same weight as "Volume". But it is the one fact a person opens
 * this screen to read, and the timestamp beside it is what makes the fact trustworthy: a state with no
 * age is a guess. The age is set in [TabularSmall] because it is data the partner reported, not
 * something the app is saying.
 */
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
        // Not monospace: this reads "há 5 min" or "nunca vista online" — a sentence about the
        // reading, not the reading itself. Setting prose in a data face would say the wrong thing
        // about what it is.
        Text(
            text = state.lastSeen.asText(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The tone the rail carries. Only what the app does not know, or could not do, is coloured.
 */
private fun LockUiState.tone(): StateTone = when (this) {
    is LockUiState.Loading -> StateTone.Settled
    is LockUiState.Ready -> StateTone.Settled
    is LockUiState.CommandSent -> StateTone.Waiting
    is LockUiState.CommandExpired -> StateTone.Waiting
    is LockUiState.CommandFailed -> StateTone.Failed
    is LockUiState.Failed -> StateTone.Failed
}

/**
 * SPEC L7: four levels, and the selected one is always the level the lock reported.
 *
 * The chips are disabled while a write is in flight and in a build that may not write at all, and
 * the selection never moves on a tap — it moves when `mudar-volume` answers. A selector that jumped
 * ahead of the hardware would be telling the user the door is quieter than it is.
 */
@Composable
private fun VolumeSelector(
    state: LockUiState.Ready,
    isEnabled: Boolean,
    onChangeVolume: (VolumeLevel) -> Unit,
) {
    val changing = state.writeInFlight as? LockWrite.Volume
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = stringResource(Res.string.lock_volume_label), style = MaterialTheme.typography.bodyMedium)
        // A null volume is the lock not having answered, which is a different thing from every level
        // being wrong. Writing is refused in that case on purpose: `mudar-volume` with no idea of the
        // current level would be the app deciding for hardware it has not heard from.
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
            // `Waiting`, not `Failed`: the door's own state read fine and is on screen above. Painting
            // this red would tell the user the screen is broken when one secondary reading is missing
            // (ADR-026).
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

/**
 * The command as the label of the button that sends it (SPEC L3).
 *
 * It is the verb, not the state: the button on a closed door says "Abrir", because what the user is
 * choosing is the change, and the door's current state is already on the line above.
 */
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

/**
 * The same categories, as the half-sentence a failed **write** ends with (SPEC U6).
 *
 * Only [LockError.Failed] differs, and it has to: its reading sentence is "the lock could not be
 * read", which is the wrong story entirely when what failed was a command.
 */
private val LockError.writeMessage: StringResource
    get() = if (this == LockError.Failed) Res.string.lock_error_write_refused else message

/**
 * One friendly sentence per category, never the server's own words (SPEC U6).
 *
 * Internal rather than private because the history tab fails in exactly these five ways and has to
 * say the same five things: a read of the same lock, refused for the same reasons (SPEC L9).
 */
internal val LockError.message: StringResource
    get() = when (this) {
        LockError.TokenRejected -> Res.string.lock_error_rejected
        LockError.TokenExpired -> Res.string.lock_error_expired
        LockError.Offline -> Res.string.lock_error_offline
        LockError.UnexpectedResponse -> Res.string.lock_error_unexpected
        LockError.Failed -> Res.string.lock_error_failed
    }
