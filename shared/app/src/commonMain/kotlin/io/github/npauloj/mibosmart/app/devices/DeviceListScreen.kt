package io.github.npauloj.mibosmart.app.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.npauloj.mibosmart.app.resources.Res
import io.github.npauloj.mibosmart.app.resources.device_empty
import io.github.npauloj.mibosmart.app.resources.device_error_device_not_found
import io.github.npauloj.mibosmart.app.resources.device_error_expired
import io.github.npauloj.mibosmart.app.resources.device_error_failed
import io.github.npauloj.mibosmart.app.resources.device_error_offline
import io.github.npauloj.mibosmart.app.resources.device_error_rejected
import io.github.npauloj.mibosmart.app.resources.device_error_unexpected
import io.github.npauloj.mibosmart.app.resources.device_filter_all
import io.github.npauloj.mibosmart.app.resources.device_filter_linked
import io.github.npauloj.mibosmart.app.resources.device_filter_shared
import io.github.npauloj.mibosmart.app.resources.device_kind_camera
import io.github.npauloj.mibosmart.app.resources.device_kind_hub
import io.github.npauloj.mibosmart.app.resources.device_kind_lock
import io.github.npauloj.mibosmart.app.resources.device_kind_other
import io.github.npauloj.mibosmart.app.resources.device_last_seen_days
import io.github.npauloj.mibosmart.app.resources.device_last_seen_hours
import io.github.npauloj.mibosmart.app.resources.device_last_seen_minutes
import io.github.npauloj.mibosmart.app.resources.device_last_seen_never
import io.github.npauloj.mibosmart.app.resources.device_list_loading
import io.github.npauloj.mibosmart.app.resources.device_list_title
import io.github.npauloj.mibosmart.app.resources.device_origin_linked
import io.github.npauloj.mibosmart.app.resources.device_origin_shared
import io.github.npauloj.mibosmart.app.resources.device_retry
import io.github.npauloj.mibosmart.app.resources.device_status_offline
import io.github.npauloj.mibosmart.app.resources.device_stale_days
import io.github.npauloj.mibosmart.app.resources.device_stale_hours
import io.github.npauloj.mibosmart.app.resources.device_stale_minutes
import io.github.npauloj.mibosmart.app.resources.device_status_online
import io.github.npauloj.mibosmart.domain.device.DeviceKind
import io.github.npauloj.mibosmart.domain.device.DeviceOrigin
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** The hub of the app: every other feature is reached from a row here (SPEC D1–D6, U3, U8). */
@Composable
fun DeviceListScreen(
    modifier: Modifier = Modifier,
    viewModel: DeviceListViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    DeviceListScreenContent(state = state, onRetry = viewModel::load, modifier = modifier)
}

/** The screen as a pure function of its state, so every state has a preview and no ViewModel. */
@Composable
fun DeviceListScreenContent(
    state: DeviceListUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 24.dp)) {
        Text(
            text = stringResource(Res.string.device_list_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        // SPEC U8: the chips are part of the frame, not of the content — they stay put while the list
        // below swaps between loading, empty and error. Choosing one is D-02's criterion.
        OriginFilterChips()

        when {
            // Rows win over every other state: a cache on screen while page 1 is in flight is SPEC
            // U2, and a cache on screen after it failed is SPEC D8 — both beat a spinner or an error.
            state.rows.isNotEmpty() -> Column {
                state.staleFor?.let { StaleBanner(staleFor = it, onRetry = onRetry) }
                DeviceRows(state.rows)
            }

            state.isLoading -> CenteredMessage { LoadingIndicator() }
            state.error != null -> CenteredMessage {
                ErrorState(error = state.error, serverMessage = state.serverMessage, onRetry = onRetry)
            }

            // Not loading, no error, no rows: page 1 answered with nothing (SPEC D3, `state.isEmpty`).
            else -> CenteredMessage { Text(stringResource(Res.string.device_empty)) }
        }
    }
}

/**
 * "Sem conexão — última atualização há N min", above rows the app could not refresh (SPEC D8).
 *
 * It sits inside the list frame rather than replacing it, and carries the retry: the user can read
 * what is there *and* ask again, which is exactly what the error state cannot offer.
 */
@Composable
private fun StaleBanner(staleFor: Elapsed, onRetry: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(staleFor.unit.staleTemplate, staleFor.amount.toString()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f, fill = false),
        )
        TextButton(onClick = onRetry) { Text(stringResource(Res.string.device_retry)) }
    }
}

/**
 * "Todos / Vinculados / Compartilhados", visible in every state (SPEC U8).
 *
 * They are inert on purpose: this slice always requests `origem: todos`, and a chip that looked
 * selectable but silently did nothing would be worse than one that is plainly not ready. D-02 gives
 * them behaviour and persistence (SPEC D4).
 */
@Composable
private fun OriginFilterChips() {
    val labels = listOf(
        Res.string.device_filter_all,
        Res.string.device_filter_linked,
        Res.string.device_filter_shared,
    )

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
        labels.forEachIndexed { index, label ->
            FilterChip(
                selected = index == 0,
                onClick = {},
                enabled = false,
                label = { Text(stringResource(label)) },
            )
        }
    }
}

@Composable
private fun DeviceRows(rows: List<DeviceRow>) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(rows, key = DeviceRow::id) { row ->
            DeviceRowItem(row)
            HorizontalDivider()
        }
    }
}

@Composable
private fun DeviceRowItem(row: DeviceRow) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(
            text = row.name,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "${stringResource(row.kind.label)} · ${row.model}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(
                    if (row.isOnline) Res.string.device_status_online else Res.string.device_status_offline,
                ),
                style = MaterialTheme.typography.labelMedium,
                color = if (row.isOnline) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Text(text = stringResource(row.origin.label), style = MaterialTheme.typography.labelMedium)
            row.lastSeen?.let { Text(text = it.text(), style = MaterialTheme.typography.labelMedium) }
        }
        // SPEC D6: a sub-device is only addressable through its hub, so the row says which one.
        row.parentName?.let {
            Text(text = it, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ErrorState(
    error: DeviceListError,
    serverMessage: String?,
    onRetry: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // The server's sentence wins only where it is fit to show — an expired token (SPEC U6, S3.1).
        Text(text = serverMessage ?: stringResource(error.message))
        Button(onClick = onRetry) { Text(stringResource(Res.string.device_retry)) }
    }
}

@Composable
private fun LoadingIndicator() {
    val loading = stringResource(Res.string.device_list_loading)

    CircularProgressIndicator(modifier = Modifier.semantics { contentDescription = loading })
}

/** Loading, empty and error all sit in the same place under the chips, so the frame never jumps. */
@Composable
private fun CenteredMessage(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) { content() }
}

/** "visto pela última vez há X", or "nunca visto online" when the partner never saw it (SPEC U3). */
@Composable
private fun LastSeen.text(): String = when (this) {
    LastSeen.Never -> stringResource(Res.string.device_last_seen_never)
    is LastSeen.Ago -> stringResource(unit.template, amount.toString())
}

private val ElapsedUnit.template: StringResource
    get() = when (this) {
        ElapsedUnit.Minutes -> Res.string.device_last_seen_minutes
        ElapsedUnit.Hours -> Res.string.device_last_seen_hours
        ElapsedUnit.Days -> Res.string.device_last_seen_days
    }

/** "Sem conexão — última atualização há N …" (SPEC D8), in the unit the age rounded to. */
private val ElapsedUnit.staleTemplate: StringResource
    get() = when (this) {
        ElapsedUnit.Minutes -> Res.string.device_stale_minutes
        ElapsedUnit.Hours -> Res.string.device_stale_hours
        ElapsedUnit.Days -> Res.string.device_stale_days
    }

private val DeviceKind.label: StringResource
    get() = when (this) {
        DeviceKind.Camera -> Res.string.device_kind_camera
        DeviceKind.Lock -> Res.string.device_kind_lock
        DeviceKind.Hub -> Res.string.device_kind_hub
        is DeviceKind.Other -> Res.string.device_kind_other
    }

private val DeviceOrigin.label: StringResource
    get() = when (this) {
        DeviceOrigin.Linked -> Res.string.device_origin_linked
        DeviceOrigin.Shared -> Res.string.device_origin_shared
    }

/** One friendly sentence per category, never the server's own words (SPEC U6). */
private val DeviceListError.message: StringResource
    get() = when (this) {
        DeviceListError.TokenRejected -> Res.string.device_error_rejected
        DeviceListError.TokenExpired -> Res.string.device_error_expired
        DeviceListError.Offline -> Res.string.device_error_offline
        DeviceListError.DeviceNotFound -> Res.string.device_error_device_not_found
        DeviceListError.UnexpectedResponse -> Res.string.device_error_unexpected
        DeviceListError.Failed -> Res.string.device_error_failed
    }
