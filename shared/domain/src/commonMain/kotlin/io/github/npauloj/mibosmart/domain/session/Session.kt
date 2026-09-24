package io.github.npauloj.mibosmart.domain.session

import kotlin.jvm.JvmInline
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * A partner access token: a bearer credential that lives at most 2 h and opens a physical lock.
 */
@JvmInline
value class Token(val value: String) {
    override fun toString(): String = "Token(***)"
}

/**
 * A stored session: the credential, when it started counting down, and how long it was given
 * (S7).
 */
data class Session(
    val token: Token,
    val issuedAt: Instant,
    val lifetime: Duration = LIFETIME,
) {

    /** How the session reads on a given clock — the one input the expiry banner has (SPEC S7). */
    fun stateAt(now: Instant): SessionState =
        if (remainingUntilWarning(now) <= Duration.ZERO) SessionState.ExpiringSoon else SessionState.Valid

    /**
     * How long until [stateAt] turns to [SessionState.ExpiringSoon]; zero or negative once it
     * has.
     */
    fun remainingUntilWarning(now: Instant): Duration = (lifetime - WARN_MARGIN) - (now - issuedAt)

    /** How much of the window is left; zero or negative once the partner will refuse the token. */
    fun remainingLife(now: Instant): Duration = lifetime - (now - issuedAt)

    companion object {

        /** How long a **pasted** token lasts (SPEC S7). */
        val LIFETIME: Duration = 2.hours

        /** How early the app warns, measured back from whatever deadline the session has (SPEC S7). */
        val WARN_MARGIN: Duration = 10.minutes

        /** When a session of the default [LIFETIME] starts warning — 1 h 50 min in. */
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

/** Where the validated session lives for as long as the user is signed in. */
interface SessionStore {

    /** The current session, or `null` when there is none. */
    suspend fun read(): Session?

    /**
     * Stores [token] as the session's credential, replacing any previous one.
     * @param lifetime how long the partner gave this credential.
     */
    suspend fun write(token: Token, issuedAt: Instant, lifetime: Duration = Session.LIFETIME)

    /** Removes the session, so the next [read] answers `null` (SPEC S6, S8). */
    suspend fun clear()
}

/**
 * The partner-side half of the session: the domain states the intent, `:shared:data` knows the
 * wire (ADR-004).
 */
interface SessionRepository {

    /** Validates [token] with exactly one partner call (SPEC S2); returns normally when accepted. */
    suspend fun validateToken(token: Token)

    /** Exchanges [token] for a fresh credential with exactly one partner call (SPEC S10). */
    suspend fun renewToken(token: Token): RenewedSession
}

/**
 * What the partner answers a renewal with: the new credential and the life it was given (SPEC
 * S10).
 */
data class RenewedSession(val token: Token, val lifetime: Duration)
