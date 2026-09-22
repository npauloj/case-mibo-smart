package io.github.npauloj.mibosmart.app.lock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

/**
 * Everything the history tab shows, in one immutable value (ADR-003).
 *
 * Loading, list, empty and error are the four states SPEC §4 asks for; the empty one is [Entries]
 * with nothing in it rather than a subtype, because "this door has not been opened" is an answer the
 * partner gave and not a different shape of screen (SPEC L10).
 */
sealed interface OpeningHistoryUiState {

    /** `historico-abertura` is in flight — the one request this tab costs (SPEC L9, ADR-006). */
    data object Loading : OpeningHistoryUiState

    /** The partner answered, newest first. An empty [rows] is SPEC L10's "Sem aberturas registradas". */
    data class Entries(val rows: List<OpeningRow>) : OpeningHistoryUiState {

        val isEmpty: Boolean get() = rows.isEmpty()
    }

    /**
     * No list: one named cause and one action (SPEC U6).
     *
     * @property serverMessage the partner's own sentence, when the failure carried one worth showing
     *   (SPEC S3.1).
     */
    data class Failed(
        val error: LockError,
        val serverMessage: String? = null,
    ) : OpeningHistoryUiState
}

/**
 * One opening, as the screen renders it (SPEC L9, U4).
 *
 * The words are not here: [kind] and [actor] are the domain's values and the composable turns them
 * into "Abertura remota (nome)" from a Compose resource (SPEC E6), exactly like the lock screen does
 * with its states. What *is* here is everything time-dependent, because it has to be computed
 * against an injected clock to be assertable at all.
 *
 * @property age how long ago it happened, in the coarse buckets the lock screen already counts time
 *   in ([LastSeen]). `LastSeen.Never` cannot occur: an entry that exists has a time.
 * @property absoluteTime the same moment spelled out, so a row says both "há 5 min" and when that
 *   was (SPEC U4). It is local wall-clock time, as the partner sent it.
 * @property actor who opened the door, when the entry names anyone. Personal data: it lives in this
 *   state for as long as the screen is on and is never written to a log (SPEC L9, LGPD).
 */
data class OpeningRow(
    val kind: OpeningKind,
    val actor: String?,
    val age: LastSeen,
    val absoluteTime: String,
)

/**
 * The history tab: one state, one request, and no way to spend a second one by accident (ADR-003).
 *
 * The guard is [current]: entering the tab reads the history once per lock, and coming back to it —
 * a tab switch, a recomposition, a rotation — finds the address unchanged and asks the partner
 * nothing (SPEC L9, ADR-006). Only [onRetry] reads again, and only a tap reaches it.
 *
 * @param clock where "há 5 min" is measured from, so the rule is asserted with a fixed instant
 *   instead of the machine running the tests (SPEC U4).
 * @param timeZone which zone turns the partner's zone-less `tempoLocal` into an age. It is the
 *   device's, which is what "local" means here — and a parameter for the same reason as [clock].
 */
class OpeningHistoryViewModel(
    private val openingHistory: OpeningHistory,
    private val clock: Clock,
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

    /**
     * The result as the one value the tab renders.
     *
     * `now` is read once per load rather than per frame: a list that renumbered itself while the
     * user reads it would be worse than one that is a minute stale.
     */
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
    )
}

/**
 * `18/09/2026 10:27` — the moment as a Brazilian reader writes it (SPEC U4).
 *
 * Spelled out by hand rather than through a locale: the app ships one language (SPEC E6), and the
 * one thing this must not do is reorder day and month, which is exactly what a machine locale on an
 * en-US device would do to `09/18`. Seconds are dropped — the row already answers "when" twice.
 */
private fun LocalDateTime.asAbsoluteText(): String =
    "${day.padded()}/${month.number.padded()}/$year ${hour.padded()}:${minute.padded()}"

private fun Int.padded(): String = toString().padStart(2, '0')
