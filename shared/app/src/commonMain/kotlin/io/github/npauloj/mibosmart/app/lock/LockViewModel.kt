package io.github.npauloj.mibosmart.app.lock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.npauloj.mibosmart.domain.lock.LockState
import io.github.npauloj.mibosmart.domain.lock.VolumeLevel
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Everything the lock screen shows, in one immutable value, as a sealed hardware state (ADR-003).
 *
 * The SPEC's `Locked`, `Unlocked`, `RemoteOpenDisabled` and `Offline` are readings of [Ready] rather
 * than alternatives to it: the screen shows the door, the volume and the precondition **together**,
 * and a lock that refuses commands is still a lock whose state is worth seeing (SPEC L2, L5).
 * What is genuinely exclusive gets its own subtype — which is how L-02's command states
 * (`CommandSent`, `Confirmed`, `CommandExpired`, `CommandFailed`) arrive: as new subtypes, never as
 * a refactor of these.
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
     *
     * @property areWritesEnabled whether this build may write to the lock at all
     *   ([LockWritesSwitch]). It is a property of the build, not of the lock, and it is in the state
     *   so the screen can say so **before** the user asks for something it will not do.
     * @property writeInFlight the write waiting for the partner, if any. Its control is disabled
     *   while it is set, and the value it would produce is deliberately **not** in [lock] yet
     *   (SPEC L7: the UI follows the API).
     * @property writeFailure the last write that did not happen, with the reason to show (SPEC U6).
     *   A new write clears it; a failed one never changes [lock].
     */
    data class Ready(
        override val deviceName: String,
        val lock: LockState,
        override val lastSeen: LastSeen? = null,
        val areWritesEnabled: Boolean = true,
        val writeInFlight: LockWrite? = null,
        val writeFailure: WriteFailure? = null,
    ) : LockUiState {

        /**
         * The lock refuses commands from the app until remote opening is granted (SPEC L2).
         *
         * Only [LockViewModel.onEnableRemoteOpen] can clear this, and only by believing a fresh read
         * of `status-abrir-remoto` — no other intent in this class touches it.
         */
        val isRemoteOpenDisabled: Boolean get() = !lock.isRemoteOpenEnabled
    }

    /**
     * No reading: one named cause and one action (SPEC U6).
     *
     * @property serverMessage the partner's own sentence, when the failure carried one worth showing
     *   (SPEC S3.1).
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

/**
 * The two things this slice can ask a lock to change (SPEC L2, L7).
 *
 * Open and close are not here: they are L-02's, and they arrive as their own subtypes rather than as
 * a rewrite of these.
 */
sealed interface LockWrite {

    /** Setting the level the lock announces itself at — `mudar-volume`. */
    data class Volume(val level: VolumeLevel) : LockWrite

    /** Granting the app the right to command the lock — `habilitar-abrir-remoto`. */
    data object RemoteOpen : LockWrite
}

/**
 * A write that did not happen, named so the user can tell which control failed and why (SPEC U6).
 *
 * @property serverMessage the partner's own sentence when it sent one worth showing (SPEC S3.1).
 */
data class WriteFailure(
    val write: LockWrite,
    val error: LockError,
    val serverMessage: String? = null,
)

/**
 * The lock screen: one state, and intents as suspend functions rather than a second stream (ADR-003).
 *
 * Reading costs three requests of the account's budget (ADR-006), so they are spent once per lock —
 * a recomposition, a rotation or a second visit to the same destination does not pay again, and only
 * [onRetry] asks the partner anything after that (SPEC E5).
 */
class LockViewModel(
    private val loadLock: LoadLock,
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

    fun changeVolume(level: VolumeLevel) {
        viewModelScope.launch { onChangeVolume(level) }
    }

    /**
     * SPEC L7: the partner decides, then the screen shows it.
     *
     * Nothing writes [LockState.volume] before [ChangeVolumeResult.Changed] arrives, so the selector
     * cannot display a level the lock is not at — not even for the length of a request.
     */
    suspend fun onChangeVolume(level: VolumeLevel) {
        val address = current?.address ?: return
        val ready = writableLock() ?: return
        // The level it is already at costs a request to change nothing (ADR-006).
        if (ready.lock.volume == level) return
        mutableState.value = ready.copy(writeInFlight = LockWrite.Volume(level), writeFailure = null)
        mutableState.value = LockUiMapper.afterVolumeChange(ready, level, changeLockVolume(address, level))
    }

    fun enableRemoteOpen() {
        viewModelScope.launch { onEnableRemoteOpen() }
    }

    /**
     * SPEC L2, action half: **the only path in the app that grants remote opening.**
     *
     * It runs when the user chooses the labelled action and never as a side effect of anything else:
     * opening the screen, retrying and changing the volume all leave the flag exactly as the partner
     * last reported it. On success the answer comes from a fresh `status-abrir-remoto` rather than
     * from the fact that the write returned; on failure the lock is left as it was, with the reason
     * beside the control that stays disabled.
     */
    suspend fun onEnableRemoteOpen() {
        val address = current?.address ?: return
        val ready = writableLock() ?: return
        mutableState.value = ready.copy(writeInFlight = LockWrite.RemoteOpen, writeFailure = null)
        mutableState.value = LockUiMapper.afterEnablingRemoteOpen(ready, enableLockRemoteOpen(address))
    }

    /**
     * The lock a write may act on: a loaded one with nothing already in flight.
     *
     * The second half is the re-entrancy guard (SPEC L6's rule, applied to this slice's writes): the
     * screen disables the control that is waiting, and this makes a second tap inert even if it did
     * not — a double write to a door is worth guarding twice.
     */
    private fun writableLock(): LockUiState.Ready? =
        (mutableState.value as? LockUiState.Ready)?.takeIf { it.writeInFlight == null }

    private suspend fun read(destination: LockDestination) {
        mutableState.value = LockUiMapper.loading(destination, clock.now())
        val result = loadLock(destination.address)
        mutableState.value = LockUiMapper.toUiState(destination, result, clock.now(), lockWrites.isOn)
    }
}
