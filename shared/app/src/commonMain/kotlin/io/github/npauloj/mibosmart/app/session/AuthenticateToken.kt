package io.github.npauloj.mibosmart.app.session

import io.github.npauloj.mibosmart.domain.error.SmartHomeException
import io.github.npauloj.mibosmart.domain.session.SessionRepository
import io.github.npauloj.mibosmart.domain.session.SessionStore
import io.github.npauloj.mibosmart.domain.session.Token
import kotlin.coroutines.cancellation.CancellationException

/**
 * What "Validar" means on the token screen: one partner call and, only if it was accepted, a stored
 * session (SPEC S2 first half, S3, S4).
 *
 * The order matters — the token is written after the partner accepted it, so a rejected token leaves
 * nothing behind.
 */
class AuthenticateToken(
    private val sessionRepository: SessionRepository,
    private val sessionStore: SessionStore,
) {

    suspend operator fun invoke(token: Token): AuthenticationResult =
        try {
            sessionRepository.validateToken(token)
            sessionStore.write(token)
            AuthenticationResult.Success
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Throwable) {
            failure.toResult()
        }

    private fun Throwable.toResult(): AuthenticationResult = when (this) {
        is SmartHomeException.TokenRejected -> AuthenticationResult.TokenRejected
        is SmartHomeException.TokenExpired -> AuthenticationResult.TokenExpired(serverMessage)
        is SmartHomeException.Offline -> AuthenticationResult.Offline
        is SmartHomeException.UnexpectedResponse -> AuthenticationResult.UnexpectedResponse
        else -> AuthenticationResult.Failed
    }
}

/**
 * Everything validating a token can end in (ADR-002: each use case answers with its own closed set,
 * so the screen's `when` is exhaustive and the compiler catches a missing branch).
 */
sealed interface AuthenticationResult {

    /** The partner accepted the token and it is now the session's credential. */
    data object Success : AuthenticationResult

    /** The partner does not recognise the credential — HTTP 401 (SPEC S3). */
    data object TokenRejected : AuthenticationResult

    /**
     * The credential was recognised and has expired — HTTP 403 (SPEC S3.1).
     *
     * [serverMessage] is the partner's own sentence and is shown as-is when present: unlike a generic
     * API error it tells the user precisely what to do (SPEC U6, ADR-012).
     */
    data class TokenExpired(val serverMessage: String?) : AuthenticationResult

    /** The call never reached the partner (SPEC S4). */
    data object Offline : AuthenticationResult

    /** The answer was not the documented envelope (SPEC E3). */
    data object UnexpectedResponse : AuthenticationResult

    /** The partner named an error of its own; its message stays in the exception, not on screen (SPEC U6). */
    data object Failed : AuthenticationResult
}
