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

    // SPEC U2: a tap on a camera or a lock row *is* the navigation — one event, consumed once, no
    // confirmation step in between. Keyed on the ViewModel so a recomposition does not re-subscribe.
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is DeviceListEvent.OpenLiveVideo -> onOpenLiveVideo(event.camera)
                // The address comes with the event: it was assembled from the rows on screen, and the
                // lock screen has no list of its own to find the hub in (api-contract §5).
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
        // SPEC U8: the chips are part of the frame, not of the content — they stay put while the list
        // below swaps between loading, empty and error, so the way out of an empty filter is always
        // on screen (SPEC D3).
        OriginFilterChips(selected = state.filter, onSelect = onSelectFilter)

        // SPEC D7: the pull is the only thing on this screen that refetches by itself. Coming back
        // to the list reuses the ViewModel and costs nothing.
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                // Rows win over every other state: a cache on screen while page 1 is in flight is SPEC
                // U2, a cache on screen after it failed is SPEC D8, and a list under a failed *next*
                // page is SPEC D2 — all three beat a spinner or an error.
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

                // Not loading, no error, no rows: page 1 answered with nothing (SPEC D3, `state.isEmpty`).
                else -> CenteredMessage { Text(stringResource(Res.string.device_empty)) }
            }
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
        // Waiting, not neutral: these rows are real but the app cannot say whether they still
        // describe the account, and that uncertainty is the same category as an unconfirmed command.
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

/**
 * "Todos / Vinculados / Compartilhados", visible in every state (SPEC D4, U8).
 *
 * Exactly one is on at a time, and choosing it reloads the list from page 1 — the chips are the only
 * control on this screen that changes what is being asked for.
 */
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
 * The rows, plus whatever the end of the list currently is: a footer spinner, a failed next page, or
 * nothing at all once the partner has run out of devices (SPEC D2).
 */
@Composable
private fun DeviceRows(
    state: DeviceListUiState,
    onLoadMore: () -> Unit,
    onCameraTap: (DeviceRow) -> Unit,
    onLockTap: (DeviceRow) -> Unit,
) {
    val listState = rememberLazyListState()
    // SPEC D2: the next page is asked for while the user still has a screenful to read, so the list
    // does not stop under their finger. Derived from the list's own layout and nothing else, so it
    // cannot go stale against a row count captured somewhere else.
    val reachedEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            last >= info.totalItemsCount - 1 - NEXT_PAGE_THRESHOLD
        }
    }
    // A failed next page stops the automatic asking (SPEC D8): the user is sitting at the bottom of
    // the list, which is exactly where the trigger fires, and retrying on its own would spend the
    // account's requests in a loop. The footer's button is the way back (ADR-006).
    LaunchedEffect(reachedEnd, state.hasMore, state.error) {
        if (reachedEnd && state.hasMore && state.error == null) onLoadMore()
    }

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        items(state.rows, key = DeviceRow::id) { row ->
            DeviceRowItem(row, onCameraTap, onLockTap)
            HorizontalDivider()
        }
        if (state.isLoadingMore) item { LoadingMoreFooter() }
        // Rows on screen *and* an error means the next page failed: page 1's failures take the list
        // away and never reach this composable (SPEC D2, D8).
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
    // SPEC U2: a camera row is the two-tap path to the picture and a lock row the two-tap path to the
    // door. Which rows take a tap at all is `isActionable`, decided in the mapper: hubs and the rest
    // never do, and neither does a lock whose row is missing a part of its address — a row that
    // reacted to a tap by doing nothing would read as a broken app.
    Column(
        modifier = Modifier.fillMaxWidth()
            .clickable(enabled = row.isActionable) {
                when (row.kind) {
                    DeviceKind.Camera -> onCameraTap(row)
                    DeviceKind.Lock -> onLockTap(row)
                    // Informational rows (SPEC D6); `isActionable` already keeps the tap off them.
                    DeviceKind.Hub, is DeviceKind.Other -> Unit
                }
            }
            .padding(vertical = 12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // The kind, as a shape, before it is read as a word. A list of seventeen rows is scanned
            // for "the camera" or "the lock" and the eye finds a silhouette faster than a noun — the
            // text stays, because an icon alone would be a guess, and a screen reader gets the word.
            Icon(
                painter = painterResource(row.kind.icon),
                contentDescription = null,
                modifier = Modifier.padding(top = 2.dp).size(22.dp),
                // The icon is a label, not a control: `onSurfaceVariant` keeps it in the same voice as
                // the line under the name. Drawn in `primary` it would invite a tap of its own.
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
                        // `tertiary`, not `primary`: since the theme landed, `primary` is the
                        // interactive colour, and a status badge drawn in it invites a tap that does
                        // nothing. The brand green in its "on / working" job is what this is.
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
                // SPEC D6: a sub-device is only addressable through its hub, so the row says which.
                row.parentName?.let {
                    Text(text = it, style = MaterialTheme.typography.labelSmall)
                }
                // SPEC U6: when the row cannot open, it names the cause in one sentence and offers
                // no dead action — the alternative is a lock screen with nothing to address.
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
        // The server's sentence wins only where it is fit to show — an expired token (SPEC U6, S3.1).
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

/**
 * A next page that failed, at the end of the list it could not extend (SPEC D2, D8).
 *
 * The same sentence the error state would have used, in the one place where it does not take the
 * rows away — and the same "Tentar novamente", which here asks for that page and not for page 1.
 */
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

/**
 * Why a lock row is not tappable, in the user's words rather than the contract's (SPEC U6).
 *
 * Exhaustive with no `else`, like every other mapping on this screen: a reason added later does not
 * compile until someone has written the sentence the user reads for it.
 */
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
/**
 * The silhouette for a kind, beside the word for it.
 *
 * `Other` gets a deliberately neutral box: the list must not suggest a capability the app does not
 * have. A lamp drawn as a lamp reads as something this screen can switch on, and it cannot.
 */
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

/**
 * How many rows from the bottom the next page is asked for.
 *
 * Small on purpose: the account pays per request (ADR-006), so the list fetches ahead by a couple of
 * rows — enough to hide the wait on a fast scroll, not enough to pull pages nobody looks at.
 */
private const val NEXT_PAGE_THRESHOLD = 3
