package io.github.npauloj.mibosmart.data.session

import io.github.npauloj.mibosmart.domain.session.Session
import io.github.npauloj.mibosmart.domain.session.SessionStore
import io.github.npauloj.mibosmart.domain.session.Token
import kotlin.time.Duration
import kotlin.time.Instant

/** Keeps the validated session for the lifetime of the process (SPEC S2, first half). */
class InMemorySessionStore : SessionStore {

    private var session: Session? = null

    override suspend fun read(): Session? = session

    override suspend fun write(token: Token, issuedAt: Instant, lifetime: Duration) {
        session = Session(token, issuedAt, lifetime)
    }

    override suspend fun clear() {
        session = null
    }
}
