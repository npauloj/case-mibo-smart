package io.github.npauloj.mibosmart.app.session

import io.github.npauloj.mibosmart.domain.session.SessionStore

/** What "Sair" means: the credential leaves the device (SPEC S8). */
class Logout(private val sessionStore: SessionStore) {

    suspend operator fun invoke() {
        sessionStore.clear()
    }
}
