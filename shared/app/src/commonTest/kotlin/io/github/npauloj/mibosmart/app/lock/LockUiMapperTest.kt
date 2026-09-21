package io.github.npauloj.mibosmart.app.lock

import io.github.npauloj.mibosmart.domain.device.DeviceStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** SPEC U3 and the offline half of L5: an offline lock says what it knows and how old that is. */
class LockUiMapperTest {

    /**
     * The values are not wrong, they are old — so the screen keeps them and date-stamps them.
     *
     * Dropping the last known state would leave the user with an empty screen for a door they can
     * see; showing it without its age would be a lie (`docs/research/user-feedback.md`, cluster 4).
     */
    @Test
    fun offlineStateCarriesLastSeen() {
        val destination = LockSamples.destination(
            status = DeviceStatus.Offline,
            lastSeen = LockSamples.Now - 3.hours,
        )

        val state = LockUiMapper.toUiState(destination, LoadLockResult.Loaded(LockSamples.Locked), LockSamples.Now)

        assertTrue(state.isOffline)
        assertEquals(LastSeen.Hours(3), state.lastSeen)
        val ready = assertIs<LockUiState.Ready>(state, "offline is not a failure to read: the hub answered")
        assertEquals(LockSamples.Locked, ready.lock, "an offline lock still shows its last known state")
    }

    @Test
    fun offlineWithoutATimestampSaysNever() {
        val destination = LockSamples.destination(status = DeviceStatus.Offline, lastSeen = null)

        val state = LockUiMapper.toUiState(destination, LoadLockResult.Loaded(LockSamples.Locked), LockSamples.Now)

        assertEquals(LastSeen.Never, state.lastSeen)
    }

    @Test
    fun anOnlineLockIsNotDateStamped() {
        val state = LockUiMapper.toUiState(
            LockSamples.destination(),
            LoadLockResult.Loaded(LockSamples.Locked),
            LockSamples.Now,
        )

        assertFalse(state.isOffline)
        assertNull(state.lastSeen)
    }

    /** The coarsest unit that still reads naturally, and never a negative or a zero (SPEC U3). */
    @Test
    fun ageIsShownInTheUnitThatReadsNaturally() {
        assertEquals(LastSeen.Moments, lastSeen(40.seconds))
        assertEquals(LastSeen.Minutes(5), lastSeen(5.minutes))
        assertEquals(LastSeen.Hours(2), lastSeen(2.hours + 30.minutes))
        assertEquals(LastSeen.Days(4), lastSeen(4.days + 1.hours))
        assertEquals(LastSeen.Moments, lastSeen((-2).hours), "a skewed clock must not read 'há -2 h'")
    }

    /** A failure keeps the offline label: the reason it failed does not make the lock present. */
    @Test
    fun aFailedReadStillCarriesTheOfflineLabel() {
        val destination = LockSamples.destination(
            status = DeviceStatus.Offline,
            lastSeen = LockSamples.Now - 10.minutes,
        )

        val state = LockUiMapper.toUiState(destination, LoadLockResult.Offline, LockSamples.Now)

        assertEquals(LastSeen.Minutes(10), state.lastSeen)
        assertEquals(LockError.Offline, assertIs<LockUiState.Failed>(state).error)
    }

    private fun lastSeen(age: kotlin.time.Duration): LastSeen? = LockUiMapper.toUiState(
        LockSamples.destination(status = DeviceStatus.Offline, lastSeen = LockSamples.Now - age),
        LoadLockResult.Loaded(LockSamples.Locked),
        LockSamples.Now,
    ).lastSeen
}
