package io.github.npauloj.mibosmart.app.lock

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.github.npauloj.mibosmart.app.ui.AppTheme
import io.github.npauloj.mibosmart.domain.lock.LockState
import io.github.npauloj.mibosmart.domain.lock.VolumeLevel

// android.content.res.Configuration.UI_MODE_NIGHT_YES, which commonMain cannot import (rule 4).
private const val UI_MODE_NIGHT_YES = 0x20

/**
 * Every state `LockScreenContent` can render (SPEC §4 "Visual acceptance"), declared once: the named
 * previews below take one value each so their names stay readable metadata, and tooling that walks
 * providers gets the same sequence.
 *
 * The device name is a placeholder. A real lock's `nome` carries part of its serial
 * (`docs/api-contract.md` §3) and no identifier of the test account is committed (ADR-008).
 */
internal class LockUiStateProvider : PreviewParameterProvider<LockUiState> {

    override val values: Sequence<LockUiState> =
        sequenceOf(Loading, Locked, Unlocked, RemoteOpenDisabled, Offline, Error)

    internal companion object {
        private const val LOCK_NAME = "Fechadura da entrada"

        val Loading = LockUiState.Loading(deviceName = LOCK_NAME)

        val Locked = LockUiState.Ready(
            deviceName = LOCK_NAME,
            lock = LockState(isOpen = false, isRemoteOpenEnabled = true, volume = VolumeLevel.Medium),
        )

        val Unlocked = LockUiState.Ready(
            deviceName = LOCK_NAME,
            lock = LockState(isOpen = true, isRemoteOpenEnabled = true, volume = VolumeLevel.High),
        )

        /** SPEC L2: the precondition is explained and nothing on the screen can grant it (L-01b). */
        val RemoteOpenDisabled = LockUiState.Ready(
            deviceName = LOCK_NAME,
            lock = LockState(isOpen = false, isRemoteOpenEnabled = false, volume = VolumeLevel.Mute),
        )

        /** SPEC L5 / U3: the last thing known about the lock, with how old it is. */
        val Offline = LockUiState.Ready(
            deviceName = LOCK_NAME,
            lock = LockState(isOpen = false, isRemoteOpenEnabled = true, volume = VolumeLevel.Low),
            lastSeen = LastSeen.Hours(3),
        )

        val Error = LockUiState.Failed(deviceName = LOCK_NAME, error = LockError.Offline)
    }
}

@Preview(name = "LockScreen_Loading")
@Preview(name = "LockScreen_Loading_Dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun LockScreenLoadingPreview() = LockScreenPreview(LockUiStateProvider.Loading)

@Preview(name = "LockScreen_Locked")
@Preview(name = "LockScreen_Locked_Dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun LockScreenLockedPreview() = LockScreenPreview(LockUiStateProvider.Locked)

@Preview(name = "LockScreen_Unlocked")
@Preview(name = "LockScreen_Unlocked_Dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun LockScreenUnlockedPreview() = LockScreenPreview(LockUiStateProvider.Unlocked)

@Preview(name = "LockScreen_RemoteOpenDisabled")
@Preview(name = "LockScreen_RemoteOpenDisabled_Dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun LockScreenRemoteOpenDisabledPreview() = LockScreenPreview(LockUiStateProvider.RemoteOpenDisabled)

@Preview(name = "LockScreen_Offline")
@Preview(name = "LockScreen_Offline_Dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun LockScreenOfflinePreview() = LockScreenPreview(LockUiStateProvider.Offline)

@Preview(name = "LockScreen_Error")
@Preview(name = "LockScreen_Error_Dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun LockScreenErrorPreview() = LockScreenPreview(LockUiStateProvider.Error)

/** All states side by side, straight from the provider. */
@Preview(name = "LockScreen_AllStates")
@Composable
private fun LockScreenAllStatesPreview(
    @PreviewParameter(LockUiStateProvider::class) state: LockUiState,
) = LockScreenPreview(state)

@Composable
private fun LockScreenPreview(state: LockUiState) {
    AppTheme {
        LockScreenContent(state = state, onRetry = {}, onBack = {})
    }
}
