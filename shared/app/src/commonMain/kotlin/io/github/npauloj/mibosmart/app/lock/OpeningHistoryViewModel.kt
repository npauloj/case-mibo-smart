package io.github.npauloj.mibosmart.app.lock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.npauloj.mibosmart.domain.device.ModelCatalog
import io.github.npauloj.mibosmart.domain.device.RawCodes
import io.github.npauloj.mibosmart.domain.lock.LockAddress
import io.github.npauloj.mibosmart.domain.lock.OpeningEvent
import io.github.npauloj.mibosmart.domain.lock.OpeningKind
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toInstant

/** Everything the history tab shows, in one immutable value (ADR-003). */
sealed interface OpeningHistoryUiState {

    /** `historico-abertura` is in flight — the one request this tab costs (SPEC L9, ADR-006). */
    data object Loading : OpeningHistoryUiState

    /** The partner answered, newest first. An empty [rows] is SPEC L10's "Sem aberturas registradas". */
    data class Entries(val rows: List<OpeningRow>) : OpeningHistoryUiState {

        val isEmpty: Boolean get() = rows.isEmpty()
    }

    /**
     * No list: one named cause and one action (SPEC U6).
     * @property serverMessage the partner's own sentence, when the failure carried one worth
     * showing (SPEC S3.1).
     */
    data class Failed(
        val error: LockError,
        val serverMessage: String? = null,
    ) : OpeningHistoryUiState
}

/**
 * One opening, as the screen renders it (SPEC L9, U4).
 * @property age how long ago it happened, in the coarse buckets the lock screen already counts
 * time in ([LastSeen]).
 * @property absoluteTime the same moment spelled out, so a row says both "há 5 min" and when
 * that was (SPEC U4).
 * @property actor who opened the door, when the entry names anyone.
 * @property catalogLabel what the partner's catalogue calls an opening the app has no word of
 * its own for, or the raw `tipo` when it has no entry either (ADR-007).
 */
data class OpeningRow(
    val kind: OpeningKind,
    val actor: String?,
    val age: LastSeen,
    val absoluteTime: String,
    val catalogLabel: String? = null,
)

/**
 * The history tab: one state, one request, and no way to spend a second one by accident
 * (ADR-003).
 * @param clock where "há 5 min" is measured from, so the rule is asserted with a fixed instant
 * instead of the machine running the tests (SPEC U4).
 * @param catalog the partner's words for an opening type this app does not model (ADR-007).
 * @param timeZone which zone turns the partner's zone-less `tempoLocal` into an age.
 */
class OpeningHistoryViewModel(
    private val openingHistory: OpeningHistory,
    private val clock: Clock,
    private val catalog: ModelCatalog = RawCodes,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {

    private val mutableState = MutableStateFlow<OpeningHistoryUiState>(OpeningHistoryUiState.Loading)
    val state: StateFlow<OpeningHistoryUiState> = mutableState.asStateFlow()

    private var current: LockAddress? = null

    /** Screens call this; [onOpen] is the same intent as a suspend function, so a test can await it. */
    fun open(address: LockAddress) {
        viewModelScope.launch { onOpen(address) }
    }

    /** Reads the history of [address], at most once per lock. */
    suspend fun onOpen(address: LockAddress) {
        if (current == address) return
        current = address
        read(address)
    }

    fun retry() {
        viewModelScope.launch { onRetry() }
    }

    /** The one action the error state offers (SPEC U6); it is the user's choice, never a timer (E5). */
    suspend fun onRetry() {
        read(current ?: return)
    }

    private suspend fun read(address: LockAddress) {
        mutableState.value = OpeningHistoryUiState.Loading
        mutableState.value = openingHistory(address).toUiState(clock.now())
    }

    /** The result as the one value the tab renders. */
    private fun OpeningHistoryResult.toUiState(now: Instant): OpeningHistoryUiState = when (this) {
        is OpeningHistoryResult.Loaded -> OpeningHistoryUiState.Entries(events.map { it.toRow(now) })
        OpeningHistoryResult.TokenRejected -> OpeningHistoryUiState.Failed(LockError.TokenRejected)
        is OpeningHistoryResult.TokenExpired ->
            OpeningHistoryUiState.Failed(LockError.TokenExpired, serverMessage)

        OpeningHistoryResult.Offline -> OpeningHistoryUiState.Failed(LockError.Offline)
        OpeningHistoryResult.UnexpectedResponse -> OpeningHistoryUiState.Failed(LockError.UnexpectedResponse)
        OpeningHistoryResult.Failed -> OpeningHistoryUiState.Failed(LockError.Failed)
    }

    private fun OpeningEvent.toRow(now: Instant): OpeningRow = OpeningRow(
        kind = kind,
        actor = actor,
        age = LockUiMapper.elapsedSince(at.toInstant(timeZone), now),
        absoluteTime = at.asAbsoluteText(),
        catalogLabel = (kind as? OpeningKind.Unknown)?.let { catalog.label(it.type) },
    )
}

/** `18/09/2026 10:27` — the moment as a Brazilian reader writes it (SPEC U4). */
private fun LocalDateTime.asAbsoluteText(): String =
    "${day.padded()}/${month.number.padded()}/$year ${hour.padded()}:${minute.padded()}"

private fun Int.padded(): String = toString().padStart(2, '0')
