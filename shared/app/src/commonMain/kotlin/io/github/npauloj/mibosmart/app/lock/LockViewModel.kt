package io.github.npauloj.mibosmart.app.lock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.npauloj.mibosmart.domain.lock.LockCommand
import io.github.npauloj.mibosmart.domain.lock.LockState
import io.github.npauloj.mibosmart.domain.lock.VolumeLevel
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Everything the lock screen shows, in one immutable value, as a sealed hardware state
 * (ADR-003).
 */
sealed interface LockUiState {

    /** Known before any read answers: the row the screen was opened from carries it. */
    val deviceName: String

    /** How long ago the lock was seen — set only while the device list reports it offline (U3). */
    val lastSeen: LastSeen?

    /** The lock is offline, so what is on screen is the last thing known about it (SPEC L5). */
    val isOffline: Boolean get() = lastSeen != null

    /** The three reads of SPEC L1 are in flight. */
    data class Loading(
        override val deviceName: String,
        override val lastSeen: LastSeen? = null,
    ) : LockUiState

    /**
     * All three reads answered.
     * @property areWritesEnabled whether this build may write to the lock at all
     * ([LockWritesSwitch]).
     * @property writeInFlight the write waiting for the partner, if any.
     * @property writeFailure the last write that did not happen, with the reason to show (SPEC
     * U6).
     */
    data class Ready(
        override val deviceName: String,
        val lock: LockState,
        override val lastSeen: LastSeen? = null,
        val areWritesEnabled: Boolean = true,
        val writeInFlight: LockWrite? = null,
        val writeFailure: WriteFailure? = null,
    ) : LockUiState {

        /** The lock refuses commands from the app until remote opening is granted (SPEC L2). */
        val isRemoteOpenDisabled: Boolean get() = !lock.isRemoteOpenEnabled

        /** Whether an open/close command may be sent at all (SPEC L2, L5, and the build's switch). */
        val canCommand: Boolean
            get() = areWritesEnabled && !isRemoteOpenDisabled && !isOffline && writeInFlight == null
    }

    /** A command the user sent, in one of the phases where the door has not settled (SPEC §4). */
    sealed interface Commanding : LockUiState {

        /** The lock as it was last read: what a failed command restores to (SPEC L5). */
        val before: Ready

        /** What was asked of the door, so every phase can name it in the user's own words. */
        val command: LockCommand

        override val deviceName: String get() = before.deviceName

        override val lastSeen: LastSeen? get() = before.lastSeen
    }

    /** `controle-fechadura` is in flight, or its confirmation is: the control is dead (SPEC L3, L6). */
    data class CommandSent(
        override val before: Ready,
        override val command: LockCommand,
    ) : Commanding

    /**
     * The command was taken and the device never agreed — a disagreeing read, or none in 10 s
     * (SPEC L4).
     * @property isChecking a "Verificar" read is in flight; the action is disabled while it is.
     * @property checkFailure why the last "Verificar" could not answer (SPEC U6).
     */
    data class CommandExpired(
        override val before: Ready,
        override val command: LockCommand,
        val isChecking: Boolean = false,
        val checkFailure: LockError? = null,
    ) : Commanding

    /**
     * The command did not reach the lock, so the screen goes back to what it was showing (SPEC
     * L5).
     */
    data class CommandFailed(
        override val before: Ready,
        override val command: LockCommand,
        val error: LockError,
        val serverMessage: String? = null,
    ) : Commanding

    /**
     * No reading: one named cause and one action (SPEC U6).
     * @property serverMessage the partner's own sentence, when the failure carried one worth
     * showing (SPEC S3.1).
     */
    data class Failed(
        override val deviceName: String,
        val error: LockError,
        val serverMessage: String? = null,
        override val lastSeen: LastSeen? = null,
    ) : LockUiState
}

/** The reasons a read can fail, one user-facing message each (SPEC E2, U6, ADR-012). */
enum class LockError { TokenRejected, TokenExpired, Offline, UnexpectedResponse, Failed }

/** The two things this slice can ask a lock to change (SPEC L2, L7). */
sealed interface LockWrite {

    /** Setting the level the lock announces itself at — `mudar-volume`. */
    data class Volume(val level: VolumeLevel) : LockWrite

    /** Granting the app the right to command the lock — `habilitar-abrir-remoto`. */
    data object RemoteOpen : LockWrite
}

/**
 * A write that did not happen, named so the user can tell which control failed and why (SPEC
 * U6).
 * @property serverMessage the partner's own sentence when it sent one worth showing (SPEC
 * S3.1).
 */
data class WriteFailure(
    val write: LockWrite,
    val error: LockError,
    val serverMessage: String? = null,
)

/**
 * The lock screen: one state, and intents as suspend functions rather than a second stream
 * (ADR-003).
 */
class LockViewModel(
    private val loadLock: LoadLock,
    private val toggleLock: ToggleLock,
    private val changeLockVolume: ChangeVolume,
    private val enableLockRemoteOpen: EnableRemoteOpen,
    private val lockWrites: LockWritesSwitch,
    private val clock: Clock,
) : ViewModel() {

    private val mutableState = MutableStateFlow<LockUiState>(LockUiState.Loading(deviceName = ""))
    val state: StateFlow<LockUiState> = mutableState.asStateFlow()

    private var current: LockDestination? = null

    /** Screens call this; [onOpen] is the same intent as a suspend function, so a test can await it. */
    fun open(destination: LockDestination) {
        viewModelScope.launch { onOpen(destination) }
    }

    /** Reads the lock the screen was opened on, at most once per destination. */
    suspend fun onOpen(destination: LockDestination) {
        if (current == destination) return
        current = destination
        read(destination)
    }

    fun retry() {
        viewModelScope.launch { onRetry() }
    }

    /** The one action the error state offers (SPEC U6); it is the user's choice, never a timer (E5). */
    suspend fun onRetry() {
        read(current ?: return)
    }

    fun command(command: LockCommand) {
        viewModelScope.launch { onCommand(command) }
    }

    /** SPEC L3–L6: the whole confirmation path, in the order the user experiences it. */
    suspend fun onCommand(command: LockCommand) {
        val address = current?.address ?: return
        val ready = commandableLock() ?: return
        mutableState.value = LockUiState.CommandSent(ready, command)
        announce(LockUiMapper.afterCommand(ready, command, toggleLock(address, command, ready.lock)))
    }

    fun verify() {
        viewModelScope.launch { onVerify() }
    }

    /**
     * SPEC L4's "Verificar": **exactly one** more `status-abertura`, and only from an
     * unconfirmed command.
     */
    suspend fun onVerify() {
        val address = current?.address ?: return
        val expired = mutableState.value as? LockUiState.CommandExpired ?: return
        if (expired.isChecking) return
        val checking = expired.copy(isChecking = true, checkFailure = null)
        mutableState.value = checking
        mutableState.value = LockUiMapper.afterVerifying(
            checking,
            toggleLock.verify(address, checking.command, checking.before.lock),
        )
    }

    fun changeVolume(level: VolumeLevel) {
        viewModelScope.launch { onChangeVolume(level) }
    }

    /** SPEC L7: the partner decides, then the screen shows it. */
    suspend fun onChangeVolume(level: VolumeLevel) {
        val address = current?.address ?: return
        val ready = writableLock() ?: return
        if (ready.lock.volume == level) return
        mutableState.value = ready.copy(writeInFlight = LockWrite.Volume(level), writeFailure = null)
        mutableState.value = LockUiMapper.afterVolumeChange(ready, level, changeLockVolume(address, level))
    }

    fun enableRemoteOpen() {
        viewModelScope.launch { onEnableRemoteOpen() }
    }

    /** SPEC L2, action half: **the only path in the app that grants remote opening.** */
    suspend fun onEnableRemoteOpen() {
        val address = current?.address ?: return
        val ready = writableLock() ?: return
        mutableState.value = ready.copy(writeInFlight = LockWrite.RemoteOpen, writeFailure = null)
        mutableState.value = LockUiMapper.afterEnablingRemoteOpen(ready, enableLockRemoteOpen(address))
    }

    /** The lock a write may act on: a loaded one with nothing already in flight. */
    private fun writableLock(): LockUiState.Ready? =
        (mutableState.value as? LockUiState.Ready)?.takeIf { it.writeInFlight == null }

    /** The lock a command may act on — the re-entrancy guard of SPEC L6, in the ViewModel's state. */
    private fun commandableLock(): LockUiState.Ready? {
        val readings = when (val state = mutableState.value) {
            is LockUiState.Ready -> state
            is LockUiState.CommandFailed -> state.before
            is LockUiState.CommandExpired -> state.before.takeIf { !state.isChecking }
            else -> null
        }
        return readings?.takeIf { it.canCommand }
    }

    /** Publishes [next] and, when it is a failure notice, takes it back down (SPEC L5). */
    private suspend fun announce(next: LockUiState) {
        mutableState.value = next
        if (next !is LockUiState.CommandFailed) return
        delay(FAILURE_NOTICE)
        if (mutableState.value === next) mutableState.value = next.before
    }

    private suspend fun read(destination: LockDestination) {
        mutableState.value = LockUiMapper.loading(destination, clock.now())
        val result = loadLock(destination.address)
        mutableState.value = LockUiMapper.toUiState(destination, result, clock.now(), lockWrites.isOn)
    }

    private companion object {

        /** How long [LockUiState.CommandFailed] stays on screen before the readings come back. */
        val FAILURE_NOTICE = 4.seconds
    }
}
