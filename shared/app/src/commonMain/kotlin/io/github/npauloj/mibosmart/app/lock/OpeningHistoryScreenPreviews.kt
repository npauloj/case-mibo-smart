package io.github.npauloj.mibosmart.app.lock

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.github.npauloj.mibosmart.app.ui.AppTheme
import io.github.npauloj.mibosmart.domain.lock.OpeningKind

private const val UI_MODE_NIGHT_YES = 0x20

/**
 * Every state `OpeningHistoryContent` can render (SPEC §4 "Visual acceptance"), declared once:
 * the named previews below take one value each so their names stay readable metadata, and
 * tooling that walks providers gets the same sequence.
 */
internal class OpeningHistoryUiStateProvider : PreviewParameterProvider<OpeningHistoryUiState> {

    override val values: Sequence<OpeningHistoryUiState> =
        sequenceOf(Loading, List, Empty, Error)

    internal companion object {

        val Loading = OpeningHistoryUiState.Loading

        /**
         * SPEC L9 and U4 in one frame: newest first, both times on every row, and every kind of
         * opening the contract can produce.
         */
        val List = OpeningHistoryUiState.Entries(
            rows = listOf(
                OpeningRow(OpeningKind.Remote, "Casa Inteligente", LastSeen.Moments, "21/09/2026 12:00"),
                OpeningRow(OpeningKind.Remote, "Ana", LastSeen.Minutes(5), "21/09/2026 11:55"),
                OpeningRow(OpeningKind.Local, null, LastSeen.Hours(3), "21/09/2026 09:00"),
                OpeningRow(OpeningKind.Remote, null, LastSeen.Days(2), "19/09/2026 18:42"),
                OpeningRow(
                    OpeningKind.Unknown("biometria"),
                    null,
                    LastSeen.Days(4),
                    "17/09/2026 07:15",
                    catalogLabel = "Abertura por biometria",
                ),
                OpeningRow(OpeningKind.Unknown("teclado"), null, LastSeen.Days(6), "15/09/2026 08:30"),
                OpeningRow(OpeningKind.Unknown(""), null, LastSeen.Days(9), "12/09/2026 22:05"),
            ),
        )

        /** SPEC L10: the partner answered, and this door has not been opened. */
        val Empty = OpeningHistoryUiState.Entries(rows = emptyList())

        val Error = OpeningHistoryUiState.Failed(LockError.Offline)
    }
}

@Preview(name = "OpeningHistory_Loading")
@Preview(name = "OpeningHistory_Loading_Dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun OpeningHistoryLoadingPreview() = OpeningHistoryPreview(OpeningHistoryUiStateProvider.Loading)

@Preview(name = "OpeningHistory_List")
@Preview(name = "OpeningHistory_List_Dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun OpeningHistoryListPreview() = OpeningHistoryPreview(OpeningHistoryUiStateProvider.List)

@Preview(name = "OpeningHistory_Empty")
@Preview(name = "OpeningHistory_Empty_Dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun OpeningHistoryEmptyPreview() = OpeningHistoryPreview(OpeningHistoryUiStateProvider.Empty)

@Preview(name = "OpeningHistory_Error")
@Preview(name = "OpeningHistory_Error_Dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun OpeningHistoryErrorPreview() = OpeningHistoryPreview(OpeningHistoryUiStateProvider.Error)

/** All states side by side, straight from the provider. */
@Preview(name = "OpeningHistory_AllStates")
@Composable
private fun OpeningHistoryAllStatesPreview(
    @PreviewParameter(OpeningHistoryUiStateProvider::class) state: OpeningHistoryUiState,
) = OpeningHistoryPreview(state)

@Composable
private fun OpeningHistoryPreview(state: OpeningHistoryUiState) {
    AppTheme {
        OpeningHistoryContent(state = state, onRetry = {}, onBack = {})
    }
}
