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

    override val values: Sequence<DeviceListUiState> = sequenceOf(Loading, Success, Empty, Error)

    internal companion object {
        val Loading = DeviceListUiState(isLoading = true)
        val Success = DeviceListUiState(rows = PreviewFixtures.fullPage)
        val Empty = DeviceListUiState()
        val Error = DeviceListUiState(error = DeviceListError.Offline)
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

@Preview(name = "DeviceListScreen_Empty")
@Preview(name = "DeviceListScreen_Empty_Dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun DeviceListEmptyPreview() = DeviceListPreview(DeviceListUiStateProvider.Empty)

@Preview(name = "DeviceListScreen_Error")
@Preview(name = "DeviceListScreen_Error_Dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun DeviceListErrorPreview() = DeviceListPreview(DeviceListUiStateProvider.Error)

/** All states side by side, straight from the provider. */
@Preview(name = "DeviceListScreen_AllStates")
@Composable
private fun DeviceListAllStatesPreview(
    @PreviewParameter(DeviceListUiStateProvider::class) state: DeviceListUiState,
) = DeviceListPreview(state)

@Composable
private fun DeviceListPreview(state: DeviceListUiState) {
    AppTheme {
        DeviceListScreenContent(state = state, onRetry = {})
    }
}
