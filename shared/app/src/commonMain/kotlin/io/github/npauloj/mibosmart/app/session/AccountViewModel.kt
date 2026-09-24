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

/** Whether this is a developer's build: `local.properties` → `BuildConfig.DEBUG` → here. */
@JvmInline
value class DebugBuild(val isOn: Boolean)

/**
 * How much life the session has left, in the granularity the account screen renders (SPEC S7,
 * S6).
 */
sealed interface SessionExpiry {

    /** The vault has not answered yet; the screen shows the frame and no numbers. */
    data object Unknown : SessionExpiry

    /** Time left, already split the way it is written ("expira em 1 h 47 min"). */
    data class Remaining(val hours: Int, val minutes: Int, val soon: Boolean) : SessionExpiry

    /** Past the 2 h: the next request will be refused and the guard will act on it (SPEC S6). */
    data object Expired : SessionExpiry
}

/**
 * Everything the account screen shows, in one immutable value (ADR-003).
 * @property tokenSuffix the last 4 characters of the credential, and **the only fragment of it
 * this app ever renders** (SPEC S9, ADR-008).
 * @property requestCount the ADR-006 budget counter, `null` outside a debug build.
 * @property signOutFailed the vault refused to clear.
 * @property renewing a renewal is in flight.
 * @property renewFailed the last renewal did not happen.
 */
data class AccountUiState(
    val tokenSuffix: String = "",
    val expiry: SessionExpiry = SessionExpiry.Unknown,
    val requestCount: Int? = null,
    val signOutFailed: Boolean = false,
    val renewing: Boolean = false,
    val renewFailed: Boolean = false,
) {

    /** Whether "Renovar" is on screen (SPEC S10). */
    val canRenew: Boolean get() = (expiry as? SessionExpiry.Remaining)?.soon == true
}

/**
 * The account screen: what the session is, how to extend it, and the one way out of it (S8, S9,
 * S10).
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
     * Emitted once the credential is gone, whether "Sair" removed it or there was none to start
     * with.
     */
    val signedOut: SharedFlow<Unit> = mutableSignedOut.asSharedFlow()

    private val mutableRenewed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Emitted once per renewal the partner actually answered (SPEC S10). */
    val renewed: SharedFlow<Unit> = mutableRenewed.asSharedFlow()

    init {
        viewModelScope.launch { onOpen() }
    }

    /**
     * Reads the session and describes it; the intent is a suspend function so a test can await
     * it rather than guess at a dispatcher (ADR-003).
     */
    suspend fun onOpen() {
        val session = sessionStartup()
        if (session == null) {
            mutableSignedOut.emit(Unit)
            return
        }
        mutableState.value = AccountUiState(
            tokenSuffix = session.token.value.takeLast(TokenMask.VISIBLE_SUFFIX),
            expiry = session.expiryAt(clock),
            requestCount = requestCounter.requests.value.takeIf { debugBuild.isOn },
        )
    }

    /** "Renovar", launched on the ViewModel's scope so a rotation cannot lose the answer mid-flight. */
    fun renew() {
        viewModelScope.launch { onRenew() }
    }

    /** The same intent as a suspend function, so a test can await it (ADR-003). */
    suspend fun onRenew() {
        if (mutableState.value.renewing) return
        mutableState.update { it.copy(renewing = true, renewFailed = false) }

        when (renewToken()) {
            RenewalResult.Success -> {
                onOpen()
                mutableRenewed.emit(Unit)
            }
            RenewalResult.Failed -> mutableState.update { it.copy(renewing = false, renewFailed = true) }
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
