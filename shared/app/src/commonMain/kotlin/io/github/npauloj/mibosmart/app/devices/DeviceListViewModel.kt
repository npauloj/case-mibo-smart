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
     * The last request that failed, and two different screens depending on what is under it: with
     * no [rows] it *is* the screen (SPEC D8); with rows on it, a next page failed and it is a line
     * at the end of a list that still works (SPEC D2).
     */
    val error: DeviceListError? = null,
    /** The partner's own sentence, set only by [DeviceListError.TokenExpired] (SPEC S3.1, U6). */
    val serverMessage: String? = null,
    /**
     * How old [rows] are, set only when they came from the cache because the request failed
     * (SPEC D8). While a load is running the cached rows are shown *without* it: the app is not
     * offline until the call says so.
     */
    val staleFor: Elapsed? = null,
    /** Which chip is on, and which `origem` the rows were fetched with (SPEC D4). */
    val filter: OriginFilter = OriginFilter.All,
    /** A page past the first is in flight: the footer spinner, never the whole-screen one (SPEC D2). */
    val isLoadingMore: Boolean = false,
    /**
     * The last page came back full, so another one may exist (SPEC D2).
     *
     * `false` is the end of the list and the end of the requests: nothing in the UI may ask again,
     * which is what keeps an endless scroll from eating the account's budget (ADR-006).
     */
    val hasMore: Boolean = false,
    /** The user pulled the list down (SPEC D7) — the same reload, drawn as the pull indicator. */
    val isRefreshing: Boolean = false,
) {
    /** Page 1 answered, and it had nothing in it — the empty state, not an error (SPEC D3). */
    val isEmpty: Boolean get() = !isLoading && error == null && rows.isEmpty()
}

/**
 * The reasons the list can fail, one user-facing message each (SPEC E2, U6).
 *
 * [TokenRejected] and [TokenExpired] are here because the screen must render *something* today; SPEC
 * D9 hands them to the session guard, and S-02b routes on them instead of drawing them.
 */
enum class DeviceListError { TokenRejected, TokenExpired, Offline, DeviceNotFound, UnexpectedResponse, Failed }

/**
 * What the list asks the navigator for, once each — a one-shot event, not state (ADR-003).
 *
 * It carries the whole [Device] and not an id because the live screen needs the camera's `ns` *and*
 * its status to decide SPEC V7 without spending a request, and the list already has both.
 */
sealed interface DeviceListEvent {

    /** SPEC U2: a camera row opens the live screen directly — no intermediate screen or dialog. */
    data class OpenLiveVideo(val camera: Device) : DeviceListEvent

    /**
     * SPEC U2 and D6: a lock row opens the lock screen the same way, with the lock already addressed.
     *
     * The [address] travels with the event because it is assembled from the row *this* list holds
     * (`docs/api-contract.md` §3, §5): the lock screen never received that row, and asking the
     * partner for it again would spend a request on a fact already on screen (ADR-006). The two are kept
     * apart rather than packed into the lock feature's own destination type — `app.devices` must not
     * import `app.lock` (rule 3), and the navigator that knows both is what joins them.
     */
    data class OpenLock(val lock: Device, val address: LockAddress) : DeviceListEvent
}

/**
 * The device list: one state, intents as functions, one request in flight (ADR-003, SPEC D11).
 *
 * **Every** partner call this screen makes goes through [start], which cancels whatever was running
 * and records the `(origem, pagina)` being asked for. That single job is what SPEC D11 buys: a chip
 * tapped twice, a pull during a load, or a scroll at the bottom of a page cannot each leave a
 * request racing the others, and a response that arrives for a query nobody is looking at any more
 * is dropped instead of overwriting the list. The account pays for every request (ADR-006), so
 * "ignored" here always means "not sent", never "sent and discarded".
 *
 * Entering the list costs exactly one request (SPEC D1): the load starts from `init`, never from the
 * composition — a `LaunchedEffect` would fire again on every recomposition key change and a
 * configuration change would pay for a second page 1. Coming back to the list reuses this ViewModel
 * and therefore makes no call at all (SPEC D7).
 *
 * @param catalog the partner's words for its own model codes (SPEC D5, ADR-007). It is read off a
 *   table already in memory, so naming every row on a page costs **no** partner request (ADR-006).
 *   It defaults to [RawCodes] — the codes as they came — which is what iOS runs; Android's Koin graph
 *   hands over the Java-backed one.
 * @param now where "visto pela última vez há X" is measured from. It is read once per load rather
 *   than per frame: a list that re-renders must not renumber itself under the user's eyes.
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
     * The highest page that actually made it into [loaded], which is what the next one counts from.
     *
     * Not [current]`.page`: that one moves when a request *starts*, so counting from it would skip
     * a page whose request failed — the user would retry from the footer and silently lose the
     * devices in between.
     */
    private var loadedPage = 0

    /**
     * Which filter the stored page belongs to, so the cache is only ever offered for that one.
     *
     * It starts as the remembered chip — the one whose page 1 the last session cached — and moves
     * whenever a page 1 succeeds, because that is exactly when the repository rewrites the file.
     * Showing a cached "Todos" page under "Compartilhados" would be a lie the user cannot detect.
     */
    private var cachedFilter: OriginFilter? = null

    init {
        listJob = viewModelScope.launch {
            // SPEC D4: the list opens on the chip the user left it on, read before anything is asked
            // for — it decides the `origem` of the very first request, so it cannot come later.
            val remembered = listDevices.rememberedFilter()
            cachedFilter = remembered
            current = ListQuery(remembered, FIRST_PAGE)
            mutableState.update { it.copy(filter = remembered) }
            load(current, Start.Opening)
        }
    }

    /**
     * What the retry button does (SPEC D8).
     *
     * Ignored while a request is in flight: the account pays per request (ADR-006) and the answer
     * the user is waiting for is already on its way.
     */
    fun load() {
        if (listJob?.isActive == true) return
        start(Start.Retry)
    }

    /**
     * Pull-to-refresh: page 1 again, from the top (SPEC D7).
     *
     * Unlike [load] it *cancels* what is in flight (SPEC D11) — the user asked for fresh rows, and
     * letting the older request finish would let it answer over them.
     */
    fun refresh() {
        start(Start.Refresh)
    }

    /**
     * A chip (SPEC D4): reload from page 1 with the new `origem`, and remember the choice.
     *
     * Tapping the chip that is already on does nothing — repeating the request would spend one of
     * the account's for a list it is already showing.
     */
    fun selectFilter(filter: OriginFilter) {
        if (filter == mutableState.value.filter) return
        mutableState.update { it.copy(filter = filter) }
        start(Start.Filtering, origin = filter)
    }

    /**
     * The next page, asked for by the scroll (SPEC D2) or by the footer's retry (SPEC D8).
     *
     * Two guards, both about the budget: nothing is asked for while a request is in flight — which
     * is how a fast scroll past the end of a page produces one request and not five — and nothing is
     * asked for once the list has ended.
     */
    fun loadMore() {
        if (listJob?.isActive == true || !mutableState.value.hasMore) return
        start(Start.NextPage)
    }

    /**
     * A tap on a camera row (SPEC U2).
     *
     * A row the list cannot match to a loaded camera — a lock, or a row from a page that has since
     * been replaced — is ignored rather than guessed at: the live screen needs the real device.
     */
    fun onCameraTap(row: DeviceRow) {
        val camera = loaded.firstOrNull { it.id.value == row.id && it.kind == DeviceKind.Camera } ?: return
        viewModelScope.launch { mutableEvents.emit(DeviceListEvent.OpenLiveVideo(camera)) }
    }

    /**
     * A tap on a lock row (SPEC U2, D6, L1).
     *
     * The address is assembled here, from the lock's own row and nothing else, so opening a lock
     * costs zero partner requests (ADR-006) and works whether or not the hub is on the loaded page —
     * the partner sends the hub's `ns` and `idProduto` on the sub-device itself
     * (`docs/api-contract.md` §3). When that row does not carry all four parts, **no event is
     * emitted**: the row already says so and stays untappable, because an address guessed from three
     * of four parts would command a different device (§5).
     */
    fun onLockTap(row: DeviceRow) {
        val lock = loaded.firstOrNull { it.id.value == row.id && it.kind == DeviceKind.Lock } ?: return
        val addressable = addressing(lock) as? LockAddressing.Addressable ?: return
        viewModelScope.launch {
            mutableEvents.emit(DeviceListEvent.OpenLock(lock = lock, address = addressable.address))
        }
    }

    /**
     * The only place a page request is started — the single job of SPEC D11.
     *
     * Cancelling first is what makes a filter change or a refresh during a load safe; [current] is
     * moved before the new job starts so that anything the cancelled one still manages to answer is
     * recognised as stale in [load].
     */
    private fun start(trigger: Start, origin: OriginFilter = mutableState.value.filter) {
        val page = if (trigger == Start.NextPage) loadedPage + 1 else FIRST_PAGE
        listJob?.cancel()
        current = ListQuery(origin, page)
        listJob = viewModelScope.launch { load(current, trigger) }
    }

    /** Asks for [query], then applies it only if it is still the page the user is looking at. */
    private suspend fun load(query: ListQuery, trigger: Start) {
        mutableState.update { it.starting(trigger) }
        // SPEC D4: the chip is recorded as part of the load it caused, and through the same single
        // job — two chips tapped in a row therefore leave the *last* one on disk, not whichever
        // write happened to finish second.
        if (trigger == Start.Filtering) listDevices.rememberFilter(query.origin)
        if (trigger == Start.Opening) renderCache()
        // SPEC D8: the stored page is page 1 of one filter, so it can only answer for that query —
        // never for a page 2, and never for a chip it was not fetched under.
        val mayUseCache = query.page == FIRST_PAGE && query.origin == cachedFilter
        val result = listDevices(origin = query.origin, page = query.page, mayUseCache = mayUseCache)
        // SPEC D11: cancellation is cooperative, so a job cancelled *while* the partner was
        // answering can still arrive here with a result in hand. The query it was asked for is what
        // decides, not the job: a late page from the previous filter never reaches the list.
        if (query != current) return
        apply(result, query)
    }

    /**
     * The rows already on disk, on screen before the partner has answered (SPEC U2).
     *
     * Still `isLoading`: these rows are what we knew, not yet what the partner says. They are also
     * put into [loaded], so what the screen shows and what the ViewModel believes never diverge.
     */
    private suspend fun renderCache() {
        val cached = listDevices.cached().takeIf { it.isNotEmpty() } ?: return
        loaded = cached
        mutableState.update { it.copy(rows = cached.toRows(now(), catalog)) }
    }

    /**
     * Turns one page's outcome into the state (SPEC D2, D3, D8, U2).
     *
     * [now] is read once here rather than per row, so every elapsed time on the screen is measured
     * from the same instant and a re-render cannot renumber the list under the user's eyes.
     */
    private fun apply(result: DeviceListResult, query: ListQuery) {
        val isFirstPage = query.page == FIRST_PAGE
        // Page 1 came back at all, so the repository has just rewritten the cache with this filter's
        // rows — except when the answer *was* the cache (Stale), which changes nothing.
        if (isFirstPage && result !is DeviceListResult.Stale) cachedFilter = query.origin
        loaded = when (result) {
            // Page 1 replaces the list; a later page is appended in the order it arrived. The pages
            // are not re-sorted together: rows the user has already read must not move under them.
            //
            // `distinctBy` is not tidiness: pagination here is blind (`docs/api-contract.md` §3), so
            // a device that moves between pages while the user scrolls comes back twice — and two
            // rows with one id is a duplicate key, which a `LazyColumn` answers with a crash.
            is DeviceListResult.Loaded ->
                if (isFirstPage) result.devices else (loaded + result.devices).distinctBy(Device::id)
            is DeviceListResult.Stale -> result.devices
            // Page 1: nothing to show (SPEC D3). Past it: the page before was the last one, and the
            // rows stay exactly as they are (SPEC D2).
            DeviceListResult.Empty -> if (isFirstPage) emptyList() else loaded
            // Page 1 failing takes the list away (see `failed`), so there is nothing loaded any
            // more; a next page failing leaves every row that is already there.
            else -> if (isFirstPage) emptyList() else loaded
        }
        // The counter follows the rows: a page that arrived moves it on, a page that failed must be
        // asked for again rather than skipped, and a list that was taken away starts from nothing.
        loadedPage = when {
            loaded.isEmpty() -> 0
            result is DeviceListResult.Loaded -> query.page
            else -> loadedPage
        }
        val at = now()
        mutableState.update { it.applied(result, loaded.toRows(at, catalog), isFirstPage, at) }
    }

    /**
     * Every outcome, mapped with no `else`: a result added by a later slice does not compile until
     * someone has decided what the list does with it, which is the app-side half of SPEC E2.
     */
    private fun DeviceListUiState.applied(
        result: DeviceListResult,
        rows: List<DeviceRow>,
        isFirstPage: Boolean,
        at: Instant,
    ): DeviceListUiState {
        // Whatever the outcome, the request is over: nothing is spinning, and nothing is stale
        // unless this result says so.
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
        // The footer spins; the rows above it do not move.
        Start.NextPage -> copy(isLoadingMore = true, error = null, serverMessage = null)
        // A new chip: the rows on screen belong to the old one, so they go with it (SPEC D4).
        Start.Filtering -> DeviceListUiState(isLoading = true, filter = filter)
        // Retry, refresh and the first load all keep whatever is readable while they run. Blanking a
        // list to show a spinner is the flash SPEC U2 is about, and the "sem conexão" banner goes
        // because the app is trying again — it is not offline right now (SPEC D8).
        else -> DeviceListUiState(
            isLoading = true,
            rows = rows,
            filter = filter,
            isRefreshing = trigger == Start.Refresh,
        )
    }

    /**
     * A failure, which means two different things depending on the page.
     *
     * Page 1 has nothing to stand on, so the error *is* the screen. A next page that failed leaves a
     * perfectly good list behind it, and taking that away to show a message would punish the user
     * for scrolling — so the rows stay, [DeviceListUiState.hasMore] stays, and the footer carries
     * the message and the retry. Nothing asks again on its own: only that button does.
     */
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
