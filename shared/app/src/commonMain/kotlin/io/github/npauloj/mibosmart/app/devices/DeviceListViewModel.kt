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
     * Loads page 1 and turns the outcome into the state (SPEC D1, D3, D8 without cache).
     *
     * A second call while one is in flight is ignored: the screen shows a spinner and no retry button
     * while loading, and every request is billed to the account (ADR-006).
     */
    suspend fun onLoad() {
        if (isLoadInFlight) return
        isLoadInFlight = true
        mutableState.value = DeviceListUiState(isLoading = true)
        try {
            mutableState.value = listDevices().toState(now())
        } finally {
            isLoadInFlight = false
        }
    }
}

private fun DeviceListResult.toState(now: Instant): DeviceListUiState = when (this) {
    is DeviceListResult.Loaded -> DeviceListUiState(rows = devices.toRows(now))
    DeviceListResult.Empty -> DeviceListUiState()
    DeviceListResult.TokenRejected -> DeviceListUiState(error = DeviceListError.TokenRejected)
    is DeviceListResult.TokenExpired ->
        DeviceListUiState(error = DeviceListError.TokenExpired, serverMessage = serverMessage)

    DeviceListResult.Offline -> DeviceListUiState(error = DeviceListError.Offline)
    DeviceListResult.DeviceNotFound -> DeviceListUiState(error = DeviceListError.DeviceNotFound)
    DeviceListResult.UnexpectedResponse -> DeviceListUiState(error = DeviceListError.UnexpectedResponse)
    DeviceListResult.Failed -> DeviceListUiState(error = DeviceListError.Failed)
}
