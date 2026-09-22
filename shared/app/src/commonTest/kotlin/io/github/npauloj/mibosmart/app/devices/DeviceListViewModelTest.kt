package io.github.npauloj.mibosmart.app.devices

import app.cash.turbine.test
import io.github.npauloj.mibosmart.domain.device.CachedDevices
import io.github.npauloj.mibosmart.domain.device.Device
import io.github.npauloj.mibosmart.domain.device.DeviceKind
import io.github.npauloj.mibosmart.domain.device.OriginFilter
import io.github.npauloj.mibosmart.domain.error.SmartHomeException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
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

    /**
     * SPEC U2: the tap on a camera row *is* the navigation — one event, carrying the device the live
     * screen needs, with no state in between for a dialog to hang off.
     *
     * Turbine, because this is the one-shot event flow; the screen state next to it is still read
     * from `state.value`.
     */
    @Test
    fun cameraTapEmitsOpenLiveVideo() = runTest(dispatcher) {
        val repository = FakeDeviceRepository {
            listOf(device("iM3-C", kind = DeviceKind.Camera), device("MFR 1001", kind = DeviceKind.Lock))
        }
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.events.test {
            viewModel.onCameraTap(viewModel.state.value.rows.first { it.name == "iM3-C" })
            advanceUntilIdle()

            val event = assertIs<DeviceListEvent.OpenLiveVideo>(awaitItem())
            assertEquals("iM3-C", event.camera.name)
            assertEquals(DeviceKind.Camera, event.camera.kind)

            // SPEC D6: a lock has its own destination, and sending it to this one would open the live
            // screen on a device that has no video.
            viewModel.onCameraTap(viewModel.state.value.rows.first { it.name == "MFR 1001" })
            advanceUntilIdle()
            expectNoEvents()
        }
        assertEquals(1, repository.calls, "navigating must not reload the list (SPEC D7)")
    }

    /**
     * SPEC U2 and D6: a lock row opens the lock screen the way a camera row opens the picture — one
     * event, no intermediate screen, and the list left exactly as it was to come back to.
     */
    @Test
    fun lockTapEmitsOpenLock() = runTest(dispatcher) {
        val repository = FakeDeviceRepository { lockAndHub() }
        val viewModel = viewModel(repository)
        advanceUntilIdle()
        viewModel.selectFilter(OriginFilter.Linked)
        advanceUntilIdle()

        viewModel.events.test {
            viewModel.onLockTap(viewModel.lockRow())
            advanceUntilIdle()

            val event = assertIs<DeviceListEvent.OpenLock>(awaitItem())
            assertEquals(LOCK_NAME, event.lock.name)
            assertEquals(DeviceKind.Lock, event.lock.kind)

            // SPEC D7: the tap navigates and nothing else — so `onBack` finds the same chip and the
            // same rows, because this ViewModel never rebuilt them.
            assertEquals(OriginFilter.Linked, viewModel.state.value.filter)
            assertEquals(listOf(LOCK_NAME, HUB_NAME), viewModel.state.value.rows.map { it.name })
            expectNoEvents()
        }
    }

    /**
     * SPEC L1 and `docs/api-contract.md` §5: all four parts, each read off the lock's own row.
     *
     * The hub's product id is the part nothing else in the app can supply, and the partner sends it
     * on the sub-device (`idProdutoDispositivoPai`, §3) — which is why it is asserted beside the
     * other three and not assumed.
     */
    @Test
    fun openLockCarriesTheCompositeAddress() = runTest(dispatcher) {
        val viewModel = viewModel(FakeDeviceRepository { lockAndHub() })
        advanceUntilIdle()

        viewModel.events.test {
            viewModel.onLockTap(viewModel.lockRow())
            advanceUntilIdle()

            val address = assertIs<DeviceListEvent.OpenLock>(awaitItem()).address
            assertEquals(LOCK_NAMESPACE, address.lock.value)
            assertEquals(HUB_NAMESPACE, address.hub.value)
            assertEquals(HUB_PRODUCT_ID, address.hubProductId)
            assertEquals(LOCK_PRODUCT_ID, address.lockProductId)
        }
    }

    /**
     * SPEC D2, D6 and L1: the list is paged, so a lock is routinely on screen while its hub is not —
     * and that must not cost the user the lock, because the partner already put the hub's `ns` and
     * `idProduto` on the lock's own row (`docs/api-contract.md` §3).
     *
     * The page here has no hub row at all, which is the strongest form of the case: searching the
     * loaded rows for one would find nothing, so a tap that still opens the lock proves the address
     * came from the lock itself.
     */
    @Test
    fun aLockIsAddressableWithoutItsHubOnScreen() = runTest(dispatcher) {
        val viewModel = viewModel(FakeDeviceRepository { lockAndHub(withHub = false) })
        advanceUntilIdle()

        val row = viewModel.lockRow()
        assertNull(row.unavailable, "every part of the address is on the lock's own row")
        assertTrue(row.isActionable)

        viewModel.events.test {
            viewModel.onLockTap(row)
            advanceUntilIdle()

            // The hub's product id is the one part D-03 could only reach through the hub's row, so
            // it is what says the address really came from the lock's own. The other three are
            // `openLockCarriesTheCompositeAddress`'s.
            val address = assertIs<DeviceListEvent.OpenLock>(awaitItem()).address
            assertEquals(HUB_PRODUCT_ID, address.hubProductId)
            assertEquals(HUB_NAMESPACE, address.hub.value)
        }
    }

    /**
     * The guard the composite address depends on: a blank `idProduto` is a part of the namespace the
     * partner never sent, and an `ns` short of one part is another device's.
     *
     * Both sides are asserted because they are two different fields of the row — the lock's own
     * `idProduto`, sent beside the namespace, and its `idProdutoDispositivoPai`, joined *inside* it
     * (`docs/api-contract.md` §3, §5).
     */
    @Test
    fun aLockWithABlankProductIdIsNotActionable() = runTest(dispatcher) {
        val blanks = listOf(
            lockAndHub(lockProductId = ""),
            lockAndHub(hubProductId = ""),
        )

        blanks.forEach { page ->
            val viewModel = viewModel(FakeDeviceRepository { page })
            advanceUntilIdle()

            val row = viewModel.lockRow()
            assertEquals(LockAddressing.Unavailable.ProductIdMissing, row.unavailable)
            assertFalse(row.isActionable)

            viewModel.events.test {
                viewModel.onLockTap(row)
                advanceUntilIdle()

                expectNoEvents()
            }
        }
    }

    /**
     * A lock row that arrived without `idProdutoDispositivoPai` at all — absent, not blank.
     *
     * It takes the same path as a blank one on purpose: the user can do nothing about either, and
     * the only alternative to refusing is joining three parts and a hole into a namespace that
     * belongs to some other device (`docs/api-contract.md` §5).
     */
    @Test
    fun aLockWithoutAParentProductIdIsNotActionable() = runTest(dispatcher) {
        val viewModel = viewModel(FakeDeviceRepository { lockAndHub(hubProductId = null) })
        advanceUntilIdle()

        val row = viewModel.lockRow()
        assertEquals(LockAddressing.Unavailable.ProductIdMissing, row.unavailable)
        assertFalse(row.isActionable, "a row that cannot open must not offer the tap")

        viewModel.events.test {
            viewModel.onLockTap(row)
            advanceUntilIdle()

            expectNoEvents()
        }
    }

    /**
     * ADR-006: the whole point of assembling the address from loaded rows — opening a lock spends
     * nothing. The fake fails the test outright if it is asked for a page after the list has loaded.
     */
    @Test
    fun openingALockCallsThePartnerZeroTimes() = runTest(dispatcher) {
        var loaded = false
        val repository = FakeDeviceRepository {
            if (loaded) fail("opening a lock spent a partner request (ADR-006)")
            lockAndHub()
        }
        val viewModel = viewModel(repository)
        advanceUntilIdle()
        loaded = true

        viewModel.onLockTap(viewModel.lockRow())
        advanceUntilIdle()

        assertEquals(1, repository.calls, "only the list's own page 1")
    }

    /** SPEC D4: the list opens on the chip the user left it on, and asks for that `origem`. */
    @Test
    fun opensOnTheRememberedFilter() = runTest(dispatcher) {
        val repository = FakeDeviceRepository { listOf(device("iM3-C")) }
        val viewModel = DeviceListViewModel(
            listDevices = listDevices(repository, FakeDeviceListPreferences(OriginFilter.Shared)),
            now = { NOW },
        )
        advanceUntilIdle()

        assertEquals(OriginFilter.Shared, viewModel.state.value.filter)
        assertEquals(listOf(DeviceQuery(OriginFilter.Shared, page = 1)), repository.queries)
    }

    /** SPEC D4: choosing a chip reloads from page 1 with the new `origem` and records the choice. */
    @Test
    fun choosingAChipReloadsFromPageOneAndRemembersIt() = runTest(dispatcher) {
        val repository = FakeDeviceRepository { query ->
            if (query.origin == OriginFilter.All) listOf(device("iM3-C")) else listOf(device("MFR 1001"))
        }
        val preferences = FakeDeviceListPreferences()
        val viewModel =
            DeviceListViewModel(listDevices = listDevices(repository, preferences), now = { NOW })
        advanceUntilIdle()

        viewModel.selectFilter(OriginFilter.Linked)
        advanceUntilIdle()

        assertEquals(listOf("MFR 1001"), viewModel.state.value.rows.map { it.name })
        assertEquals(OriginFilter.Linked, viewModel.state.value.filter)
        assertEquals(listOf(OriginFilter.Linked), preferences.written)
        assertEquals(OriginFilter.Linked, preferences.readOriginFilter(), "the next launch must open on it")
    }

    /** ADR-006: the chip that is already on is not a change, and must not be paid for again. */
    @Test
    fun choosingTheChipAlreadyOnSpendsNoRequest() = runTest(dispatcher) {
        val repository = FakeDeviceRepository { listOf(device("iM3-C")) }
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.selectFilter(OriginFilter.All)
        advanceUntilIdle()

        assertEquals(1, repository.calls)
    }

    private fun viewModel(repository: FakeDeviceRepository) =
        DeviceListViewModel(listDevices = listDevices(repository), now = { NOW })

    /** The lock row as the screen would hand it back — by name, so a reorder cannot pick the hub. */
    private fun DeviceListViewModel.lockRow(): DeviceRow =
        state.value.rows.single { it.name == LOCK_NAME }

    private companion object {
        val NOW = Instant.parse("2026-09-21T12:00:00Z")
    }
}
