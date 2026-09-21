package io.github.npauloj.mibosmart.app.session

import io.github.npauloj.mibosmart.domain.session.Session
import io.github.npauloj.mibosmart.domain.session.SessionStore

/**
 * What the app knows about its session before it draws anything (SPEC S5).
 *
 * One vault read and no partner call: a token that is still in the vault is treated as good until a
 * real request says otherwise, because validating it on startup would spend a request of the
 * account's budget to learn what the next screen's own request is about to tell us anyway (SPEC E5,
 * ADR-006). A token that has expired meanwhile comes back as a 401/403 on that request, and the
 * session guard is what turns it into a trip back to the token screen (SPEC S6).
 *
 * The account screen reads the session through this same use case rather than reaching for the store:
 * "the session, without asking the partner" is one question, and it should have one answer.
 */
class SessionStartup(private val sessionStore: SessionStore) {

    /** The stored session, or `null` when this is a cold start without one. */
    suspend operator fun invoke(): Session? = sessionStore.read()
}
