package io.github.npauloj.mibosmart.app.devices

import io.github.npauloj.mibosmart.domain.device.CachedDevices
import io.github.npauloj.mibosmart.domain.device.Device
import io.github.npauloj.mibosmart.domain.device.DeviceKind
import io.github.npauloj.mibosmart.domain.error.SmartHomeException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.yield
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * The four states of SPEC §2 as the screen sees them, read from `state.value` after
 * `advanceUntilIdle()` — never with Turbine, which this repository reserves for one-shot event flows.
 *
 * `Dispatchers.setMain` is required because the load runs in `viewModelScope`, which is pinned to the
 * main dispatcher.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DeviceListViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /** SPEC D1 and ADR-006: entering the list costs exactly one request, and it starts loading. */
    @Test
    fun openingTheListLoadsPageOneExactlyOnce() = runTest(dispatcher) {
        val repository = FakeDeviceRepository { listOf(device("iM7-FC")) }

        val viewModel = viewModel(repository)
        assertTrue(viewModel.state.value.isLoading, "the list must show it is working")
        advanceUntilIdle()

        assertEquals(1, repository.calls)
        assertEquals(listOf("iM7-FC"), viewModel.state.value.rows.map { it.name })
        assertFalse(viewModel.state.value.isLoading)
        assertNull(viewModel.state.value.error)
    }

    /** SPEC D3: page 1 with nothing in it is the empty state, not an error. */
    @Test
    fun anEmptyPageOneIsTheEmptyState() = runTest(dispatcher) {
        val viewModel = viewModel(FakeDeviceRepository { emptyList() })
        advanceUntilIdle()

        assertTrue(viewModel.state.value.isEmpty)
        assertNull(viewModel.state.value.error)
    }

    /** SPEC D8 without a cache: the error state, with a retry that makes the second request. */
    @Test
    fun aNetworkFailureIsTheErrorStateAndRetryCallsAgain() = runTest(dispatcher) {
        var failing = true
        val repository = FakeDeviceRepository {
            if (failing) throw SmartHomeException.Offline(cause = null) else listOf(device("MFR 1001"))
        }

        val viewModel = viewModel(repository)
        advanceUntilIdle()
        assertEquals(DeviceListError.Offline, viewModel.state.value.error)
        assertTrue(viewModel.state.value.rows.isEmpty(), "there is no cache to fall back on yet")

        failing = false
        viewModel.load()
        advanceUntilIdle()

        assertEquals(2, repository.calls)
        assertNull(viewModel.state.value.error)
        assertEquals(listOf("MFR 1001"), viewModel.state.value.rows.map { it.name })
    }

    /**
     * SPEC D8 with a cache: the rows stay, and the state carries how old they are so the banner can
     * say "última atualização há 14 min" instead of the screen going blank.
     */
    @Test
    fun aNetworkFailureWithACacheKeepsTheRowsAndTheirAge() = runTest(dispatcher) {
        val repository = FakeDeviceRepository(
            cached = { CachedDevices(listOf(device("MFR 1001")), NOW - 14.minutes) },
            answer = { throw SmartHomeException.Offline(cause = null) },
        )

        val viewModel = viewModel(repository)
        advanceUntilIdle()

        assertEquals(listOf("MFR 1001"), viewModel.state.value.rows.map { it.name })
        assertEquals(Elapsed(14, ElapsedUnit.Minutes), viewModel.state.value.staleFor)
        assertNull(viewModel.state.value.error, "the rows are real; the banner is what says they are old")
        assertFalse(viewModel.state.value.isLoading)
    }

    /**
     * Retrying from the stale state must not blank the list: the rows stay readable while the
     * request runs, and the "sem conexão" banner goes — the app is trying again, not offline.
     */
    @Test
    fun retryingFromTheStaleStateKeepsTheRowsAndDropsTheBanner() = runTest(dispatcher) {
        val retry = CompletableDeferred<List<Device>>()
        var failing = true
        val repository = FakeDeviceRepository(
            cached = { CachedDevices(listOf(device("MFR 1001")), NOW - 14.minutes) },
            answer = { if (failing) throw SmartHomeException.Offline(cause = null) else retry.await() },
        )
        val viewModel = viewModel(repository)
        advanceUntilIdle()
        assertEquals(Elapsed(14, ElapsedUnit.Minutes), viewModel.state.value.staleFor)

        failing = false
        viewModel.load()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.isLoading, "the retry is in flight")
        assertEquals(listOf("MFR 1001"), viewModel.state.value.rows.map { it.name })
        assertNull(viewModel.state.value.staleFor, "the app is trying again; it is not offline right now")

        retry.complete(listOf(device("iM3-C")))
        advanceUntilIdle()

        assertEquals(listOf("iM3-C"), viewModel.state.value.rows.map { it.name })
    }

    /** SPEC S3.1 / U6: the 403 sentence is the partner's, and it is the one worth showing. */
    @Test
    fun anExpiredSessionKeepsTheServersSentence() = runTest(dispatcher) {
        val message = "Token expirado, por favor gere um novo token"
        val viewModel = viewModel(FakeDeviceRepository { throw SmartHomeException.TokenExpired(message) })
        advanceUntilIdle()

        assertEquals(DeviceListError.TokenExpired, viewModel.state.value.error)
        assertEquals(message, viewModel.state.value.serverMessage)
    }

    /** ADR-006: pressing retry twice while a request is in flight still spends one request. */
    @Test
    fun aSecondLoadWhileOneIsInFlightIsIgnored() = runTest(dispatcher) {
        // `yield()` is what makes the first load still be running when the other two start; without a
        // suspension point the fake answers inside one dispatch and the race cannot be reproduced.
        val repository = FakeDeviceRepository {
            yield()
            emptyList()
        }
        val viewModel = viewModel(repository)

        viewModel.load()
        viewModel.load()
        advanceUntilIdle()

        assertEquals(1, repository.calls)
    }

    /** SPEC U3: the elapsed time is measured once per load, from the clock the ViewModel was given. */
    @Test
    fun theRowsAreDatedFromTheInjectedClock() = runTest(dispatcher) {
        val repository = FakeDeviceRepository {
            listOf(device("iM3-C", kind = DeviceKind.Camera, isOnline = false, lastSeen = NOW - 1.hours))
        }

        val viewModel = viewModel(repository)
        advanceUntilIdle()

        assertEquals(LastSeen.Ago(1, ElapsedUnit.Hours), viewModel.state.value.rows.single().lastSeen)
    }

    private fun viewModel(repository: FakeDeviceRepository) =
        DeviceListViewModel(listDevices = ListDevices(repository), now = { NOW })

    private companion object {
        val NOW = Instant.parse("2026-09-21T12:00:00Z")
    }
}
