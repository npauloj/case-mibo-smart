package io.github.npauloj.mibosmart.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.npauloj.mibosmart.app.session.SessionStartup
import io.github.npauloj.mibosmart.domain.session.RefusedRequests
import io.github.npauloj.mibosmart.domain.session.Session
import io.github.npauloj.mibosmart.domain.session.SessionEndReason
import io.github.npauloj.mibosmart.domain.session.SessionGuard
import io.github.npauloj.mibosmart.domain.session.SessionState
import io.github.npauloj.mibosmart.domain.session.TokenRefusal
import kotlin.time.Clock
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The places the app can be, named once so nothing else invents a route (SPEC S5, S8). */
enum class AppDestination {

    /** The way in: paste a token and validate it (SPEC S1–S4). */
    TokenEntry,

    /** Everything a session unlocks; the device list is its first screen (SPEC D1). */
    DeviceList,

    /** The session itself: its suffix, its deadline and "Sair" (SPEC S8, S9). */
    Account,
}

/**
 * Where the app is and what it has to say about the session, in one immutable value (ADR-003).
 *
 * @property destination `null` until the stored session has been read. Routing before the answer is
 *   back would mean showing the token screen to a signed-in user for a frame and then yanking it
 *   away, which is the flicker SPEC S5 exists to avoid; the read is a vault access, not a request, so
 *   the wait is a frame, not a spinner.
 * @property expiringSoon whether the device list shows the non-blocking banner of SPEC S7.
 * @property sessionEnded why the user is back on the token screen, when they did not ask to be
 *   (SPEC S6, U5). `null` on a cold start and after a deliberate "Sair" — neither is something to
 *   explain.
 */
data class AppUiState(
    val destination: AppDestination? = null,
    val expiringSoon: Boolean = false,
    val sessionEnded: SessionEndReason? = null,
)

/**
 * Startup routing, the session's clock, and the guard that ends a session the partner has refused
 * (ADR-003).
 *
 * The state is not `rememberSaveable`: it is derived from the vault on every start, so the app can
 * never come back "signed in" to a session the store cannot back (ADR-010).
 */
class AppViewModel(
    private val sessionStartup: SessionStartup,
    private val sessionGuard: SessionGuard,
    private val refusedRequests: RefusedRequests,
    private val clock: Clock,
) : ViewModel() {

    private val mutableState = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = mutableState.asStateFlow()

    private var expiryWarning: Job? = null

    /**
     * Where a newly validated token puts the user (SPEC U5).
     *
     * It is the screen the guard interrupted, so an expiry that arrives on the account screen ends
     * back on the account screen rather than dumping the user at the list they had left.
     */
    private var returnTo: AppDestination = AppDestination.DeviceList

    init {
        viewModelScope.launch { onStart() }
        // Collected for the ViewModel's whole life, not per screen: a refusal can arrive from any
        // request, and the screen that sent it may already be gone (SPEC S6).
        viewModelScope.launch { refusedRequests.refusals.collect { onRefusal(it) } }
    }

    /** The token screen's one output: the partner accepted a token, so a session now exists. */
    fun onAuthenticated() {
        viewModelScope.launch { onStart() }
    }

    /** The account screen, reached from the device list and left the same way (SPEC S8). */
    fun openAccount() {
        mutableState.update { it.copy(destination = AppDestination.Account) }
    }

    fun closeAccount() {
        mutableState.update { it.copy(destination = AppDestination.DeviceList) }
    }

    /**
     * "Sair" has already emptied the vault (SPEC S8): all that is left is the way out of the screens
     * it backed, with nothing to explain — the user asked for this.
     */
    fun onSignedOut() {
        expiryWarning?.cancel()
        returnTo = AppDestination.DeviceList
        mutableState.value = AppUiState(destination = AppDestination.TokenEntry)
    }

    /**
     * Reads the stored session and routes from it; the intent is a suspend function so a test can
     * await it rather than guess at a dispatcher (ADR-003).
     */
    suspend fun onStart() {
        val session = sessionStartup()

        expiryWarning?.cancel()
        if (session == null) {
            // The reason survives: this is the path a re-validation that failed comes back through,
            // and the user still has to be told why they are here (SPEC U5).
            mutableState.update { AppUiState(destination = AppDestination.TokenEntry, sessionEnded = it.sessionEnded) }
            return
        }

        val sessionState = session.stateAt(clock.now())
        // Back to where the guard found them, and the reason is spent (SPEC U5).
        mutableState.value = AppUiState(
            destination = returnTo,
            expiringSoon = sessionState == SessionState.ExpiringSoon,
        )
        returnTo = AppDestination.DeviceList
        if (sessionState == SessionState.Valid) warnWhenItExpires(session)
    }

    /**
     * A request came back refused: end the session if it was this one's, and say so (SPEC S6, U5).
     *
     * The decision is the guard's, not this class's — including the one case that must change
     * nothing, a refusal carrying a token the vault no longer holds.
     */
    suspend fun onRefusal(refusal: TokenRefusal) {
        // The token screen is never a place to come back *to*: a refusal that arrives while the app
        // is already there (a request that outlived the screen) still returns the user to the list.
        val signedInAt = mutableState.value.destination
            ?.takeUnless { it == AppDestination.TokenEntry }
            ?: AppDestination.DeviceList
        val ended = sessionGuard.onRefusal(refusal, returnTo = signedInAt) ?: return

        expiryWarning?.cancel()
        returnTo = ended.returnTo
        mutableState.value = AppUiState(
            destination = AppDestination.TokenEntry,
            sessionEnded = ended.reason,
        )
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
