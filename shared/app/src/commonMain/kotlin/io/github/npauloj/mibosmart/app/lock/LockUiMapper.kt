package io.github.npauloj.mibosmart.app.lock

import io.github.npauloj.mibosmart.domain.device.Device
import io.github.npauloj.mibosmart.domain.device.DeviceStatus
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

    fun toUiState(destination: LockDestination, result: LoadLockResult, now: Instant): LockUiState {
        val name = destination.device.name
        // An offline lock keeps whatever the hub last knew about it, labelled with its age
        // (SPEC L5, U3) — the values are not wrong, they are old, and saying so is the difference
        // between a stale screen and a lying one.
        val lastSeen = lastSeenOrNull(destination.device, now)
        return when (result) {
            is LoadLockResult.Loaded -> LockUiState.Ready(name, result.lock, lastSeen)
            LoadLockResult.TokenRejected -> LockUiState.Failed(name, LockError.TokenRejected, lastSeen = lastSeen)
            is LoadLockResult.TokenExpired ->
                LockUiState.Failed(name, LockError.TokenExpired, result.serverMessage, lastSeen)

            LoadLockResult.Offline -> LockUiState.Failed(name, LockError.Offline, lastSeen = lastSeen)
            LoadLockResult.UnexpectedResponse ->
                LockUiState.Failed(name, LockError.UnexpectedResponse, lastSeen = lastSeen)

            LoadLockResult.Failed -> LockUiState.Failed(name, LockError.Failed, lastSeen = lastSeen)
        }
    }

    /** How old the lock's presence is, or `null` while it is online — only an offline lock says it. */
    private fun lastSeenOrNull(device: Device, now: Instant): LastSeen? {
        if (device.status == DeviceStatus.Online) return null
        val lastSeen = device.lastSeen ?: return LastSeen.Never
        return elapsedSince(lastSeen, now)
    }

    private fun elapsedSince(lastSeen: Instant, now: Instant): LastSeen {
        val elapsed = now - lastSeen
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
