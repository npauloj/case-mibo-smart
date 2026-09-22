package io.github.npauloj.mibosmart.app.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.npauloj.mibosmart.app.resources.Res
import io.github.npauloj.mibosmart.app.resources.lock_back
import io.github.npauloj.mibosmart.app.resources.lock_history_days
import io.github.npauloj.mibosmart.app.resources.lock_history_empty
import io.github.npauloj.mibosmart.app.resources.lock_history_hours
import io.github.npauloj.mibosmart.app.resources.lock_history_loading
import io.github.npauloj.mibosmart.app.resources.lock_history_local
import io.github.npauloj.mibosmart.app.resources.lock_history_minutes
import io.github.npauloj.mibosmart.app.resources.lock_history_moments
import io.github.npauloj.mibosmart.app.resources.lock_history_remote
import io.github.npauloj.mibosmart.app.resources.lock_history_remote_named
import io.github.npauloj.mibosmart.app.resources.lock_history_title
import io.github.npauloj.mibosmart.app.resources.lock_history_unknown
import io.github.npauloj.mibosmart.app.resources.lock_history_when
import io.github.npauloj.mibosmart.app.resources.lock_retry
import io.github.npauloj.mibosmart.domain.lock.LockAddress
import io.github.npauloj.mibosmart.domain.lock.OpeningKind
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * The lock's opening history (SPEC L9, L10, U4): one `historico-abertura`, newest first, each entry
 * saying how it was opened, by whom when anyone is named, and when — twice.
 *
 * It is read-only. Nothing on this tab reaches the door, which is why it has no kill switch and no
 * confirmation state: the worst a bug here can do is describe an opening badly.
 */
@Composable
fun OpeningHistoryScreen(
    address: LockAddress,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OpeningHistoryViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // On the ViewModel's scope, not the composition's: leaving the tab must not cancel a request the
    // account has already paid for, and returning to it must not pay again (ADR-003, ADR-006). The
    // once-per-lock guard is the ViewModel's.
    LaunchedEffect(address) { viewModel.open(address) }

    OpeningHistoryContent(
        state = state,
        onRetry = viewModel::retry,
        onBack = onBack,
        modifier = modifier,
    )
}

/** The tab as a pure function of its state, so every state has a preview and no ViewModel. */
@Composable
fun OpeningHistoryContent(
    state: OpeningHistoryUiState,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TextButton(onClick = onBack) { Text(stringResource(Res.string.lock_back)) }
        Text(text = stringResource(Res.string.lock_history_title), style = MaterialTheme.typography.headlineSmall)

        when (state) {
            OpeningHistoryUiState.Loading -> LoadingRow(Res.string.lock_history_loading)
            // SPEC L10: a door nobody has opened is an answer, so it gets a sentence rather than the
            // blank space an empty list would leave.
            is OpeningHistoryUiState.Entries ->
                if (state.isEmpty) EmptyNotice() else OpeningList(state.rows)

            is OpeningHistoryUiState.Failed -> ErrorSection(state, onRetry)
        }
    }
}

@Composable
private fun EmptyNotice() {
    Text(text = stringResource(Res.string.lock_history_empty), style = MaterialTheme.typography.bodyMedium)
}

/**
 * The openings, newest first — the order the use case settled, not one this list re-derives.
 *
 * Lazy because 50 entries is the whole answer and the endpoint has no second page (SPEC L9): there
 * is nothing at the bottom of this list that could ask the partner for more, by design (ADR-006).
 */
@Composable
private fun OpeningList(rows: List<OpeningRow>) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(rows) { row -> OpeningEntry(row) }
    }
}

/**
 * One opening: what it was, and when — in both of the ways SPEC U4 asks for.
 *
 * "há 5 min" alone is unanchored and an absolute timestamp alone has to be subtracted in the
 * reader's head, so the row says both and neither is a footnote.
 */
@Composable
private fun OpeningEntry(row: OpeningRow) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(text = row.label(), style = MaterialTheme.typography.titleSmall)
        Text(
            text = stringResource(Res.string.lock_history_when, row.age.asRelativeText(), row.absoluteTime),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** One named cause and one action, never a status code or the server's raw words (SPEC U6). */
@Composable
private fun ErrorSection(state: OpeningHistoryUiState.Failed, onRetry: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // The server's sentence wins when there is one (SPEC S3.1) — the same rule, and the same
        // five categories, as the lock tab beside this one.
        Text(
            text = state.serverMessage ?: stringResource(state.error.message),
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(onClick = onRetry) { Text(stringResource(Res.string.lock_retry)) }
    }
}

/**
 * How the door was opened, in the user's words (SPEC L9, U4).
 *
 * Every branch produces something: a remote opening with no name still reads "Abertura remota", and
 * a `tipo` the app has never seen is shown as the partner's catalogue calls it, or as the partner
 * wrote it. A row is never blank and is never dropped — an opening nobody can explain is exactly
 * the one worth seeing.
 */
@Composable
private fun OpeningRow.label(): String = when (kind) {
    OpeningKind.Remote -> actor
        ?.let { stringResource(Res.string.lock_history_remote_named, it) }
        ?: stringResource(Res.string.lock_history_remote)

    OpeningKind.Local -> stringResource(Res.string.lock_history_local)
    // The catalogue's words when the partner's legacy SDK has them (ADR-007) — on iOS, and for a
    // `tipo` nobody catalogued, this is the raw word. An empty one leaves only the one honest thing
    // left to say: that the door was opened.
    is OpeningKind.Unknown ->
        (catalogLabel ?: kind.type).ifBlank { stringResource(Res.string.lock_history_unknown) }
}

/**
 * "há 5 min" — the same buckets the lock tab counts "última atualização há X" in, other words.
 *
 * [LastSeen.Never] cannot reach here: an entry that exists happened at a time. It shares the
 * sub-minute branch because "há instantes" is the only sentence that would still be true if it did.
 */
@Composable
private fun LastSeen.asRelativeText(): String = when (this) {
    LastSeen.Never, LastSeen.Moments -> stringResource(Res.string.lock_history_moments)
    is LastSeen.Minutes -> stringResource(Res.string.lock_history_minutes, value.toString())
    is LastSeen.Hours -> stringResource(Res.string.lock_history_hours, value.toString())
    is LastSeen.Days -> stringResource(Res.string.lock_history_days, value.toString())
}
