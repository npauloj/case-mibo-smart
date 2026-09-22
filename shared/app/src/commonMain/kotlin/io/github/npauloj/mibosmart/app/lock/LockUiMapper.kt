package io.github.npauloj.mibosmart.app.lock

import io.github.npauloj.mibosmart.domain.device.Device
import io.github.npauloj.mibosmart.domain.device.DeviceStatus
import io.github.npauloj.mibosmart.domain.lock.LockCommand
import io.github.npauloj.mibosmart.domain.lock.LockState
import io.github.npauloj.mibosmart.domain.lock.VolumeLevel
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Turns a [LoadLockResult] into the one value the lock screen renders (ADR-003).
 *
 * It is a pure function of the destination, the result and the current instant so that "há 5 min"
 * can be asserted with a fixed clock instead of being read off the machine running the tests.
 */
internal object LockUiMapper {

    /** The screen while the three reads are in flight; what is already known is shown at once. */
    fun loading(destination: LockDestination, now: Instant): LockUiState = LockUiState.Loading(
        deviceName = destination.device.name,
        lastSeen = lastSeenOrNull(destination.device, now),
    )

    /**
     * @param areWritesEnabled the build's [LockWritesSwitch], carried into [LockUiState.Ready] so the
     *   screen can disable what it will not do before the user asks for it.
     */
    fun toUiState(
        destination: LockDestination,
        result: LoadLockResult,
        now: Instant,
        areWritesEnabled: Boolean,
    ): LockUiState {
        val name = destination.device.name
        // An offline lock keeps whatever the hub last knew about it, labelled with its age
        // (SPEC L5, U3) — the values are not wrong, they are old, and saying so is the difference
        // between a stale screen and a lying one.
        val lastSeen = lastSeenOrNull(destination.device, now)
        return when (result) {
            is LoadLockResult.Loaded -> LockUiState.Ready(name, result.lock, lastSeen, areWritesEnabled)
            LoadLockResult.TokenRejected -> LockUiState.Failed(name, LockError.TokenRejected, lastSeen = lastSeen)
            is LoadLockResult.TokenExpired ->
                LockUiState.Failed(name, LockError.TokenExpired, result.serverMessage, lastSeen)

            LoadLockResult.Offline -> LockUiState.Failed(name, LockError.Offline, lastSeen = lastSeen)
            LoadLockResult.UnexpectedResponse ->
                LockUiState.Failed(name, LockError.UnexpectedResponse, lastSeen = lastSeen)

            LoadLockResult.Failed -> LockUiState.Failed(name, LockError.Failed, lastSeen = lastSeen)
        }
    }

    /**
     * The screen after a command and its one confirmation read answered (SPEC L3, L4, L5).
     *
     * Three destinations and no fourth: the lock the device confirmed, an unconfirmed command the
     * user can check, or a notice that the command did not leave — and the last two both keep
     * [before]'s readings, so nothing the screen already showed is lost to a failure.
     *
     * [ToggleLockResult.Unconfirmed] carries the freshest reading rather than the requested one; a
     * disagreeing `status-abertura` is news about the door, and `CommandExpired` shows it beside the
     * command that did not take.
     */
    fun afterCommand(
        before: LockUiState.Ready,
        command: LockCommand,
        result: ToggleLockResult,
    ): LockUiState = when (result) {
        is ToggleLockResult.Confirmed -> before.settled(result.lock)
        is ToggleLockResult.Unconfirmed ->
            LockUiState.CommandExpired(before.settled(result.lock), command)

        // The screen already says this build sends nothing to the lock, and nothing was sent.
        ToggleLockResult.WritesDisabled -> before.settled(before.lock)
        ToggleLockResult.TokenRejected -> before.commandFailed(command, LockError.TokenRejected)
        is ToggleLockResult.TokenExpired ->
            before.commandFailed(command, LockError.TokenExpired, result.serverMessage)

        ToggleLockResult.Offline -> before.commandFailed(command, LockError.Offline)
        ToggleLockResult.UnexpectedResponse -> before.commandFailed(command, LockError.UnexpectedResponse)
        ToggleLockResult.Failed -> before.commandFailed(command, LockError.Failed)
    }

    /**
     * The screen after the user's one extra read (SPEC L4).
     *
     * A read that agreed settles the screen; a read that disagreed stays unconfirmed but updates the
     * readings under it, so "Verificar" is never a button that visibly does nothing. A read that
     * could not answer says so beside the action instead of pretending the door is still unknown for
     * some other reason (SPEC U6).
     */
    fun afterVerifying(
        checking: LockUiState.CommandExpired,
        result: ToggleLockResult,
    ): LockUiState = when (result) {
        is ToggleLockResult.Confirmed -> checking.before.settled(result.lock)
        is ToggleLockResult.Unconfirmed ->
            checking.copy(before = checking.before.settled(result.lock), isChecking = false)

        ToggleLockResult.WritesDisabled -> checking.copy(isChecking = false)
        ToggleLockResult.TokenRejected -> checking.checkFailed(LockError.TokenRejected)
        is ToggleLockResult.TokenExpired -> checking.checkFailed(LockError.TokenExpired)
        ToggleLockResult.Offline -> checking.checkFailed(LockError.Offline)
        ToggleLockResult.UnexpectedResponse -> checking.checkFailed(LockError.UnexpectedResponse)
        ToggleLockResult.Failed -> checking.checkFailed(LockError.Failed)
    }

    /**
     * The screen after `mudar-volume` answered (SPEC L7).
     *
     * [before] is the lock as the user saw it when picking [level]. The new level is written into it
     * **only** on [ChangeVolumeResult.Changed], so every other branch leaves the selector showing the
     * level the hardware is actually at — there is no optimistic step to undo.
     */
    fun afterVolumeChange(
        before: LockUiState.Ready,
        level: VolumeLevel,
        result: ChangeVolumeResult,
    ): LockUiState.Ready = when (result) {
        is ChangeVolumeResult.Changed -> before.settled(before.lock.copy(volume = result.volume))
        // The screen already says lock writes are off in this build — `areWritesEnabled` is on the
        // state and the selector is disabled — so there is nothing new to announce here.
        ChangeVolumeResult.WritesDisabled -> before.settled(before.lock)
        ChangeVolumeResult.TokenRejected -> before.failed(LockWrite.Volume(level), LockError.TokenRejected)
        is ChangeVolumeResult.TokenExpired ->
            before.failed(LockWrite.Volume(level), LockError.TokenExpired, result.serverMessage)

        ChangeVolumeResult.Offline -> before.failed(LockWrite.Volume(level), LockError.Offline)
        ChangeVolumeResult.UnexpectedResponse ->
            before.failed(LockWrite.Volume(level), LockError.UnexpectedResponse)

        ChangeVolumeResult.Failed -> before.failed(LockWrite.Volume(level), LockError.Failed)
    }

    /**
     * The screen after `habilitar-abrir-remoto` answered (SPEC L2).
     *
     * On success the flag comes from [EnableRemoteOpenResult.Refreshed] — the re-read of
     * `status-abrir-remoto`, not from the write having returned — and on every failure the lock is
     * left exactly as it was read, which is what "the open/close control stays disabled" means in
     * state terms.
     */
    fun afterEnablingRemoteOpen(
        before: LockUiState.Ready,
        result: EnableRemoteOpenResult,
    ): LockUiState.Ready = when (result) {
        is EnableRemoteOpenResult.Refreshed ->
            before.settled(before.lock.copy(isRemoteOpenEnabled = result.isRemoteOpenEnabled))

        EnableRemoteOpenResult.WritesDisabled -> before.settled(before.lock)
        EnableRemoteOpenResult.TokenRejected -> before.failed(LockWrite.RemoteOpen, LockError.TokenRejected)
        is EnableRemoteOpenResult.TokenExpired ->
            before.failed(LockWrite.RemoteOpen, LockError.TokenExpired, result.serverMessage)

        EnableRemoteOpenResult.Offline -> before.failed(LockWrite.RemoteOpen, LockError.Offline)
        EnableRemoteOpenResult.UnexpectedResponse ->
            before.failed(LockWrite.RemoteOpen, LockError.UnexpectedResponse)

        EnableRemoteOpenResult.Failed -> before.failed(LockWrite.RemoteOpen, LockError.Failed)
    }

    /** Nothing in flight and nothing to explain: [lock] is what the partner last confirmed. */
    private fun LockUiState.Ready.settled(lock: LockState): LockUiState.Ready =
        copy(lock = lock, writeInFlight = null, writeFailure = null)

    /**
     * A write that did not happen: the reading is untouched and the reason sits beside its control.
     *
     * A failed write is not a failed screen (SPEC U6) — the lock is still readable, so this stays a
     * [LockUiState.Ready] rather than collapsing into [LockUiState.Failed] and losing what was read.
     */
    /** SPEC L5: the command did not leave, so the lock under the notice is the lock as it was read. */
    private fun LockUiState.Ready.commandFailed(
        command: LockCommand,
        error: LockError,
        serverMessage: String? = null,
    ): LockUiState.CommandFailed = LockUiState.CommandFailed(settled(lock), command, error, serverMessage)

    /** The extra read could not answer: still unconfirmed, and now the user knows why (SPEC U6). */
    private fun LockUiState.CommandExpired.checkFailed(error: LockError): LockUiState.CommandExpired =
        copy(isChecking = false, checkFailure = error)

    private fun LockUiState.Ready.failed(
        write: LockWrite,
        error: LockError,
        serverMessage: String? = null,
    ): LockUiState.Ready = copy(writeInFlight = null, writeFailure = WriteFailure(write, error, serverMessage))

    /** How old the lock's presence is, or `null` while it is online — only an offline lock says it. */
    private fun lastSeenOrNull(device: Device, now: Instant): LastSeen? {
        if (device.status == DeviceStatus.Online) return null
        val lastSeen = device.lastSeen ?: return LastSeen.Never
        return elapsedSince(past = lastSeen, now = now)
    }

    /**
     * How long ago [past] was, as of [now] — the one place this screen's feature rounds time.
     *
     * Internal rather than private because the history tab counts the same way: "há 5 min" on an
     * opening (SPEC U4) and "última atualização há 5 min" on an offline lock (SPEC U3) are two
     * sentences over one rule, and a second copy of the thresholds is how they drift apart.
     */
    internal fun elapsedSince(past: Instant, now: Instant): LastSeen {
        val elapsed = now - past
        return when {
            elapsed < Duration.ZERO || elapsed.inWholeMinutes < 1 -> LastSeen.Moments
            elapsed.inWholeHours < 1 -> LastSeen.Minutes(elapsed.inWholeMinutes.toInt())
            elapsed.inWholeDays < 1 -> LastSeen.Hours(elapsed.inWholeHours.toInt())
            else -> LastSeen.Days(elapsed.inWholeDays.toInt())
        }
    }
}

/**
 * How long ago the lock was last seen, in the coarsest unit that still reads naturally (SPEC U3).
 *
 * The screen owns the words — "há 5 min" is a Compose resource (SPEC E6) — so what crosses the
 * mapper is the number and its unit, never a formatted sentence.
 */
sealed interface LastSeen {

    /** The partner never reported the device online: "nunca visto online". */
    data object Never : LastSeen

    /** Less than a minute ago, which no number reads well for. */
    data object Moments : LastSeen

    data class Minutes(val value: Int) : LastSeen

    data class Hours(val value: Int) : LastSeen

    data class Days(val value: Int) : LastSeen
}
