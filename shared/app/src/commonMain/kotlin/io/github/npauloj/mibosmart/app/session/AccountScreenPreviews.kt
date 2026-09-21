package io.github.npauloj.mibosmart.app.session

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.github.npauloj.mibosmart.app.ui.AppTheme

// android.content.res.Configuration.UI_MODE_NIGHT_YES, which commonMain cannot import (rule 4).
private const val UI_MODE_NIGHT_YES = 0x20

/**
 * Every state `AccountScreenContent` can render, declared once: the named previews below take one
 * value each so their names stay readable metadata, and tooling that walks providers gets the same
 * sequence.
 */
internal class AccountUiStateProvider : PreviewParameterProvider<AccountUiState> {

    override val values: Sequence<AccountUiState> = sequenceOf(Valid, ExpiringSoon, Expired)

    internal companion object {

        /** A fresh session, with the debug counter the case is measured by (ADR-006). */
        val Valid = AccountUiState(
            tokenSuffix = SUFFIX,
            expiry = SessionExpiry.Remaining(hours = 1, minutes = 47, soon = false),
            requestCount = 12,
        )

        /** Inside the last 10 minutes — the visual proof that SPEC S7 is visible here too. */
        val ExpiringSoon = AccountUiState(
            tokenSuffix = SUFFIX,
            expiry = SessionExpiry.Remaining(hours = 0, minutes = 7, soon = true),
            requestCount = 148,
        )

        /** Past the 2 h: the next request will be refused and the guard will act (SPEC S6). */
        val Expired = AccountUiState(
            tokenSuffix = SUFFIX,
            expiry = SessionExpiry.Expired,
            requestCount = 287,
        )
    }
}

/**
 * The last 4 characters of a token that never existed.
 *
 * A suffix is not a credential, but it is written here as its own constant so the file cannot grow a
 * full token by accident — the shape the CI secret scan looks for (ADR-008).
 */
private const val SUFFIX = "4f7c"

@Preview(name = "AccountScreen_Valid")
@Preview(name = "AccountScreen_Valid_Dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun AccountScreenValidPreview() = AccountScreenPreview(AccountUiStateProvider.Valid)

@Preview(name = "AccountScreen_ExpiringSoon")
@Preview(name = "AccountScreen_ExpiringSoon_Dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun AccountScreenExpiringSoonPreview() = AccountScreenPreview(AccountUiStateProvider.ExpiringSoon)

@Preview(name = "AccountScreen_Expired")
@Preview(name = "AccountScreen_Expired_Dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun AccountScreenExpiredPreview() = AccountScreenPreview(AccountUiStateProvider.Expired)

/** All states side by side, straight from the provider. */
@Preview(name = "AccountScreen_AllStates")
@Composable
private fun AccountScreenAllStatesPreview(
    @PreviewParameter(AccountUiStateProvider::class) state: AccountUiState,
) = AccountScreenPreview(state)

@Composable
private fun AccountScreenPreview(state: AccountUiState) {
    AppTheme {
        AccountScreenContent(state = state, onSignOut = {}, onBack = {})
    }
}
