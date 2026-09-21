package io.github.npauloj.mibosmart.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.github.npauloj.mibosmart.app.devices.DeviceListScreenContent
import io.github.npauloj.mibosmart.app.devices.DeviceListUiStateProvider
import io.github.npauloj.mibosmart.app.session.TokenEntryUiStateProvider
import io.github.npauloj.mibosmart.app.session.TokenScreenContent
import io.github.npauloj.mibosmart.app.ui.AppTheme
import io.github.npauloj.mibosmart.domain.session.SessionEndReason

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
        DeviceListDestination(expiringSoon, onOpenAccount = {}) {
            DeviceListScreenContent(
                state = DeviceListUiStateProvider.Success,
                onRetry = {},
                onRefresh = {},
                onSelectFilter = {},
                onLoadMore = {},
                onCameraTap = {},
            )
        }
    }
}

/**
 * The token screen under the reason it was reopened (SPEC U5, S6).
 *
 * Two previews because the two reasons read differently: the partner's own sentence on a 403, and the
 * app's wording — the one that names the 2 h — on everything else (ADR-012).
 */
@Preview(name = "TokenEntryDestination_Expired")
@Preview(name = "TokenEntryDestination_Expired_Dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun TokenEntryDestinationExpiredPreview() = TokenEntryDestinationPreview(SessionEndReason.Expired)

@Preview(name = "TokenEntryDestination_StatedByPartner")
@Preview(name = "TokenEntryDestination_StatedByPartner_Dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun TokenEntryDestinationStatedPreview() =
    TokenEntryDestinationPreview(SessionEndReason.StatedByPartner(PARTNER_SENTENCE))

/** The sentence the partner really answers a 403 with, probed 2026-09-21 (ADR-012). */
private const val PARTNER_SENTENCE = "Token expirado, por favor gere um novo token"

@Composable
private fun TokenEntryDestinationPreview(reason: SessionEndReason) {
    AppTheme {
        TokenEntryDestination(reason) {
            TokenScreenContent(
                state = TokenEntryUiStateProvider.Idle,
                onTokenChange = {},
                onValidate = {},
            )
        }
    }
}
