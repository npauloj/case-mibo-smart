package io.github.npauloj.mibosmart.app.devices

import io.github.npauloj.mibosmart.domain.device.CachedDevices
import io.github.npauloj.mibosmart.domain.device.Device
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * SPEC **U2** — "two taps to the picture": starting the app with a token already stored must put the
 * list on screen straight away, not a spinner that waits on the partner.
 *
 * It lives beside the device list because the list is what U2 measures; the token half of the start
 * is `TokenEntryViewModelTest`'s. `Dispatchers.setMain` is required because the load runs in
 * `viewModelScope`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionStartTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /**
     * The cached rows are on screen while page 1 is still in flight — asserted *before* the fake
     * partner answers, which is the only way to tell "rendered from the cache" from "rendered fast".
     */
    @Test
    fun cachedListRenderedBeforeNetwork() = runTest(dispatcher) {
        val network = CompletableDeferred<List<Device>>()
        val repository = FakeDeviceRepository(
            cached = { CachedDevices(listOf(device("MFR 1001")), NOW - 14.minutes) },
            answer = { network.await() },
        )

        val viewModel = DeviceListViewModel(listDevices = listDevices(repository), now = { NOW })
        advanceUntilIdle()

        assertEquals(1, repository.calls, "page 1 must already have been asked for")
        assertEquals(listOf("MFR 1001"), viewModel.state.value.rows.map { it.name })
        assertTrue(viewModel.state.value.isLoading, "the list is readable and still refreshing")
        assertNull(viewModel.state.value.staleFor, "nothing is stale until a request actually fails")

        network.complete(listOf(device("iM3-C")))
        advanceUntilIdle()

        assertEquals(listOf("iM3-C"), viewModel.state.value.rows.map { it.name })
        assertFalse(viewModel.state.value.isLoading)
    }

    /** Nothing cached is the old behaviour, unchanged: a spinner, then whatever page 1 says. */
    @Test
    fun withoutACacheTheListStillStartsOnTheSpinner() = runTest(dispatcher) {
        val network = CompletableDeferred<List<Device>>()
        val viewModel = DeviceListViewModel(
            listDevices = listDevices(FakeDeviceRepository { network.await() }),
            now = { NOW },
        )
        advanceUntilIdle()

        assertTrue(viewModel.state.value.isLoading)
        assertTrue(viewModel.state.value.rows.isEmpty())

        network.complete(listOf(device("iM3-C")))
        advanceUntilIdle()

        assertEquals(listOf("iM3-C"), viewModel.state.value.rows.map { it.name })
    }

    private companion object {
        val NOW = Instant.parse("2026-09-21T12:00:00Z")
    }
}
