package io.github.npauloj.mibosmart.app.lock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.npauloj.mibosmart.domain.lock.LockState
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

    /** All three reads answered. */
    data class Ready(
        override val deviceName: String,
        val lock: LockState,
        override val lastSeen: LastSeen? = null,
    ) : LockUiState {

        /**
         * The lock refuses commands from the app until remote opening is granted (SPEC L2).
         *
         * This slice only explains it. The action that enables it is L-01b's, and nothing here — no
         * intent, no retry, no read — can turn the flag on.
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
 * The lock screen: one state, and intents as suspend functions rather than a second stream (ADR-003).
 *
 * Reading costs three requests of the account's budget (ADR-006), so they are spent once per lock —
 * a recomposition, a rotation or a second visit to the same destination does not pay again, and only
 * [onRetry] asks the partner anything after that (SPEC E5).
 */
class LockViewModel(
    private val loadLock: LoadLock,
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

    private suspend fun read(destination: LockDestination) {
        mutableState.value = LockUiMapper.loading(destination, clock.now())
        val result = loadLock(destination.address)
        mutableState.value = LockUiMapper.toUiState(destination, result, clock.now())
    }
}
