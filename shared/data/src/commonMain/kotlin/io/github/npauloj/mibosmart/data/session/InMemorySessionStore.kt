package io.github.npauloj.mibosmart.data.session

import io.github.npauloj.mibosmart.domain.session.SessionStore
import io.github.npauloj.mibosmart.domain.session.Token

/**
 * Keeps the validated token for the lifetime of the process (SPEC S2, first half).
 *
 * Volatile on purpose: this slice writes nothing to disk, so a killed process asks for the token
 * again. S-01b puts the Keystore / Keychain vault behind the same interface (ADR-008) and this
 * implementation stays as the one tests use.
 */
class InMemorySessionStore : SessionStore {

    private var token: Token? = null

    override suspend fun read(): Token? = token

    override suspend fun write(token: Token) {
        this.token = token
    }
}
