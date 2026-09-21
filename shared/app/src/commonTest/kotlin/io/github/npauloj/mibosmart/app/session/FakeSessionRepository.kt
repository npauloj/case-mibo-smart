package io.github.npauloj.mibosmart.app.session

import io.github.npauloj.mibosmart.domain.session.SessionRepository
import io.github.npauloj.mibosmart.domain.session.Token

/**
 * A partner that answers whatever the test wants, and counts how often it was asked — the account
 * pays per request (ADR-006), so "how many calls" is part of the behaviour under test.
 */
internal class FakeSessionRepository(private val answer: suspend () -> Unit = {}) : SessionRepository {

    var calls: Int = 0
        private set

    var lastToken: Token? = null
        private set

    override suspend fun validateToken(token: Token) {
        calls++
        lastToken = token
        answer()
    }
}
