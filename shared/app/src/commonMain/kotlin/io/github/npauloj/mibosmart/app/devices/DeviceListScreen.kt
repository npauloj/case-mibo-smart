package io.github.npauloj.mibosmart.app.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import io.github.npauloj.mibosmart.app.resources.device_list_loading_more
import io.github.npauloj.mibosmart.app.resources.device_list_title
import io.github.npauloj.mibosmart.app.resources.device_lock_product_id_missing
import io.github.npauloj.mibosmart.app.resources.device_origin_linked
import io.github.npauloj.mibosmart.app.resources.device_origin_shared
import io.github.npauloj.mibosmart.app.resources.device_retry
import io.github.npauloj.mibosmart.app.resources.device_stale_days
import io.github.npauloj.mibosmart.app.resources.device_stale_hours
import io.github.npauloj.mibosmart.app.resources.device_stale_minutes
import io.github.npauloj.mibosmart.app.resources.device_status_offline
import io.github.npauloj.mibosmart.app.resources.device_status_online
import io.github.npauloj.mibosmart.app.resources.ic_device_camera
import io.github.npauloj.mibosmart.app.resources.ic_device_hub
import io.github.npauloj.mibosmart.app.resources.ic_device_lock
import io.github.npauloj.mibosmart.app.resources.ic_device_other
import io.github.npauloj.mibosmart.app.ui.LocalAppColors
import io.github.npauloj.mibosmart.domain.device.Device
import io.github.npauloj.mibosmart.domain.device.DeviceKind
import io.github.npauloj.mibosmart.domain.device.DeviceOrigin
import io.github.npauloj.mibosmart.domain.device.OriginFilter
import io.github.npauloj.mibosmart.domain.lock.LockAddress
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** The hub of the app: every other feature is reached from a row here (SPEC D1–D7, U2, U3, U8). */
@Composable
fun DeviceListScreen(
    onOpenLiveVideo: (Device) -> Unit,
    onOpenLock: (Device, LockAddress) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DeviceListViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is DeviceListEvent.OpenLiveVideo -> onOpenLiveVideo(event.camera)
                is DeviceListEvent.OpenLock -> onOpenLock(event.lock, event.address)
            }
        }
    }

    DeviceListScreenContent(
        state = state,
        onRetry = viewModel::load,
        onRefresh = viewModel::refresh,
        onSelectFilter = viewModel::selectFilter,
        onLoadMore = viewModel::loadMore,
        onCameraTap = viewModel::onCameraTap,
        onLockTap = viewModel::onLockTap,
        modifier = modifier,
    )
}

/** The screen as a pure function of its state, so every state has a preview and no ViewModel. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceListScreenContent(
    state: DeviceListUiState,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    onSelectFilter: (OriginFilter) -> Unit,
    onLoadMore: () -> Unit,
    onCameraTap: (DeviceRow) -> Unit,
    onLockTap: (DeviceRow) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 24.dp)) {
        Text(
            text = stringResource(Res.string.device_list_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        OriginFilterChips(selected = state.filter, onSelect = onSelectFilter)

        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                state.rows.isNotEmpty() -> Column {
                    state.staleFor?.let { StaleBanner(staleFor = it, onRetry = onRetry) }
                    DeviceRows(
                        state = state,
                        onLoadMore = onLoadMore,
                        onCameraTap = onCameraTap,
                        onLockTap = onLockTap,
                    )
                }

                state.isLoading -> CenteredMessage { LoadingIndicator() }
                state.error != null -> CenteredMessage {
                    ErrorState(error = state.error, serverMessage = state.serverMessage, onRetry = onRetry)
                }

                else -> CenteredMessage { Text(stringResource(Res.string.device_empty)) }
            }
        }
    }
}

/**
 * "Sem conexão — última atualização há N min", above rows the app could not refresh (SPEC D8).
 */
@Composable
private fun StaleBanner(staleFor: Elapsed, onRetry: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(LocalAppColors.current.waitingContainer, MaterialTheme.shapes.small)
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(staleFor.unit.staleTemplate, staleFor.amount.toString()),
            style = MaterialTheme.typography.bodySmall,
            color = LocalAppColors.current.onWaitingContainer,
            modifier = Modifier.weight(1f, fill = false),
        )
        TextButton(onClick = onRetry) { Text(stringResource(Res.string.device_retry)) }
    }
}

/** "Todos / Vinculados / Compartilhados", visible in every state (SPEC D4, U8). */
@Composable
private fun OriginFilterChips(selected: OriginFilter, onSelect: (OriginFilter) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
        OriginFilter.entries.forEach { filter ->
            FilterChip(
                selected = filter == selected,
                onClick = { onSelect(filter) },
                label = { Text(stringResource(filter.label)) },
            )
        }
    }
}

/**
 * The rows, plus whatever the end of the list currently is: a footer spinner, a failed next
 * page, or nothing at all once the partner has run out of devices (SPEC D2).
 */
@Composable
private fun DeviceRows(
    state: DeviceListUiState,
    onLoadMore: () -> Unit,
    onCameraTap: (DeviceRow) -> Unit,
    onLockTap: (DeviceRow) -> Unit,
) {
    val listState = rememberLazyListState()
    val reachedEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            last >= info.totalItemsCount - 1 - NEXT_PAGE_THRESHOLD
        }
    }
    LaunchedEffect(reachedEnd, state.hasMore, state.error) {
        if (reachedEnd && state.hasMore && state.error == null) onLoadMore()
    }

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        items(state.rows, key = DeviceRow::id) { row ->
            DeviceRowItem(row, onCameraTap, onLockTap)
            HorizontalDivider()
        }
        if (state.isLoadingMore) item { LoadingMoreFooter() }
        state.error?.let { failure ->
            item { NextPageError(error = failure, serverMessage = state.serverMessage, onRetry = onLoadMore) }
        }
    }
}

@Composable
private fun DeviceRowItem(
    row: DeviceRow,
    onCameraTap: (DeviceRow) -> Unit,
    onLockTap: (DeviceRow) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .clickable(enabled = row.isActionable) {
                when (row.kind) {
                    DeviceKind.Camera -> onCameraTap(row)
                    DeviceKind.Lock -> onLockTap(row)
                    DeviceKind.Hub, is DeviceKind.Other -> Unit
                }
            }
            .padding(vertical = 12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(
                painter = painterResource(row.kind.icon),
                contentDescription = null,
                modifier = Modifier.padding(top = 2.dp).size(22.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(modifier = Modifier.weight(1f)) {
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
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(
                            if (row.isOnline) {
                                Res.string.device_status_online
                            } else {
                                Res.string.device_status_offline
                            },
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (row.isOnline) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    Text(
                        text = stringResource(row.origin.label),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    row.lastSeen?.let {
                        Text(text = it.text(), style = MaterialTheme.typography.labelMedium)
                    }
                }
                row.parentName?.let {
                    Text(text = it, style = MaterialTheme.typography.labelSmall)
                }
                row.unavailable?.let {
                    Text(
                        text = stringResource(it.message),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
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
        Text(text = serverMessage ?: stringResource(error.message))
        Button(onClick = onRetry) { Text(stringResource(Res.string.device_retry)) }
    }
}

/** The list is still loading, but only at the bottom: the rows above stay put (SPEC D2). */
@Composable
private fun LoadingMoreFooter() {
    val loadingMore = stringResource(Res.string.device_list_loading_more)

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.semantics { contentDescription = loadingMore })
    }
}

/** A next page that failed, at the end of the list it could not extend (SPEC D2, D8). */
@Composable
private fun NextPageError(error: DeviceListError, serverMessage: String?, onRetry: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = serverMessage ?: stringResource(error.message),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f, fill = false),
        )
        TextButton(onClick = onRetry) { Text(stringResource(Res.string.device_retry)) }
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

/** Why a lock row is not tappable, in the user's words rather than the contract's (SPEC U6). */
private val LockAddressing.Unavailable.message: StringResource
    get() = when (this) {
        LockAddressing.Unavailable.ProductIdMissing -> Res.string.device_lock_product_id_missing
    }

private val DeviceOrigin.label: StringResource
    get() = when (this) {
        DeviceOrigin.Linked -> Res.string.device_origin_linked
        DeviceOrigin.Shared -> Res.string.device_origin_shared
    }

/** The chips, in the order the user reads them (SPEC D4). */
/** The silhouette for a kind, beside the word for it. */
private val DeviceKind.icon: DrawableResource
    get() = when (this) {
        DeviceKind.Camera -> Res.drawable.ic_device_camera
        DeviceKind.Lock -> Res.drawable.ic_device_lock
        DeviceKind.Hub -> Res.drawable.ic_device_hub
        is DeviceKind.Other -> Res.drawable.ic_device_other
    }

private val OriginFilter.label: StringResource
    get() = when (this) {
        OriginFilter.All -> Res.string.device_filter_all
        OriginFilter.Linked -> Res.string.device_filter_linked
        OriginFilter.Shared -> Res.string.device_filter_shared
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

/** How many rows from the bottom the next page is asked for. */
private const val NEXT_PAGE_THRESHOLD = 3
