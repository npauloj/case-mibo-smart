package io.github.npauloj.mibosmart.app.session

import io.github.npauloj.mibosmart.domain.session.RenewedSession
import io.github.npauloj.mibosmart.domain.session.SessionRepository
import io.github.npauloj.mibosmart.domain.session.Token

/**
 * A partner that answers whatever the test wants, and counts how often it was asked — the account
 * pays per request (ADR-006), so "how many calls" is part of the behaviour under test.
 *
 * [renewal] is what `renovar-token` answers: a function of the token it was handed, so a test can
 * both assert which credential was renewed and choose the lifetime that comes back (SPEC S10). It
 * throws to stage a failure, exactly as the real repository does (ADR-002).
 *
 * [answer] is deliberately the **last** parameter: every existing test passes it as a trailing
 * lambda, and a parameter added after it would silently take their place.
 */
internal class FakeSessionRepository(
    private val renewal: suspend (Token) -> RenewedSession = { unstaged() },
    private val answer: suspend () -> Unit = {},
) : SessionRepository {

    var calls: Int = 0
        private set

    var lastToken: Token? = null
        private set

    override suspend fun validateToken(token: Token) {
        calls++
        lastToken = token
        answer()
    }

    override suspend fun renewToken(token: Token): RenewedSession {
        calls++
        lastToken = token
        return renewal(token)
    }

    private companion object {

        /** A test that staged no renewal did not mean to trigger one. */
        fun unstaged(): Nothing = error("renewToken was called but this fake has no renewal staged")
    }
}
