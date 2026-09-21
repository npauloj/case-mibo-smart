package io.github.npauloj.mibosmart.app.session

import io.github.npauloj.mibosmart.domain.session.SessionStore

/**
 * What "Sair" means: the credential leaves the device (SPEC S8).
 *
 * One line, and deliberately a use case rather than a call from the ViewModel to the store — the
 * screen must not be the place that knows logout is "a vault write of nothing". It is also the caller
 * ADR-010 was waiting for before `SessionStore.clear()` could exist at all.
 *
 * A vault that fails to clear is **not** swallowed: telling the user they are signed out while the
 * token is still on disk is the one outcome ADR-008 cannot accept, so the failure travels up to the
 * screen that can say so.
 */
class Logout(private val sessionStore: SessionStore) {

    suspend operator fun invoke() {
        sessionStore.clear()
    }
}
