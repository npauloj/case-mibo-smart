package io.github.npauloj.mibosmart.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.github.npauloj.mibosmart.app.devices.DeviceListScreenContent
import io.github.npauloj.mibosmart.app.devices.DeviceListUiStateProvider
import io.github.npauloj.mibosmart.app.ui.AppTheme

// android.content.res.Configuration.UI_MODE_NIGHT_YES, which commonMain cannot import (rule 4).
private const val UI_MODE_NIGHT_YES = 0x20

/**
 * The device-list destination with and without the session banner (SPEC S7).
 *
 * Both previews render the real composition — the banner above the list's success state — so the
 * thing being reviewed is the layout shift the banner causes, not a strip on its own.
 */
@Preview(name = "DeviceListDestination_ExpiringSoon")
@Preview(name = "DeviceListDestination_ExpiringSoon_Dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun DeviceListDestinationExpiringSoonPreview() = DeviceListDestinationPreview(expiringSoon = true)

@Preview(name = "DeviceListDestination_Valid")
@Preview(name = "DeviceListDestination_Valid_Dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun DeviceListDestinationValidPreview() = DeviceListDestinationPreview(expiringSoon = false)

@Composable
private fun DeviceListDestinationPreview(expiringSoon: Boolean) {
    AppTheme {
        DeviceListDestination(expiringSoon) {
            DeviceListScreenContent(state = DeviceListUiStateProvider.Success, onRetry = {})
        }
    }
}
