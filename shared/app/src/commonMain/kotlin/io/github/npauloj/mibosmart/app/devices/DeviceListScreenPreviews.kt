package io.github.npauloj.mibosmart.app.devices

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.github.npauloj.mibosmart.app.ui.AppTheme

// android.content.res.Configuration.UI_MODE_NIGHT_YES, which commonMain cannot import (rule 4).
private const val UI_MODE_NIGHT_YES = 0x20

/**
 * Every state `DeviceListScreenContent` can render (SPEC §2 "Visual acceptance"), declared once: the
 * named previews below take one value each so their names stay readable metadata, and tooling that
 * walks providers gets the same sequence.
 *
 * The success value is the full page of [PreviewFixtures] — 20 rows, a name far too long for one
 * line, an `ultimaVezOnline` twelve days old and a device that was never seen online (SPEC U3).
 */
internal class DeviceListUiStateProvider : PreviewParameterProvider<DeviceListUiState> {

    override val values: Sequence<DeviceListUiState> =
        sequenceOf(Loading, Success, LoadingMore, Empty, Error, Stale)

    internal companion object {
        val Loading = DeviceListUiState(isLoading = true)
        val Success = DeviceListUiState(rows = PreviewFixtures.fullPage, hasMore = true)

        /**
         * SPEC D2: a full page, the footer spinner under it, and the rows exactly where they were.
         *
         * The scroll position is not part of the state, so the preview shows the footer by rendering
         * the top of the list; what it proves is that loading more does not move anything above it.
         */
        val LoadingMore = DeviceListUiState(
            rows = PreviewFixtures.fullPage,
            hasMore = true,
            isLoadingMore = true,
        )
        val Empty = DeviceListUiState()
        val Error = DeviceListUiState(error = DeviceListError.Offline)

        /** SPEC D8: the same rows as [Success], plus how old they are and a way to ask again. */
        val Stale = DeviceListUiState(
            rows = PreviewFixtures.fullPage,
            staleFor = Elapsed(amount = 14, unit = ElapsedUnit.Minutes),
        )
    }
}

@Preview(name = "DeviceListScreen_Loading")
@Preview(name = "DeviceListScreen_Loading_Dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun DeviceListLoadingPreview() = DeviceListPreview(DeviceListUiStateProvider.Loading)

@Preview(name = "DeviceListScreen_Success")
@Preview(name = "DeviceListScreen_Success_Dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun DeviceListSuccessPreview() = DeviceListPreview(DeviceListUiStateProvider.Success)

@Preview(name = "DeviceListScreen_LoadingMore")
@Preview(name = "DeviceListScreen_LoadingMore_Dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun DeviceListLoadingMorePreview() = DeviceListPreview(DeviceListUiStateProvider.LoadingMore)

@Preview(name = "DeviceListScreen_Empty")
@Preview(name = "DeviceListScreen_Empty_Dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun DeviceListEmptyPreview() = DeviceListPreview(DeviceListUiStateProvider.Empty)

@Preview(name = "DeviceListScreen_Error")
@Preview(name = "DeviceListScreen_Error_Dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun DeviceListErrorPreview() = DeviceListPreview(DeviceListUiStateProvider.Error)

@Preview(name = "DeviceListScreen_Stale")
@Preview(name = "DeviceListScreen_Stale_Dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun DeviceListStalePreview() = DeviceListPreview(DeviceListUiStateProvider.Stale)

/** All states side by side, straight from the provider. */
@Preview(name = "DeviceListScreen_AllStates")
@Composable
private fun DeviceListAllStatesPreview(
    @PreviewParameter(DeviceListUiStateProvider::class) state: DeviceListUiState,
) = DeviceListPreview(state)

@Composable
private fun DeviceListPreview(state: DeviceListUiState) {
    AppTheme {
        DeviceListScreenContent(
            state = state,
            onRetry = {},
            onRefresh = {},
            onSelectFilter = {},
            onLoadMore = {},
            onCameraTap = {},
            onLockTap = {},
        )
    }
}
