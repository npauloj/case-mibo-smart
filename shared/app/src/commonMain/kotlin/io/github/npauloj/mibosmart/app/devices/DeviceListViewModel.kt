package io.github.npauloj.mibosmart.app.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.npauloj.mibosmart.domain.device.Device
import io.github.npauloj.mibosmart.domain.device.DeviceKind
import io.github.npauloj.mibosmart.domain.device.ModelCatalog
import io.github.npauloj.mibosmart.domain.device.OriginFilter
import io.github.npauloj.mibosmart.domain.device.RawCodes
import io.github.npauloj.mibosmart.domain.lock.LockAddress
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.launch

/** Everything the device list shows, in one immutable value (ADR-003). */
data class DeviceListUiState(
    val isLoading: Boolean = false,
    val rows: List<DeviceRow> = emptyList(),
    /**
     * The last request that failed, and two different screens depending on what is under it:
     * with no [rows] it *is* the screen (SPEC D8); with rows on it, a next page failed and it
     * is a line at the end of a list that still works (SPEC D2).
     */
    val error: DeviceListError? = null,
    /** The partner's own sentence, set only by [DeviceListError.TokenExpired] (SPEC S3.1, U6). */
    val serverMessage: String? = null,
    /**
     * How old [rows] are, set only when they came from the cache because the request failed
     * (SPEC D8).
     */
    val staleFor: Elapsed? = null,
    /** Which chip is on, and which `origem` the rows were fetched with (SPEC D4). */
    val filter: OriginFilter = OriginFilter.All,
    /** A page past the first is in flight: the footer spinner, never the whole-screen one (SPEC D2). */
    val isLoadingMore: Boolean = false,
    /** The last page came back full, so another one may exist (SPEC D2). */
    val hasMore: Boolean = false,
    /** The user pulled the list down (SPEC D7) — the same reload, drawn as the pull indicator. */
    val isRefreshing: Boolean = false,
) {
    /** Page 1 answered, and it had nothing in it — the empty state, not an error (SPEC D3). */
    val isEmpty: Boolean get() = !isLoading && error == null && rows.isEmpty()
}

/** The reasons the list can fail, one user-facing message each (SPEC E2, U6). */
enum class DeviceListError { TokenRejected, TokenExpired, Offline, DeviceNotFound, UnexpectedResponse, Failed }

/** What the list asks the navigator for, once each — a one-shot event, not state (ADR-003). */
sealed interface DeviceListEvent {

    /** SPEC U2: a camera row opens the live screen directly — no intermediate screen or dialog. */
    data class OpenLiveVideo(val camera: Device) : DeviceListEvent

    /**
     * SPEC U2 and D6: a lock row opens the lock screen the same way, with the lock already
     * addressed.
     */
    data class OpenLock(val lock: Device, val address: LockAddress) : DeviceListEvent
}

/**
 * The device list: one state, intents as functions, one request in flight (ADR-003, SPEC D11).
 * @param catalog the partner's words for its own model codes (SPEC D5, ADR-007).
 * @param now where "visto pela última vez há X" is measured from.
 */
class DeviceListViewModel(
    private val listDevices: ListDevices,
    private val catalog: ModelCatalog = RawCodes,
    private val now: () -> Instant = { Clock.System.now() },
) : ViewModel() {

    private val mutableState = MutableStateFlow(DeviceListUiState(isLoading = true))
    val state: StateFlow<DeviceListUiState> = mutableState.asStateFlow()

    private val mutableEvents = MutableSharedFlow<DeviceListEvent>(extraBufferCapacity = 1)

    /** Emitted once per tap on a camera or lock row; the screen navigates, the state does not change. */
    val events: SharedFlow<DeviceListEvent> = mutableEvents.asSharedFlow()

    /** The one list job of SPEC D11 — see [start]. */
    private var listJob: Job? = null

    /** The `(origem, pagina)` currently being asked for; a response for anything else is stale. */
    private var current = ListQuery(OriginFilter.All, FIRST_PAGE)

    /** Every device on screen, in page order, and the source [DeviceListUiState.rows] is built from. */
    private var loaded: List<Device> = emptyList()

    /**
     * The highest page that actually made it into [loaded], which is what the next one counts
     * from.
     */
    private var loadedPage = 0

    /** Which filter the stored page belongs to, so the cache is only ever offered for that one. */
    private var cachedFilter: OriginFilter? = null

    /** The `origem` [loaded] was fetched under, which is not the same as the chip that is on. */
    private var loadedOrigin: OriginFilter? = null

    init {
        listJob = viewModelScope.launch {
            val remembered = listDevices.rememberedFilter()
            cachedFilter = remembered
            current = ListQuery(remembered, FIRST_PAGE)
            mutableState.update { it.copy(filter = remembered) }
            load(current, Start.Opening)
        }
    }

    /** What the retry button does (SPEC D8). */
    fun load() {
        if (listJob?.isActive == true) return
        start(Start.Retry)
    }

    /** Pull-to-refresh: page 1 again, from the top (SPEC D7). */
    fun refresh() {
        start(Start.Refresh)
    }

    /** A chip (SPEC D4): reload from page 1 with the new `origem`, and remember the choice. */
    /**
     * A chip changes what is **shown**; it asks the partner only when the app cannot answer it
     * (D4).
     */
    fun selectFilter(filter: OriginFilter) {
        val before = mutableState.value
        if (filter == before.filter) return
        mutableState.update { it.copy(filter = filter) }
        if (!canAnswerLocally(before)) {
            start(Start.Filtering, origin = filter)
            return
        }
        viewModelScope.launch { listDevices.rememberFilter(filter) }
        mutableState.update { it.copy(rows = rowsFor(filter), error = null) }
    }

    /** Whether the rows in hand can describe any chip. */
    private fun canAnswerLocally(state: DeviceListUiState): Boolean =
        loadedOrigin == OriginFilter.All &&
            !state.hasMore &&
            loaded.isNotEmpty() &&
            listJob?.isActive != true

    private fun rowsFor(filter: OriginFilter): List<DeviceRow> =
        loaded.filter { filter.accepts(it.origin) }.toRows(now(), catalog)

    /** The next page, asked for by the scroll (SPEC D2) or by the footer's retry (SPEC D8). */
    fun loadMore() {
        if (listJob?.isActive == true || !mutableState.value.hasMore) return
        start(Start.NextPage)
    }

    /** A tap on a camera row (SPEC U2). */
    fun onCameraTap(row: DeviceRow) {
        val camera = loaded.firstOrNull { it.id.value == row.id && it.kind == DeviceKind.Camera } ?: return
        viewModelScope.launch { mutableEvents.emit(DeviceListEvent.OpenLiveVideo(camera)) }
    }

    /** A tap on a lock row (SPEC U2, D6, L1). */
    fun onLockTap(row: DeviceRow) {
        val lock = loaded.firstOrNull { it.id.value == row.id && it.kind == DeviceKind.Lock } ?: return
        val addressable = addressing(lock) as? LockAddressing.Addressable ?: return
        viewModelScope.launch {
            mutableEvents.emit(DeviceListEvent.OpenLock(lock = lock, address = addressable.address))
        }
    }

    /** The only place a page request is started — the single job of SPEC D11. */
    private fun start(trigger: Start, origin: OriginFilter = mutableState.value.filter) {
        val page = if (trigger == Start.NextPage) loadedPage + 1 else FIRST_PAGE
        listJob?.cancel()
        current = ListQuery(origin, page)
        listJob = viewModelScope.launch { load(current, trigger) }
    }

    /** Asks for [query], then applies it only if it is still the page the user is looking at. */
    private suspend fun load(query: ListQuery, trigger: Start) {
        mutableState.update { it.starting(trigger) }
        if (trigger == Start.Filtering) listDevices.rememberFilter(query.origin)
        if (trigger == Start.Opening) renderCache()
        val mayUseCache = query.page == FIRST_PAGE && query.origin == cachedFilter
        val result = listDevices(origin = query.origin, page = query.page, mayUseCache = mayUseCache)
        if (query != current) return
        apply(result, query)
    }

    /** The rows already on disk, on screen before the partner has answered (SPEC U2). */
    private suspend fun renderCache() {
        val cached = listDevices.cached().takeIf { it.isNotEmpty() } ?: return
        loaded = cached
        mutableState.update { it.copy(rows = cached.toRows(now(), catalog)) }
    }

    /** Turns one page's outcome into the state (SPEC D2, D3, D8, U2). */
    private fun apply(result: DeviceListResult, query: ListQuery) {
        val isFirstPage = query.page == FIRST_PAGE
        if (isFirstPage && result !is DeviceListResult.Stale) cachedFilter = query.origin
        if (result is DeviceListResult.Loaded || result is DeviceListResult.Stale) {
            loadedOrigin = query.origin
        }
        loaded = when (result) {
            is DeviceListResult.Loaded ->
                if (isFirstPage) result.devices else (loaded + result.devices).distinctBy(Device::id)
            is DeviceListResult.Stale -> result.devices
            DeviceListResult.Empty -> if (isFirstPage) emptyList() else loaded
            else -> if (isFirstPage) emptyList() else loaded
        }
        loadedPage = when {
            loaded.isEmpty() -> 0
            result is DeviceListResult.Loaded -> query.page
            else -> loadedPage
        }
        val at = now()
        mutableState.update { it.applied(result, loaded.toRows(at, catalog), isFirstPage, at) }
    }

    /**
     * Every outcome, mapped with no `else`: a result added by a later slice does not compile
     * until someone has decided what the list does with it, which is the app-side half of SPEC
     * E2.
     */
    private fun DeviceListUiState.applied(
        result: DeviceListResult,
        rows: List<DeviceRow>,
        isFirstPage: Boolean,
        at: Instant,
    ): DeviceListUiState {
        val settled = copy(
            isLoading = false,
            isLoadingMore = false,
            isRefreshing = false,
            error = null,
            serverMessage = null,
            staleFor = null,
        )
        return when (result) {
            is DeviceListResult.Loaded -> settled.copy(rows = rows, hasMore = result.hasMore)
            DeviceListResult.Empty -> settled.copy(rows = rows, hasMore = false)
            is DeviceListResult.Stale ->
                settled.copy(rows = rows, hasMore = false, staleFor = result.fetchedAt.ageAt(at))

            DeviceListResult.TokenRejected -> settled.failed(DeviceListError.TokenRejected, isFirstPage)
            is DeviceListResult.TokenExpired ->
                settled.failed(DeviceListError.TokenExpired, isFirstPage, result.serverMessage)

            DeviceListResult.Offline -> settled.failed(DeviceListError.Offline, isFirstPage)
            DeviceListResult.DeviceNotFound -> settled.failed(DeviceListError.DeviceNotFound, isFirstPage)
            DeviceListResult.UnexpectedResponse ->
                settled.failed(DeviceListError.UnexpectedResponse, isFirstPage)

            DeviceListResult.Failed -> settled.failed(DeviceListError.Failed, isFirstPage)
        }
    }

    /** What starting a request does to the state, which depends entirely on why it started. */
    private fun DeviceListUiState.starting(trigger: Start): DeviceListUiState = when (trigger) {
        Start.NextPage -> copy(isLoadingMore = true, error = null, serverMessage = null)
        Start.Filtering -> DeviceListUiState(isLoading = true, filter = filter)
        else -> DeviceListUiState(
            isLoading = true,
            rows = rows,
            filter = filter,
            isRefreshing = trigger == Start.Refresh,
        )
    }

    /** A failure, which means two different things depending on the page. */
    private fun DeviceListUiState.failed(
        error: DeviceListError,
        isFirstPage: Boolean,
        serverMessage: String? = null,
    ) = copy(
        rows = if (isFirstPage) emptyList() else rows,
        error = error,
        serverMessage = serverMessage,
    )

    /** Why a request was started — the one thing every rule above branches on. */
    private enum class Start { Opening, Retry, Refresh, Filtering, NextPage }

    /** What is being asked for: the `(origem, pagina)` SPEC D11 compares on arrival. */
    private data class ListQuery(val origin: OriginFilter, val page: Int)

    private companion object {
        /** The partner counts pages from 1 (`docs/api-contract.md` §3). */
        const val FIRST_PAGE = 1
    }
}
