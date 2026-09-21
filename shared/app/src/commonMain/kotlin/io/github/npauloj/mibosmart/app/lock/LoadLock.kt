package io.github.npauloj.mibosmart.app.lock

import io.github.npauloj.mibosmart.domain.error.SmartHomeException
import io.github.npauloj.mibosmart.domain.lock.LockAddress
import io.github.npauloj.mibosmart.domain.lock.LockRepository
import io.github.npauloj.mibosmart.domain.lock.LockState
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * What opening the lock screen means: the three reads of SPEC L1, issued together, once.
 *
 * Together because they are independent and the user is waiting for all three — in sequence the
 * screen would take three round trips to say anything. Once because the account pays for every
 * request (ADR-006): there is no timer, no poll and no retry here, and the screen is the only thing
 * that may ask again (SPEC E5).
 *
 * A failure in any of the three cancels the other two — `coroutineScope` does that — which is the
 * right call for a screen that has nothing to show without all three values.
 */
class LoadLock(private val lockRepository: LockRepository) {

    suspend operator fun invoke(address: LockAddress): LoadLockResult =
        try {
            coroutineScope {
                val isOpen = async { lockRepository.readOpenState(address) }
                val isRemoteOpenEnabled = async { lockRepository.readRemoteOpenEnabled(address) }
                val volume = async { lockRepository.readVolume(address) }
                LoadLockResult.Loaded(
                    LockState(
                        isOpen = isOpen.await(),
                        isRemoteOpenEnabled = isRemoteOpenEnabled.await(),
                        volume = volume.await(),
                    ),
                )
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Throwable) {
            failure.toResult()
        }

    private fun Throwable.toResult(): LoadLockResult = when (this) {
        is SmartHomeException.TokenRejected -> LoadLockResult.TokenRejected
        is SmartHomeException.TokenExpired -> LoadLockResult.TokenExpired(serverMessage)
        is SmartHomeException.Offline -> LoadLockResult.Offline
        is SmartHomeException.UnexpectedResponse -> LoadLockResult.UnexpectedResponse
        else -> LoadLockResult.Failed
    }
}

/**
 * Everything reading a lock can end in (ADR-002: each use case answers with its own closed set, so
 * the screen's `when` is exhaustive and the compiler catches a missing branch).
 */
sealed interface LoadLockResult {

    /** The three reads agreed to answer: this is the lock right now (SPEC L1). */
    data class Loaded(val lock: LockState) : LoadLockResult

    /** The partner does not recognise the credential — HTTP 401 (SPEC S3, S6). */
    data object TokenRejected : LoadLockResult

    /** The credential expired — HTTP 403; [serverMessage] is the partner's own sentence (SPEC S3.1). */
    data class TokenExpired(val serverMessage: String?) : LoadLockResult

    /** The call never reached the partner (SPEC S4). */
    data object Offline : LoadLockResult

    /** The answer was not the documented shape (SPEC E3). */
    data object UnexpectedResponse : LoadLockResult

    /** The partner named an error of its own; its message stays in the exception (SPEC U6). */
    data object Failed : LoadLockResult
}
