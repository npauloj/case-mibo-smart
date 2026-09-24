package io.github.npauloj.mibosmart.app.session

import io.github.npauloj.mibosmart.domain.session.SessionRepository
import io.github.npauloj.mibosmart.domain.session.SessionStore
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock

/**
 * What "Renovar" means: one partner call and, only if it was answered, a new stored session
 * (S10).
 */
class RenewToken(
    private val sessionRepository: SessionRepository,
    private val sessionStore: SessionStore,
    private val clock: Clock,
) {

    suspend operator fun invoke(): RenewalResult {
        val current = sessionStore.read() ?: return RenewalResult.NoSession
        return try {
            val renewed = sessionRepository.renewToken(current.token)
            sessionStore.write(renewed.token, clock.now(), renewed.lifetime)
            RenewalResult.Success
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            RenewalResult.Failed
        }
    }
}

/**
 * Everything renewing a session can end in (ADR-002: the screen's `when` is exhaustive, and a
 * branch added later stops compiling until someone says what it looks like).
 */
sealed interface RenewalResult {

    /** The vault now holds the new credential and the deadline the partner stated (SPEC S10). */
    data object Success : RenewalResult

    /** Nothing changed: the previous credential is still the session, and still valid (SPEC S10). */
    data object Failed : RenewalResult

    /** There was no session to renew. */
    data object NoSession : RenewalResult
}
