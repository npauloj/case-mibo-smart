package io.github.npauloj.mibosmart.app.session

import io.github.npauloj.mibosmart.domain.session.SessionRepository
import io.github.npauloj.mibosmart.domain.session.SessionStore
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock

/**
 * What "Renovar" means: one partner call and, only if it was answered, a new stored session (S10).
 *
 * The order is the same as [AuthenticateToken]'s and for the same reason — the vault is written after
 * the partner has spoken, so a renewal that fails leaves the session exactly as it was. Here that is
 * not merely tidy: renewal **adds** a credential rather than replacing one (measured 2026-09-21,
 * `docs/api-contract.md` §2), so the token the user already had is still good and throwing it away on
 * a timeout would be the app losing a session the partner never took.
 *
 * The deadline comes from the response. [Clock] only says *when* the new session started; how long it
 * lasts is `tempoExpiracao`, which is the difference between S10 and the local two-hour count a pasted
 * token still falls back to (SPEC S7).
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
            // Every failure lands here on purpose, including a 401/403. The categories
            // `AuthenticationResult` splits are there because the token screen offers a different way
            // out of each; this screen offers the same one — keep the session you have and try again.
            // A refusal of *this* credential is already on its way to the session guard, which is the
            // only thing entitled to end a session (SPEC S6, ADR-018).
            RenewalResult.Failed
        }
    }
}

/**
 * Everything renewing a session can end in (ADR-002: the screen's `when` is exhaustive, and a branch
 * added later stops compiling until someone says what it looks like).
 */
sealed interface RenewalResult {

    /** The vault now holds the new credential and the deadline the partner stated (SPEC S10). */
    data object Success : RenewalResult

    /** Nothing changed: the previous credential is still the session, and still valid (SPEC S10). */
    data object Failed : RenewalResult

    /**
     * There was no session to renew.
     *
     * It should not happen — the action only exists on a screen that is showing one — but the guard
     * can empty the vault a frame before the tap lands, and the honest answer then is the token
     * screen rather than an error over a session that no longer exists (SPEC S6).
     */
    data object NoSession : RenewalResult
}
