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
 * A stored session: the credential, when it started counting down, and how long it was given (S7).
 *
 * [issuedAt] is the moment the partner first accepted the token, not the moment it was written to the
 * vault — the two are the same call, and counting from acceptance is what `[ASSUMED]` in SPEC S7
 * states. It is stored beside the token rather than derived, because a cold start has no other way to
 * know how much of the window is left: nothing on the wire says so, and asking would be an API
 * call on a timer (SPEC E5, ADR-006).
 *
 * [lifetime] is the other half, and the two sessions the app can hold differ exactly there: a pasted
 * token has no lifetime on the wire and falls back to [LIFETIME], while a **renewed** one is given
 * `tempoExpiracao` by the partner and counts that instead (SPEC S10, measured 2026-09-21). Carrying it
 * per session rather than reading a constant is what stops the app from inventing a deadline the
 * server never promised.
 */
data class Session(
    val token: Token,
    val issuedAt: Instant,
    val lifetime: Duration = LIFETIME,
) {

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
    fun remainingUntilWarning(now: Instant): Duration = (lifetime - WARN_MARGIN) - (now - issuedAt)

    /**
     * How much of the window is left; zero or negative once the partner will refuse the token.
     *
     * The account screen is the one surface that has to say a number rather than raise a banner
     * (SPEC S6's "Sessão expirada" and the "expira em …" beside it), and it is the same arithmetic as
     * [remainingUntilWarning] against the other deadline — so it is stated once, here, instead of
     * being re-derived from the warning plus the 10 min it is early by.
     */
    fun remainingLife(now: Instant): Duration = lifetime - (now - issuedAt)

    companion object {

        /**
         * How long a **pasted** token lasts (SPEC S7).
         *
         * Counted from the acceptance this app witnessed, because that path has no field on the wire
         * to read: only `renovar-token` answers `tempoExpiracao`, and a session that came from it
         * carries that value in [lifetime] instead of falling back here (SPEC S10).
         */
        val LIFETIME: Duration = 2.hours

        /**
         * How early the app warns, measured back from whatever deadline the session has (SPEC S7).
         *
         * Ten minutes is the margin "Renovar" needs to be a choice rather than a race, which is why
         * it is a distance from the end and not an age: a renewed session with a shorter
         * `tempoExpiracao` still gets its ten minutes of warning.
         */
        val WARN_MARGIN: Duration = 10.minutes

        /**
         * When a session of the default [LIFETIME] starts warning — 1 h 50 min in.
         *
         * Kept as a named constant because it is the boundary SPEC S7 states in words, and the one a
         * test can assert to the millisecond without re-deriving it.
         */
        val WARN_AFTER: Duration = LIFETIME - WARN_MARGIN
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
 */
interface SessionStore {

    /** The current session, or `null` when there is none. */
    suspend fun read(): Session?

    /**
     * Stores [token] as the session's credential, replacing any previous one.
     *
     * @param lifetime how long the partner gave this credential. The default is the 2 h a pasted
     *   token is assumed to last (SPEC S7); a renewal passes the `tempoExpiracao` it was answered
     *   with instead, so the deadline on screen is the server's and not this app's arithmetic (S10).
     */
    suspend fun write(token: Token, issuedAt: Instant, lifetime: Duration = Session.LIFETIME)

    /**
     * Removes the session, so the next [read] answers `null` (SPEC S6, S8).
     *
     * The last member ADR-008 asked for, arriving with the two callers it was waiting on (ADR-010):
     * "Sair" and the guard that clears a session the partner has refused.
     */
    suspend fun clear()
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

    /**
     * Exchanges [token] for a fresh credential with exactly one partner call (SPEC S10).
     *
     * It **adds** a credential rather than replacing one: measured 46 s after a renewal, the old
     * token, the new one and an unrelated third all still answered `200` (`docs/api-contract.md` §2).
     * So a caller that fails here has lost nothing — [token] is still the session.
     */
    suspend fun renewToken(token: Token): RenewedSession
}

/**
 * What the partner answers a renewal with: the new credential and the life it was given (SPEC S10).
 *
 * [lifetime] is `tempoExpiracao` turned into a [Duration] by `:shared:data` — the domain never learns
 * that the wire counts seconds (ADR-004). It is carried rather than assumed because it is the whole
 * point of the endpoint: the server states the deadline, so the app stops estimating one.
 */
data class RenewedSession(val token: Token, val lifetime: Duration)
