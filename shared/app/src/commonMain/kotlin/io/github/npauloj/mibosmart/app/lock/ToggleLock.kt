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
 *
 * `controle-fechadura` answers when the partner has taken the command, not when the lock has obeyed,
 * so [invoke] spends a second request re-reading `status-abertura` and reports what the lock says.
 * Two requests per command, always — and never a third on its own: [verify] exists because SPEC L4
 * forbids polling a device on a 300-request budget (ADR-006), and only a user's tap may call it.
 */
class ToggleLock(
    private val lockRepository: LockRepository,
    private val lockWrites: LockWritesSwitch,
) {

    /**
     * Sends [command] and waits, once, for the lock to say the same thing.
     *
     * @param before the lock as the user saw it when tapping. Only the door can have moved, so
     *   everything else in it survives the command unchanged, and it is what the screen restores to
     *   when the command never left (SPEC L5).
     */
    suspend operator fun invoke(
        address: LockAddress,
        command: LockCommand,
        before: LockState,
    ): ToggleLockResult {
        // First, before anything can reach the network: this is the call that moves a physical door,
        // so "off" has to mean no request at all.
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
     * The "Verificar" action of SPEC L4: exactly one more `status-abertura`, because the user asked.
     *
     * It differs from the confirmation inside [invoke] in one way, deliberately. There, a read that
     * fails leaves the command unconfirmed and says nothing more — the user is already being told the
     * door's state is unknown. Here the user asked a direct question, so a failure is reported as a
     * failure rather than as a silent "still not confirmed".
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

    /**
     * The confirmation read, bounded by the window of SPEC L4.
     *
     * A read that times out, or fails, is `null`: not knowing and being told the wrong thing are the
     * same outcome to a user standing at a door, and both must end in `CommandExpired` rather than in
     * a screen that settles on a state nothing reported.
     */
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
        // Unconfirmed still carries the freshest reading there is: a disagreeing read is news about
        // the door even though it is not the news the command asked for.
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

        /**
         * `[ASSUMED]` (SPEC L4): how long the app waits for the device to confirm.
         *
         * Enforced on a virtual clock in `ToggleLockTest.timeoutBecomesCommandExpired` and tuned
         * against the real lock in wave 3 — the number is a guess, the behaviour at the edge is not.
         */
        val CONFIRMATION_WINDOW = 10.seconds
    }
}

/**
 * Everything commanding a lock can end in (ADR-002), with [LoadLockResult]'s failure categories.
 *
 * The first two are the ones this use case exists to tell apart: what the device confirmed, and what
 * it merely did not deny.
 */
sealed interface ToggleLockResult {

    /** The confirmation read agreed: [lock] is the door as it reads now (SPEC L3). */
    data class Confirmed(val lock: LockState) : ToggleLockResult

    /**
     * The command was taken but the device did not confirm it (SPEC L4).
     *
     * @property lock the freshest reading the app has — the disagreeing one when there was a read,
     *   otherwise the lock as it was before. It is never the state that was asked for.
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
