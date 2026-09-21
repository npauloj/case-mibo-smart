package io.github.npauloj.mibosmart.app.lock

import io.github.npauloj.mibosmart.domain.error.SmartHomeException
import io.github.npauloj.mibosmart.domain.lock.LockAddress
import io.github.npauloj.mibosmart.domain.lock.LockRepository
import kotlin.coroutines.cancellation.CancellationException

/**
 * Granting the app the right to command this lock (SPEC L2).
 *
 * This is the only code in the app that can turn remote opening on, and there is nothing anywhere
 * that can turn it off — [LockRepository.enableRemoteOpen] takes no argument. It runs only when the
 * user chooses the labelled action; no read, no retry and no other intent reaches it.
 *
 * Two requests when it succeeds, because the app does not get to assume it worked: after
 * `habilitar-abrir-remoto` it **re-reads** `status-abrir-remoto` and reports what the lock says, not
 * what it was asked. If the re-read still says `false`, the screen keeps the open/close control
 * disabled — the UI follows the API, never leads it.
 */
class EnableRemoteOpen(
    private val lockRepository: LockRepository,
    private val lockWrites: LockWritesSwitch,
) {

    suspend operator fun invoke(address: LockAddress): EnableRemoteOpenResult {
        // First, before anything can reach the network: this call changes a door's security posture,
        // so "off" has to mean no request, not a request that is later ignored.
        if (!lockWrites.isOn) return EnableRemoteOpenResult.WritesDisabled
        return try {
            lockRepository.enableRemoteOpen(address)
            EnableRemoteOpenResult.Refreshed(lockRepository.readRemoteOpenEnabled(address))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Throwable) {
            failure.toResult()
        }
    }

    private fun Throwable.toResult(): EnableRemoteOpenResult = when (this) {
        is SmartHomeException.TokenRejected -> EnableRemoteOpenResult.TokenRejected
        is SmartHomeException.TokenExpired -> EnableRemoteOpenResult.TokenExpired(serverMessage)
        is SmartHomeException.Offline -> EnableRemoteOpenResult.Offline
        is SmartHomeException.UnexpectedResponse -> EnableRemoteOpenResult.UnexpectedResponse
        else -> EnableRemoteOpenResult.Failed
    }
}

/** Everything enabling remote opening can end in (ADR-002), with [LoadLockResult]'s categories. */
sealed interface EnableRemoteOpenResult {

    /**
     * Both calls answered: [isRemoteOpenEnabled] is what `status-abrir-remoto` said **afterwards**.
     *
     * It is not always `true`, and that is the point of re-reading: a lock that ignored the grant
     * must not end up on a screen that claims it accepted one.
     */
    data class Refreshed(val isRemoteOpenEnabled: Boolean) : EnableRemoteOpenResult

    /** This build was assembled with `smarthome.lockWritesEnabled=false`: nothing was sent. */
    data object WritesDisabled : EnableRemoteOpenResult

    /** The partner does not recognise the credential — HTTP 401 (SPEC S3, S6). */
    data object TokenRejected : EnableRemoteOpenResult

    /** The credential expired — HTTP 403; [serverMessage] is the partner's own sentence (SPEC S3.1). */
    data class TokenExpired(val serverMessage: String?) : EnableRemoteOpenResult

    /** The call never reached the partner (SPEC S4). */
    data object Offline : EnableRemoteOpenResult

    /** The answer was not the documented shape (SPEC E3). */
    data object UnexpectedResponse : EnableRemoteOpenResult

    /** The partner named an error of its own; its message stays in the exception (SPEC U6). */
    data object Failed : EnableRemoteOpenResult
}
