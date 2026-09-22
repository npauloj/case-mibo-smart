package io.github.npauloj.mibosmart.app.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.npauloj.mibosmart.data.remote.RequestCounter
import io.github.npauloj.mibosmart.domain.session.Session
import io.github.npauloj.mibosmart.domain.session.SessionState
import kotlin.coroutines.cancellation.CancellationException
import kotlin.jvm.JvmInline
import kotlin.time.Clock
import kotlin.time.Duration
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Whether this is a developer's build: `local.properties` → `BuildConfig.DEBUG` → here.
 *
 * It gates the request counter and nothing else. ADR-006 wants the budget visible while the case is
 * being built, and invisible to whoever opens the delivered app — a number nobody outside the team
 * can act on is noise on a screen that exists to be calm.
 */
@JvmInline
value class DebugBuild(val isOn: Boolean)

/**
 * How much life the session has left, in the granularity the account screen renders (SPEC S7, S6).
 *
 * Split into cases rather than carried as a [Duration] because the screen does three different things
 * with it, and "negative duration" is not a thing a `when` reads well.
 */
sealed interface SessionExpiry {

    /** The vault has not answered yet; the screen shows the frame and no numbers. */
    data object Unknown : SessionExpiry

    /**
     * Time left, already split the way it is written ("expira em 1 h 47 min").
     *
     * [soon] is SPEC S7's last-10-minutes warning, computed by [Session.stateAt] rather than by
     * comparing numbers here — the policy has one home.
     */
    data class Remaining(val hours: Int, val minutes: Int, val soon: Boolean) : SessionExpiry

    /** Past the 2 h: the next request will be refused and the guard will act on it (SPEC S6). */
    data object Expired : SessionExpiry
}

/**
 * Everything the account screen shows, in one immutable value (ADR-003).
 *
 * @property tokenSuffix the last 4 characters of the credential, and **the only fragment of it this
 *   app ever renders** (SPEC S9, ADR-008). There is no reveal control, here or anywhere.
 * @property requestCount the ADR-006 budget counter, `null` outside a debug build.
 * @property signOutFailed the vault refused to clear. The user stays signed in and is told so,
 *   because the alternative is an app that says "you are out" over a token still on the device.
 * @property renewing a renewal is in flight. It disables the action rather than only spinning: a
 *   second tap is a second request out of the ~300 the account has (ADR-006).
 * @property renewFailed the last renewal did not happen. The session it would have replaced is
 *   untouched and still valid, which is why this is a line on the card and not a route away (S10).
 */
data class AccountUiState(
    val tokenSuffix: String = "",
    val expiry: SessionExpiry = SessionExpiry.Unknown,
    val requestCount: Int? = null,
    val signOutFailed: Boolean = false,
    val renewing: Boolean = false,
    val renewFailed: Boolean = false,
) {

    /**
     * Whether "Renovar" is on screen (SPEC S10).
     *
     * Only inside SPEC S7's last ten minutes: renewing earlier spends a request to buy time the
     * session already has, and an action that is always there is one the user has to decide about
     * every time they open the screen. It is derived rather than stored so it cannot disagree with
     * the countdown beside it.
     */
    val canRenew: Boolean get() = (expiry as? SessionExpiry.Remaining)?.soon == true
}

/**
 * The account screen: what the session is, how to extend it, and the one way out of it (S8, S9, S10).
 *
 * Opening it costs the account nothing — everything it shows comes from the vault, the local clock and
 * a counter (SPEC E5, ADR-006), which is why it can be opened as often as the user likes. Exactly one
 * thing on it reaches the partner, and only when the user asks for it: "Renovar".
 */
class AccountViewModel(
    private val sessionStartup: SessionStartup,
    private val logout: Logout,
    private val renewToken: RenewToken,
    private val requestCounter: RequestCounter,
    private val debugBuild: DebugBuild,
    private val clock: Clock,
) : ViewModel() {

    private val mutableState = MutableStateFlow(AccountUiState())
    val state: StateFlow<AccountUiState> = mutableState.asStateFlow()

    private val mutableSignedOut = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /**
     * Emitted once the credential is gone, whether "Sair" removed it or there was none to start with.
     *
     * A one-shot event and not a flag on the state: routing away must happen exactly once, and a
     * boolean that stays true would route again on every recomposition (`CLAUDE.md`).
     */
    val signedOut: SharedFlow<Unit> = mutableSignedOut.asSharedFlow()

    private val mutableRenewed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /**
     * Emitted once per renewal the partner actually answered (SPEC S10).
     *
     * The screen itself needs nothing from this — [onOpen] already rebuilt the card. It exists for
     * [io.github.npauloj.mibosmart.app.AppViewModel], which owns the expiry banner of SPEC S7 and has
     * no other way to learn that the deadline moved.
     *
     * `replay = 0` is load-bearing: a collector that arrives later must not be handed a renewal that
     * already happened and clear a banner that is telling the truth about the *current* session.
     */
    val renewed: SharedFlow<Unit> = mutableRenewed.asSharedFlow()

    init {
        viewModelScope.launch { onOpen() }
    }

    /**
     * Reads the session and describes it; the intent is a suspend function so a test can await it
     * rather than guess at a dispatcher (ADR-003).
     */
    suspend fun onOpen() {
        val session = sessionStartup()
        if (session == null) {
            // Nothing to show an account for. It should not happen — this screen is only reachable
            // with a session — but a guard that cleared the vault a frame ago makes it possible.
            mutableSignedOut.emit(Unit)
            return
        }
        mutableState.value = AccountUiState(
            tokenSuffix = session.token.value.takeLast(TokenMask.VISIBLE_SUFFIX),
            expiry = session.expiryAt(clock),
            // Read, not watched: the only thing on this screen that spends a request is "Renovar",
            // and it comes back through here, so the number is fresh without a collector (ADR-006).
            requestCount = requestCounter.requests.value.takeIf { debugBuild.isOn },
        )
    }

    /** "Renovar", launched on the ViewModel's scope so a rotation cannot lose the answer mid-flight. */
    fun renew() {
        viewModelScope.launch { onRenew() }
    }

    /**
     * The same intent as a suspend function, so a test can await it (ADR-003).
     *
     * A renewal already in flight is not started again: the second call would spend a second request
     * of the account's budget to obtain a third credential nobody asked for (ADR-006).
     */
    suspend fun onRenew() {
        if (mutableState.value.renewing) return
        mutableState.update { it.copy(renewing = true, renewFailed = false) }

        when (renewToken()) {
            // The card is rebuilt from the vault rather than patched: the suffix, the deadline and the
            // request count have all changed, and `onOpen` is already the one description of how the
            // screen reads a session. The user does not move — this screen *is* where they were.
            RenewalResult.Success -> {
                onOpen()
                mutableRenewed.emit(Unit)
            }
            RenewalResult.Failed -> mutableState.update { it.copy(renewing = false, renewFailed = true) }
            // The vault was emptied while the tap was in flight; there is no session left to show.
            RenewalResult.NoSession -> mutableSignedOut.emit(Unit)
        }
    }

    /** "Sair", launched on the ViewModel's scope so a rotation cannot leave the vault half-cleared. */
    fun signOut() {
        viewModelScope.launch { onSignOut() }
    }

    /** The same intent as a suspend function, so a test can await it (ADR-003). */
    suspend fun onSignOut() {
        try {
            logout()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            // An invalidated Keystore key, a Keychain error. The token may still be on the device, so
            // the app must not claim otherwise (ADR-008).
            mutableState.update { it.copy(signOutFailed = true) }
            return
        }
        mutableSignedOut.emit(Unit)
    }
}

/** The session as the screen reads it, on the clock the app was given (SPEC S7). */
private fun Session.expiryAt(clock: Clock): SessionExpiry {
    val now = clock.now()
    val left = remainingLife(now)
    if (left <= Duration.ZERO) return SessionExpiry.Expired

    return SessionExpiry.Remaining(
        hours = left.inWholeHours.toInt(),
        minutes = (left.inWholeMinutes % MINUTES_PER_HOUR).toInt(),
        soon = stateAt(now) == SessionState.ExpiringSoon,
    )
}

private const val MINUTES_PER_HOUR = 60
