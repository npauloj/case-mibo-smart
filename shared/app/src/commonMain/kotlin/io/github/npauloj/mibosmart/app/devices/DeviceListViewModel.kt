package io.github.npauloj.mibosmart.app.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.launch

/** Everything the device list shows, in one immutable value (ADR-003). */
data class DeviceListUiState(
    val isLoading: Boolean = false,
    val rows: List<DeviceRow> = emptyList(),
    val error: DeviceListError? = null,
    /** The partner's own sentence, set only by [DeviceListError.TokenExpired] (SPEC S3.1, U6). */
    val serverMessage: String? = null,
    /**
     * How old [rows] are, set only when they came from the cache because the request failed
     * (SPEC D8). While a load is running the cached rows are shown *without* it: the app is not
     * offline until the call says so.
     */
    val staleFor: Elapsed? = null,
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
 * The device list: one state, intents as suspend functions (ADR-003).
 *
 * Entering the list costs exactly one request (SPEC D1, ADR-006), so the load is started once, from
 * `init`, and never from the composition — a `LaunchedEffect` would fire again on every
 * recomposition key change and a configuration change would pay for a second page 1. Coming back to
 * the list reuses this ViewModel and therefore makes no call at all.
 *
 * @param now where "visto pela última vez há X" is measured from. It is read once per load rather
 *   than per frame: a list that re-renders must not renumber itself under the user's eyes.
 */
class DeviceListViewModel(
    private val listDevices: ListDevices,
    private val now: () -> Instant = { Clock.System.now() },
) : ViewModel() {

    private val mutableState = MutableStateFlow(DeviceListUiState(isLoading = true))
    val state: StateFlow<DeviceListUiState> = mutableState.asStateFlow()

    private var isLoadInFlight = false

    init {
        load()
    }

    /** What the retry button does; also the first load. Screens call this, tests await [onLoad]. */
    fun load() {
        viewModelScope.launch { onLoad() }
    }

    /**
     * Loads page 1 and turns the outcome into the state (SPEC D1, D3, D8, U2).
     *
     * The cache goes on screen first and costs no request (ADR-006): on a cold start the list is
     * already readable while page 1 is in flight, which is the whole of SPEC U2. A second call while
     * one is in flight is ignored — every request is billed to the account.
     */
    suspend fun onLoad() {
        if (isLoadInFlight) return
        isLoadInFlight = true
        // Rows already on screen stay there while the request runs; the stale banner does not, because
        // the app is trying again. Blanking a readable list to show a spinner is the flash SPEC U2 is
        // about, and a retry from the stale state would do it on every tap.
        mutableState.value = DeviceListUiState(isLoading = true, rows = mutableState.value.rows)
        try {
            // Still `isLoading`: these rows are what we knew, not yet what the partner says.
            listDevices.cached()
                .takeIf { it.isNotEmpty() }
                ?.let { mutableState.value = DeviceListUiState(isLoading = true, rows = it.toRows(now())) }
            mutableState.value = listDevices().toState(now())
        } finally {
            isLoadInFlight = false
        }
    }
}

private fun DeviceListResult.toState(now: Instant): DeviceListUiState = when (this) {
    is DeviceListResult.Loaded -> DeviceListUiState(rows = devices.toRows(now))
    is DeviceListResult.Stale ->
        DeviceListUiState(rows = devices.toRows(now), staleFor = fetchedAt.ageAt(now))

    DeviceListResult.Empty -> DeviceListUiState()
    DeviceListResult.TokenRejected -> DeviceListUiState(error = DeviceListError.TokenRejected)
    is DeviceListResult.TokenExpired ->
        DeviceListUiState(error = DeviceListError.TokenExpired, serverMessage = serverMessage)

    DeviceListResult.Offline -> DeviceListUiState(error = DeviceListError.Offline)
    DeviceListResult.DeviceNotFound -> DeviceListUiState(error = DeviceListError.DeviceNotFound)
    DeviceListResult.UnexpectedResponse -> DeviceListUiState(error = DeviceListError.UnexpectedResponse)
    DeviceListResult.Failed -> DeviceListUiState(error = DeviceListError.Failed)
}
