package io.github.npauloj.mibosmart.app.lock

import io.github.npauloj.mibosmart.domain.error.SmartHomeException
import io.github.npauloj.mibosmart.domain.lock.LockAddress
import io.github.npauloj.mibosmart.domain.lock.LockRepository
import io.github.npauloj.mibosmart.domain.lock.VolumeLevel
import kotlin.coroutines.cancellation.CancellationException

/**
 * Setting how loudly the lock announces itself (SPEC L7).
 *
 * One request, never optimistic: the level it returns is the level the partner accepted, so a screen
 * that renders [ChangeVolumeResult.Changed] and nothing else can never show a volume the hardware is
 * not at. There is no retry here either — the account pays per request (ADR-006) and asking again is
 * the user's decision.
 */
class ChangeVolume(
    private val lockRepository: LockRepository,
    private val lockWrites: LockWritesSwitch,
) {

    suspend operator fun invoke(address: LockAddress, volume: VolumeLevel): ChangeVolumeResult {
        // First, before anything can reach the network: off means no request at all, not a request
        // whose answer is ignored.
        if (!lockWrites.isOn) return ChangeVolumeResult.WritesDisabled
        return try {
            lockRepository.changeVolume(address, volume)
            ChangeVolumeResult.Changed(volume)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Throwable) {
            failure.toResult()
        }
    }

    private fun Throwable.toResult(): ChangeVolumeResult = when (this) {
        is SmartHomeException.TokenRejected -> ChangeVolumeResult.TokenRejected
        is SmartHomeException.TokenExpired -> ChangeVolumeResult.TokenExpired(serverMessage)
        is SmartHomeException.Offline -> ChangeVolumeResult.Offline
        is SmartHomeException.UnexpectedResponse -> ChangeVolumeResult.UnexpectedResponse
        else -> ChangeVolumeResult.Failed
    }
}

/** Everything changing the volume can end in (ADR-002), mirroring [LoadLockResult]'s categories. */
sealed interface ChangeVolumeResult {

    /** The partner accepted the level; only now may the screen show it (SPEC L7). */
    data class Changed(val volume: VolumeLevel) : ChangeVolumeResult

    /** This build was assembled with `smarthome.lockWritesEnabled=false`: nothing was sent. */
    data object WritesDisabled : ChangeVolumeResult

    /** The partner does not recognise the credential — HTTP 401 (SPEC S3, S6). */
    data object TokenRejected : ChangeVolumeResult

    /** The credential expired — HTTP 403; [serverMessage] is the partner's own sentence (SPEC S3.1). */
    data class TokenExpired(val serverMessage: String?) : ChangeVolumeResult

    /** The call never reached the partner (SPEC S4). */
    data object Offline : ChangeVolumeResult

    /** The answer was not the documented shape (SPEC E3). */
    data object UnexpectedResponse : ChangeVolumeResult

    /** The partner named an error of its own; its message stays in the exception (SPEC U6). */
    data object Failed : ChangeVolumeResult
}
