package io.github.npauloj.mibosmart.app.session

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.github.npauloj.mibosmart.app.ui.AppTheme

private const val UI_MODE_NIGHT_YES = 0x20

/**
 * Every state `TokenScreenContent` can render (SPEC §1 "Visual acceptance"), declared once: the
 * named previews below take one value each so their names stay readable metadata, and tooling
 * that walks providers gets the same sequence.
 */
internal class TokenEntryUiStateProvider : PreviewParameterProvider<TokenEntryUiState> {

    override val values: Sequence<TokenEntryUiState> =
        sequenceOf(Idle, Typing, InvalidFormat, Validating, Error)

    internal companion object {
        val Idle = TokenEntryUiState()
        val Typing = TokenEntryUiState(token = SAMPLE_TOKEN)
        val InvalidFormat = TokenEntryUiState(token = TRUNCATED_TOKEN)
        val Validating = TokenEntryUiState(token = SAMPLE_TOKEN, isValidating = true)
        val Error = TokenEntryUiState(token = SAMPLE_TOKEN, error = TokenEntryError.TokenRejected)
    }
}

/**
 * Never a real token, not even in a preview (ADR-008) — and assembled here rather than written
 * out, so the file cannot be mistaken for a leaked credential by the CI secret scan.
 */
private val SAMPLE_TOKEN = TokenFormat.PREFIX + "0123456789abcdef".repeat(2)

/** A paste that lost its second half — what `TokenScreen_InvalidFormat` has to make obvious (S1.2). */
private val TRUNCATED_TOKEN = SAMPLE_TOKEN.take(20)

@Preview(name = "TokenScreen_Idle")
@Preview(name = "TokenScreen_Idle_Dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun TokenScreenIdlePreview() = TokenScreenPreview(TokenEntryUiStateProvider.Idle)

@Preview(name = "TokenScreen_Typing")
@Preview(name = "TokenScreen_Typing_Dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun TokenScreenTypingPreview() = TokenScreenPreview(TokenEntryUiStateProvider.Typing)

@Preview(name = "TokenScreen_InvalidFormat")
@Preview(name = "TokenScreen_InvalidFormat_Dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun TokenScreenInvalidFormatPreview() = TokenScreenPreview(TokenEntryUiStateProvider.InvalidFormat)

@Preview(name = "TokenScreen_Validating")
@Preview(name = "TokenScreen_Validating_Dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun TokenScreenValidatingPreview() = TokenScreenPreview(TokenEntryUiStateProvider.Validating)

@Preview(name = "TokenScreen_Error")
@Preview(name = "TokenScreen_Error_Dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun TokenScreenErrorPreview() = TokenScreenPreview(TokenEntryUiStateProvider.Error)

/** All states side by side, straight from the provider. */
@Preview(name = "TokenScreen_AllStates")
@Composable
private fun TokenScreenAllStatesPreview(
    @PreviewParameter(TokenEntryUiStateProvider::class) state: TokenEntryUiState,
) = TokenScreenPreview(state)

@Composable
private fun TokenScreenPreview(state: TokenEntryUiState) {
    AppTheme {
        TokenScreenContent(state = state, onTokenChange = {}, onValidate = {}, onOpenPortal = {})
    }
}
