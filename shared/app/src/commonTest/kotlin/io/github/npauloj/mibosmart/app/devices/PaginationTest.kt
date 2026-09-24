package io.github.npauloj.mibosmart.app.devices

import io.github.npauloj.mibosmart.app.devices.FakeDeviceRepository.Companion.FULL_PAGE
import io.github.npauloj.mibosmart.domain.device.Device
import io.github.npauloj.mibosmart.domain.device.OriginFilter
import io.github.npauloj.mibosmart.domain.error.SmartHomeException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * SPEC **D2** (where the list ends) and **D11** (one request in flight), which are the same
 * problem seen from two sides: the account pays per request (ADR-006), so every rule here is
 * ultimately about which requests are *not* sent.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PaginationTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /** SPEC D2: a page exactly as long as the one asked for is the only evidence of a next one. */
    @Test
    fun fullPageHasMore() = runTest(dispatcher) {
        val repository = FakeDeviceRepository { query -> page(FULL_PAGE, "p${query.page}") }

        val viewModel = viewModel(repository)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.hasMore, "a full page must offer the next one")

        viewModel.loadMore()
        advanceUntilIdle()

        assertEquals(listOf(query(page = 1), query(page = 2)), repository.queries)
        assertEquals(FULL_PAGE * 2, viewModel.state.value.rows.size, "page 2 is appended, not swapped in")
        assertEquals("p1-0", viewModel.state.value.rows.first().name, "the rows already read must not move")
        assertTrue(viewModel.state.value.hasMore, "page 2 was full too")
    }

    /** SPEC D2: a page shorter than requested is the end of the list, and the end of the requests. */
    @Test
    fun shortPageEndsList() = runTest(dispatcher) {
        val repository = FakeDeviceRepository { query ->
            if (query.page == 1) page(FULL_PAGE, "p1") else page(5, "p2")
        }

        val viewModel = viewModel(repository)
        advanceUntilIdle()
        viewModel.loadMore()
        advanceUntilIdle()

        assertEquals(FULL_PAGE + 5, viewModel.state.value.rows.size)
        assertFalse(viewModel.state.value.hasMore, "a short page is the last page")

        viewModel.loadMore()
        advanceUntilIdle()

        assertEquals(2, repository.calls, "the list has ended; nothing may ask again")
    }

    /**
     * SPEC D2: an empty page ends the list too — and costs exactly the one request that
     * discovered it. The rows already on screen stay: the page before it was simply the last.
     */
    @Test
    fun emptyPageEndsListWithoutExtraCall() = runTest(dispatcher) {
        val repository = FakeDeviceRepository { query ->
            if (query.page == 1) page(FULL_PAGE, "p1") else emptyList()
        }

        val viewModel = viewModel(repository)
        advanceUntilIdle()
        viewModel.loadMore()
        advanceUntilIdle()

        assertEquals(FULL_PAGE, viewModel.state.value.rows.size, "an empty page 2 must not empty the list")
        assertFalse(viewModel.state.value.hasMore)
        assertFalse(viewModel.state.value.isEmpty, "the list is not empty; it is finished")

        viewModel.loadMore()
        viewModel.loadMore()
        advanceUntilIdle()

        assertEquals(2, repository.calls)
    }

    /** SPEC D11: the answer to a query nobody is looking at any more never reaches the list. */
    @Test
    fun staleResponseFromPreviousFilterIsDropped() = runTest(dispatcher) {
        val late = CompletableDeferred<List<Device>>()
        val repository = FakeDeviceRepository { query ->
            if (query.origin == OriginFilter.All) {
                withContext(NonCancellable) { late.await() }
            } else {
                page(2, "shared")
            }
        }

        val viewModel = viewModel(repository)
        advanceUntilIdle()
        viewModel.selectFilter(OriginFilter.Shared)
        advanceUntilIdle()
        assertEquals(listOf("shared-0", "shared-1"), viewModel.state.value.rows.map { it.name })

        late.complete(page(3, "all"))
        advanceUntilIdle()

        assertEquals(
            listOf("shared-0", "shared-1"),
            viewModel.state.value.rows.map { it.name },
            "a page fetched for the previous filter reached the list",
        )
        assertEquals(OriginFilter.Shared, viewModel.state.value.filter)
    }

    /** SPEC D11: a fast scroll past the end of a page asks for the next one once, not five times. */
    @Test
    fun concurrentNextPageTriggersMakeOneRequest() = runTest(dispatcher) {
        val repository = FakeDeviceRepository { query ->
            yield()
            page(FULL_PAGE, "p${query.page}")
        }

        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.loadMore()
        viewModel.loadMore()
        viewModel.loadMore()
        advanceUntilIdle()

        assertEquals(listOf(query(page = 1), query(page = 2)), repository.queries)
        assertEquals(FULL_PAGE * 2, viewModel.state.value.rows.size, "one page was loaded, once")
    }

    /**
     * SPEC D11: changing the chip cancels the request in flight — the cancellation reaches the
     * suspended call itself, which is what stops the account paying for a page nobody will see.
     */
    @Test
    fun filterChangeCancelsInFlightRequest() = runTest(dispatcher) {
        val inFlight = CompletableDeferred<List<Device>>()
        var cancelled = false
        val repository = FakeDeviceRepository { query ->
            if (query.origin == OriginFilter.All) {
                try {
                    inFlight.await()
                } catch (cancellation: CancellationException) {
                    cancelled = true
                    throw cancellation
                }
            } else {
                page(2, "linked")
            }
        }

        val viewModel = viewModel(repository)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.isLoading, "page 1 is in flight")

        viewModel.selectFilter(OriginFilter.Linked)
        advanceUntilIdle()

        assertTrue(cancelled, "the request in flight was left running")
        assertEquals(
            listOf(query(page = 1), query(OriginFilter.Linked, page = 1)),
            repository.queries,
            "the new filter reloads from page 1",
        )
        assertEquals(listOf("linked-0", "linked-1"), viewModel.state.value.rows.map { it.name })
    }

    /**
     * SPEC D2 and D8 at the end of the list: a next page that fails leaves the rows alone,
     * stops the list asking on its own, and is retried as *that* page — not the one after it.
     */
    @Test
    fun aFailedNextPageKeepsTheRowsAndIsRetriedAsTheSamePage() = runTest(dispatcher) {
        var failing = true
        val repository = FakeDeviceRepository { query ->
            when {
                query.page == 1 -> page(FULL_PAGE, "p1")
                failing -> throw SmartHomeException.Offline(cause = null)
                else -> page(5, "p2")
            }
        }

        val viewModel = viewModel(repository)
        advanceUntilIdle()
        viewModel.loadMore()
        advanceUntilIdle()

        assertEquals(FULL_PAGE, viewModel.state.value.rows.size, "the list the user was reading is gone")
        assertEquals(DeviceListError.Offline, viewModel.state.value.error)
        assertTrue(viewModel.state.value.hasMore, "the page is still out there; the footer offers it")

        failing = false
        viewModel.loadMore()
        advanceUntilIdle()

        assertEquals(
            listOf(query(page = 1), query(page = 2), query(page = 2)),
            repository.queries,
            "the failed page was skipped instead of retried",
        )
        assertEquals(FULL_PAGE + 5, viewModel.state.value.rows.size)
        assertNull(viewModel.state.value.error)
    }

    /** A device listed on two pages is one row, not two. */
    @Test
    fun aDeviceRepeatedAcrossPagesIsListedOnce() = runTest(dispatcher) {
        val repeated = device(name = "iM3-C", id = "PLACEHOLDER-NS-REPEATED")
        val repository = FakeDeviceRepository { query ->
            if (query.page == 1) page(FULL_PAGE - 1, "p1") + repeated else listOf(repeated, device("MFR 1001"))
        }

        val viewModel = viewModel(repository)
        advanceUntilIdle()
        viewModel.loadMore()
        advanceUntilIdle()

        val ids = viewModel.state.value.rows.map { it.id }
        assertEquals(ids.distinct(), ids, "the same device was listed twice")
        assertEquals(FULL_PAGE + 1, ids.size)
    }

    /** SPEC D11 and D2 together: the page counter follows the filter, it does not carry over. */
    @Test
    fun aNewFilterStartsFromPageOneAgain() = runTest(dispatcher) {
        val repository = FakeDeviceRepository { query -> page(FULL_PAGE, "p${query.page}") }

        val viewModel = viewModel(repository)
        advanceUntilIdle()
        viewModel.loadMore()
        advanceUntilIdle()
        viewModel.selectFilter(OriginFilter.Linked)
        advanceUntilIdle()
        viewModel.loadMore()
        advanceUntilIdle()

        assertEquals(
            listOf(
                query(page = 1),
                query(page = 2),
                query(OriginFilter.Linked, page = 1),
                query(OriginFilter.Linked, page = 2),
            ),
            repository.queries,
        )
    }

    private fun viewModel(repository: FakeDeviceRepository) =
        DeviceListViewModel(listDevices = listDevices(repository), now = { NOW })

    private fun query(origin: OriginFilter = OriginFilter.All, page: Int) = DeviceQuery(origin, page)

    /** [size] devices whose names say which page they came from, so append order is readable. */
    private fun page(size: Int, prefix: String): List<Device> =
        List(size) { index -> device(name = "$prefix-$index", id = "PLACEHOLDER-$prefix-$index") }

    private companion object {
        val NOW = Instant.parse("2026-09-21T12:00:00Z")
    }
}
