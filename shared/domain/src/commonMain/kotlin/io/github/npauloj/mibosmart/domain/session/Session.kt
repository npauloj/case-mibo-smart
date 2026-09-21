package io.github.npauloj.mibosmart.domain.session

import kotlin.jvm.JvmInline
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * A partner access token: a bearer credential that lives at most 2 h and opens a physical lock.
 *
 * `toString` is redacted on purpose — the token must never reach a log, a crash message or a screen
 * (ADR-008, SPEC S9). Only `:shared:data` unwraps [value], to put it in the `Authorization` header,
 * where the Ktor log sanitizer redacts it again.
 */
@JvmInline
value class Token(val value: String) {
    override fun toString(): String = "Token(***)"
}

/**
 * A stored session: the credential and when it started counting down (SPEC S7).
 *
 * [issuedAt] is the moment the partner first accepted the token, not the moment it was written to the
 * vault — the two are the same call, and counting from acceptance is what `[ASSUMED]` in SPEC S7
 * states. It is stored beside the token rather than derived, because a cold start has no other way to
 * know how much of the 2 h window is left: nothing on the wire says so, and asking would be an API
 * call on a timer (SPEC E5, ADR-006).
 */
data class Session(val token: Token, val issuedAt: Instant) {

    /**
     * How the session reads on a given clock — the one input the expiry banner has (SPEC S7).
     *
     * [now] is passed in rather than read here: the policy is a pure function of two instants, so a
     * test can put the boundary wherever it likes and the caller owns which clock it trusts.
     */
    fun stateAt(now: Instant): SessionState =
        if (remainingUntilWarning(now) <= Duration.ZERO) SessionState.ExpiringSoon else SessionState.Valid

    /**
     * How long until [stateAt] turns to [SessionState.ExpiringSoon]; zero or negative once it has.
     *
     * This is what lets the app warn *while it is open* without polling anything: the caller waits
     * this one duration out and wakes up once, instead of asking the clock on a tick (ADR-006).
     */
    fun remainingUntilWarning(now: Instant): Duration = WARN_AFTER - (now - issuedAt)

    companion object {

        /**
         * The partner's tokens last 2 h, and the app warns 10 min before that (SPEC S7).
         *
         * Ten minutes is the margin S-03's "Renovar" needs to be a choice rather than a race; until
         * renewal exists the warning is still the difference between a session that ends in the
         * user's hands and one that ends mid-tap.
         */
        val WARN_AFTER: Duration = 1.hours + 50.minutes
    }
}

/** How much life a session has left, in the only granularity the UI acts on (SPEC S7). */
enum class SessionState {

    /** Far enough from the 2 h limit that the app says nothing. */
    Valid,

    /** Past [Session.WARN_AFTER] — the device list shows the non-blocking banner. */
    ExpiringSoon,
}

/**
 * Where the validated session lives for as long as the user is signed in.
 *
 * The functions suspend because the implementation behind them is the platform vault (ADR-008) and
 * its reads and writes are blocking platform I/O.
 *
 * Deliberately still without `clear()`: logout is the slice that has a caller for it (S-02b, ADR-010).
 */
interface SessionStore {

    /** The current session, or `null` when there is none. */
    suspend fun read(): Session?

    /** Stores [token] as the session's credential, replacing any previous one. */
    suspend fun write(token: Token, issuedAt: Instant)
}

/**
 * The partner-side half of the session: the domain states the intent, `:shared:data` knows the wire
 * (ADR-004).
 *
 * Failures are typed exceptions from `domain.error` rather than return values (ADR-002); the use case
 * that calls this is the one that turns them into a result the UI can render.
 */
interface SessionRepository {

    /** Validates [token] with exactly one partner call (SPEC S2); returns normally when accepted. */
    suspend fun validateToken(token: Token)
}
