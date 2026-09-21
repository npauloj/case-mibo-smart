package io.github.npauloj.mibosmart.domain.session

import kotlin.jvm.JvmInline

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
 * Where the validated token lives for the lifetime of the session.
 *
 * The functions suspend on purpose: S-01b puts the Keystore / Keychain vault behind this same
 * interface (ADR-008) and its reads and writes are blocking platform I/O.
 */
interface SessionStore {

    /** The token of the current session, or `null` when there is none. */
    suspend fun read(): Token?

    /** Stores [token] as the session's credential, replacing any previous one. */
    suspend fun write(token: Token)
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
