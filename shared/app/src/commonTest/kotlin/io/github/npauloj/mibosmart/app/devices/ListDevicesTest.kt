package io.github.npauloj.mibosmart.app.devices

import io.github.npauloj.mibosmart.domain.device.CachedDevices
import io.github.npauloj.mibosmart.domain.error.SmartHomeException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

/**
 * SPEC D8: what the use case does when page 1 never arrives — which depends entirely on whether
 * there is a cache, and on *why* it failed.
 *
 * The transport half of the same criterion (what "offline" means on the wire) is asserted in
 * `:shared:data`'s `ListDevicesTest` with `MockEngine`; the states the user sees are
 * `DeviceListViewModelTest`'s.
 */
class ListDevicesTest {

    /** SPEC D8 with a cache: the rows the app already had, dated, instead of an empty error. */
    @Test
    fun offlineWithCacheShowsStale() = runTest {
        val listDevices = ListDevices(
            FakeDeviceRepository(
                cached = { CachedDevices(listOf(device("MFR 1001")), FETCHED_AT) },
                answer = { throw SmartHomeException.Offline(cause = null) },
            ),
        )

        val result = assertIs<DeviceListResult.Stale>(listDevices())

        assertEquals(listOf("MFR 1001"), result.devices.map { it.name })
        assertEquals(FETCHED_AT, result.fetchedAt, "without the timestamp the banner cannot say how old it is")
    }

    /** SPEC D8 without a cache: the error state, unchanged — there is nothing better to show. */
    @Test
    fun offlineWithoutCacheShowsError() = runTest {
        val listDevices = ListDevices(
            FakeDeviceRepository { throw SmartHomeException.Offline(cause = null) },
        )

        assertEquals(DeviceListResult.Offline, listDevices())
    }

    /**
     * SPEC D8 reads "fails for network reasons", and that is the whole of it: an expired token with
     * a cache must still send the user to the token screen (SPEC S6). Old rows behind a dead session
     * look like a working app and hide the one action left.
     */
    @Test
    fun anExpiredTokenIsNotHiddenBehindTheCache() = runTest {
        val listDevices = ListDevices(
            FakeDeviceRepository(
                cached = { CachedDevices(listOf(device("MFR 1001")), FETCHED_AT) },
                answer = { throw SmartHomeException.TokenExpired("Token expirado") },
            ),
        )

        assertIs<DeviceListResult.TokenExpired>(listDevices())
    }

    /** An empty cache is a cache miss: a "list" of nothing under an offline banner says less than the error. */
    @Test
    fun offlineWithAnEmptyCacheShowsError() = runTest {
        val listDevices = ListDevices(
            FakeDeviceRepository(
                cached = { CachedDevices(emptyList(), FETCHED_AT) },
                answer = { throw SmartHomeException.Offline(cause = null) },
            ),
        )

        assertEquals(DeviceListResult.Offline, listDevices())
    }

    /**
     * A corrupt or unreadable cache file costs the offline comfort of SPEC D8 and nothing else.
     *
     * Nothing above the use case can repair that file, so a screen that crashed on it would strand
     * the user on exactly the launch where they most need the retry button.
     */
    @Test
    fun aCacheThatThrowsIsACacheMiss() = runTest {
        val repository = FakeDeviceRepository(
            cached = { error("the database file is corrupt") },
            answer = { throw SmartHomeException.Offline(cause = null) },
        )

        assertEquals(DeviceListResult.Offline, ListDevices(repository)())
        assertEquals(emptyList(), ListDevices(repository).cached())
    }

    /** SPEC U2 and ADR-006: what is already known is free — reading it spends no request. */
    @Test
    fun readingTheCacheCostsNoRequest() = runTest {
        val repository = FakeDeviceRepository(cached = { CachedDevices(listOf(device("iM3-C")), FETCHED_AT) })

        assertEquals(listOf("iM3-C"), ListDevices(repository).cached().map { it.name })
        assertEquals(0, repository.calls)
    }

    private companion object {
        val FETCHED_AT = Instant.parse("2026-09-21T11:46:00Z")
    }
}
