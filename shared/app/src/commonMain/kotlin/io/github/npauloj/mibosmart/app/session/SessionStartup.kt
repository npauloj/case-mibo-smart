package io.github.npauloj.mibosmart.app.session

import io.github.npauloj.mibosmart.domain.session.Session
import io.github.npauloj.mibosmart.domain.session.SessionStore

/** What the app knows about its session before it draws anything (SPEC S5). */
class SessionStartup(private val sessionStore: SessionStore) {

    /** The stored session, or `null` when this is a cold start without one. */
    suspend operator fun invoke(): Session? = sessionStore.read()
}
