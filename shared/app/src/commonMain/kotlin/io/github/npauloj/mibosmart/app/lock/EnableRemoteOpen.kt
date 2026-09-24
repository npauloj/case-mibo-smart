package io.github.npauloj.mibosmart.app.lock

import io.github.npauloj.mibosmart.domain.error.SmartHomeException
import io.github.npauloj.mibosmart.domain.lock.LockAddress
import io.github.npauloj.mibosmart.domain.lock.LockRepository
import kotlin.coroutines.cancellation.CancellationException

/** Granting the app the right to command this lock (SPEC L2). */
class EnableRemoteOpen(
    private val lockRepository: LockRepository,
    private val lockWrites: LockWritesSwitch,
) {

    suspend operator fun invoke(address: LockAddress): EnableRemoteOpenResult {
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
     * Both calls answered: [isRemoteOpenEnabled] is what `status-abrir-remoto` said
     * **afterwards**.
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
