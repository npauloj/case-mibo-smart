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
 */
data class AccountUiState(
    val tokenSuffix: String = "",
    val expiry: SessionExpiry = SessionExpiry.Unknown,
    val requestCount: Int? = null,
    val signOutFailed: Boolean = false,
)

/**
 * The account screen: what the session is, and the one way out of it (SPEC S8, S9, ADR-006).
 *
 * It costs the account nothing — every value on it comes from the vault, the local clock and a
 * counter (SPEC E5, ADR-006). That is why it can be opened as often as the user likes.
 */
class AccountViewModel(
    private val sessionStartup: SessionStartup,
    private val logout: Logout,
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
            // Read once, when the screen opens: nothing on it spends a request, so there is nothing
            // to watch for (ADR-006).
            requestCount = requestCounter.requests.value.takeIf { debugBuild.isOn },
        )
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
