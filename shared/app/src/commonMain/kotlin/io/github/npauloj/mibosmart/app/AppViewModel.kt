package io.github.npauloj.mibosmart.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.npauloj.mibosmart.app.session.SessionStartup
import io.github.npauloj.mibosmart.domain.session.Session
import io.github.npauloj.mibosmart.domain.session.SessionState
import kotlin.time.Clock
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The two places the app can be, named once so nothing else invents a route (SPEC S5). */
enum class AppDestination {

    /** The way in: paste a token and validate it (SPEC S1–S4). */
    TokenEntry,

    /** Everything a session unlocks; the device list is its first screen (SPEC D1). */
    DeviceList,
}

/**
 * Where the app is and what it has to say about the session, in one immutable value (ADR-003).
 *
 * @property destination `null` until the stored session has been read. Routing before the answer is
 *   back would mean showing the token screen to a signed-in user for a frame and then yanking it
 *   away, which is the flicker SPEC S5 exists to avoid; the read is a vault access, not a request, so
 *   the wait is a frame, not a spinner.
 * @property expiringSoon whether the device list shows the non-blocking banner of SPEC S7.
 */
data class AppUiState(
    val destination: AppDestination? = null,
    val expiringSoon: Boolean = false,
)

/**
 * Startup routing and the session's clock, as one state (ADR-003).
 *
 * The state is not `rememberSaveable`: it is derived from the vault on every start, so the app can
 * never come back "signed in" to a session the store cannot back (ADR-010).
 *
 * Deliberately no more than that: the token-identity guard, logout and the account screen are S-02b's
 * and renewal is S-03's, so the banner here has no action of its own yet.
 */
class AppViewModel(
    private val sessionStartup: SessionStartup,
    private val clock: Clock,
) : ViewModel() {

    private val mutableState = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = mutableState.asStateFlow()

    private var expiryWarning: Job? = null

    init {
        viewModelScope.launch { onStart() }
    }

    /** The token screen's one output: the partner accepted a token, so a session now exists. */
    fun onAuthenticated() {
        viewModelScope.launch { onStart() }
    }

    /**
     * Reads the stored session and routes from it; the intent is a suspend function so a test can
     * await it rather than guess at a dispatcher (ADR-003).
     */
    suspend fun onStart() {
        val session = sessionStartup()

        expiryWarning?.cancel()
        if (session == null) {
            mutableState.value = AppUiState(destination = AppDestination.TokenEntry)
            return
        }

        val sessionState = session.stateAt(clock.now())
        mutableState.value = AppUiState(
            destination = AppDestination.DeviceList,
            expiringSoon = sessionState == SessionState.ExpiringSoon,
        )
        if (sessionState == SessionState.Valid) warnWhenItExpires(session)
    }

    /**
     * Waits the session's remaining life out **once** and then shows the banner.
     *
     * One `delay` rather than a tick: the app has to warn while it is open, and the cheapest honest
     * way is to sleep until the only instant that matters. Nothing here asks the partner anything, so
     * SPEC E5 and ADR-006 are untouched — it is the local clock, not the API, that is being watched.
     */
    private fun warnWhenItExpires(session: Session) {
        expiryWarning = viewModelScope.launch {
            delay(session.remainingUntilWarning(clock.now()))
            mutableState.update { it.copy(expiringSoon = true) }
        }
    }
}
