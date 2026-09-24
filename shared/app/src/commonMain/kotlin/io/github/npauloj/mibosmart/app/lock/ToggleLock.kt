package io.github.npauloj.mibosmart.app.lock

import io.github.npauloj.mibosmart.domain.error.SmartHomeException
import io.github.npauloj.mibosmart.domain.lock.LockAddress
import io.github.npauloj.mibosmart.domain.lock.LockCommand
import io.github.npauloj.mibosmart.domain.lock.LockCommandOutcome
import io.github.npauloj.mibosmart.domain.lock.LockRepository
import io.github.npauloj.mibosmart.domain.lock.LockState
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Opening and closing a door, and the one thing that makes it safe: **a command is not a fact**
 * (SPEC L3, L4, L5).
 */
class ToggleLock(
    private val lockRepository: LockRepository,
    private val lockWrites: LockWritesSwitch,
) {

    /**
     * Sends [command] and waits, once, for the lock to say the same thing.
     * @param before the lock as the user saw it when tapping.
     */
    suspend operator fun invoke(
        address: LockAddress,
        command: LockCommand,
        before: LockState,
    ): ToggleLockResult {
        if (!lockWrites.isOn) return ToggleLockResult.WritesDisabled
        try {
            lockRepository.command(address, command)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Throwable) {
            return failure.toResult()
        }
        return settle(command, before, confirmation = readWithinTheWindow(address))
    }

    /**
     * The "Verificar" action of SPEC L4: exactly one more `status-abertura`, because the user
     * asked.
     */
    suspend fun verify(
        address: LockAddress,
        command: LockCommand,
        before: LockState,
    ): ToggleLockResult = try {
        settle(command, before, confirmation = lockRepository.readOpenState(address))
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Throwable) {
        failure.toResult()
    }

    /** The confirmation read, bounded by the window of SPEC L4. */
    private suspend fun readWithinTheWindow(address: LockAddress): Boolean? = try {
        withTimeoutOrNull(CONFIRMATION_WINDOW) { lockRepository.readOpenState(address) }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Throwable) {
        null
    }

    /** The domain owns the rule; this only names the two outcomes in this use case's vocabulary. */
    private fun settle(
        command: LockCommand,
        before: LockState,
        confirmation: Boolean?,
    ): ToggleLockResult = when (val outcome = LockCommandOutcome.of(command, before, confirmation)) {
        is LockCommandOutcome.Confirmed -> ToggleLockResult.Confirmed(outcome.lock)
        LockCommandOutcome.Expired ->
            ToggleLockResult.Unconfirmed(before.copy(isOpen = confirmation ?: before.isOpen))
    }

    private fun Throwable.toResult(): ToggleLockResult = when (this) {
        is SmartHomeException.TokenRejected -> ToggleLockResult.TokenRejected
        is SmartHomeException.TokenExpired -> ToggleLockResult.TokenExpired(serverMessage)
        is SmartHomeException.Offline -> ToggleLockResult.Offline
        is SmartHomeException.UnexpectedResponse -> ToggleLockResult.UnexpectedResponse
        else -> ToggleLockResult.Failed
    }

    internal companion object {

        /** `[ASSUMED]` (SPEC L4): how long the app waits for the device to confirm. */
        val CONFIRMATION_WINDOW = 10.seconds
    }
}

/**
 * Everything commanding a lock can end in (ADR-002), with [LoadLockResult]'s failure
 * categories.
 */
sealed interface ToggleLockResult {

    /** The confirmation read agreed: [lock] is the door as it reads now (SPEC L3). */
    data class Confirmed(val lock: LockState) : ToggleLockResult

    /**
     * The command was taken but the device did not confirm it (SPEC L4).
     * @property lock the freshest reading the app has — the disagreeing one when there was a
     * read, otherwise the lock as it was before.
     */
    data class Unconfirmed(val lock: LockState) : ToggleLockResult

    /** This build was assembled with `smarthome.lockWritesEnabled=false`: nothing was sent. */
    data object WritesDisabled : ToggleLockResult

    /** The partner does not recognise the credential — HTTP 401 (SPEC S3, S6). */
    data object TokenRejected : ToggleLockResult

    /** The credential expired — HTTP 403; [serverMessage] is the partner's own sentence (SPEC S3.1). */
    data class TokenExpired(val serverMessage: String?) : ToggleLockResult

    /** The call never reached the partner (SPEC S4, S5). */
    data object Offline : ToggleLockResult

    /** The answer was not the documented shape (SPEC E3). */
    data object UnexpectedResponse : ToggleLockResult

    /** The partner named an error of its own; its message stays in the exception (SPEC U6). */
    data object Failed : ToggleLockResult
}
