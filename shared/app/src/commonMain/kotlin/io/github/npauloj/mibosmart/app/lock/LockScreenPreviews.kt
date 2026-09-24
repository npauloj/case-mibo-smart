package io.github.npauloj.mibosmart.app.lock

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.github.npauloj.mibosmart.app.ui.AppTheme
import io.github.npauloj.mibosmart.domain.lock.LockCommand
import io.github.npauloj.mibosmart.domain.lock.LockState
import io.github.npauloj.mibosmart.domain.lock.VolumeLevel

private const val UI_MODE_NIGHT_YES = 0x20

/**
 * Every state `LockScreenContent` can render (SPEC §4 "Visual acceptance"), declared once: the
 * named previews below take one value each so their names stay readable metadata, and tooling
 * that walks providers gets the same sequence.
 */
internal class LockUiStateProvider : PreviewParameterProvider<LockUiState> {

    override val values: Sequence<LockUiState> =
        sequenceOf(
            Loading,
            Locked,
            Unlocked,
            CommandSent,
            CommandExpired,
            CommandCheckFailed,
            CommandFailed,
            RemoteOpenDisabled,
            VolumeChanging,
            VolumeFailed,
            VolumeUnknown,
            WritesDisabled,
            Offline,
            Error,
        )

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

        /** SPEC L3: the command is out and nothing has confirmed it. */
        val CommandSent = LockUiState.CommandSent(Locked, LockCommand.Open)

        /** SPEC L4: the command was taken and the device never agreed. */
        val CommandExpired = LockUiState.CommandExpired(Locked, LockCommand.Open)

        /** SPEC U6 on the one action `CommandExpired` offers: the check could not answer either. */
        val CommandCheckFailed = CommandExpired.copy(checkFailure = LockError.Offline)

        /** SPEC L5: the command never left; the readings under the notice are untouched. */
        val CommandFailed =
            LockUiState.CommandFailed(Locked, LockCommand.Open, LockError.Offline)

        /** SPEC L2: the precondition explained, with the one action that grants it. */
        val RemoteOpenDisabled = LockUiState.Ready(
            deviceName = LOCK_NAME,
            lock = LockState(isOpen = false, isRemoteOpenEnabled = false, volume = VolumeLevel.Mute),
        )

        /** SPEC L7: `mudar-volume` is in flight. */
        val VolumeChanging = Locked.copy(writeInFlight = LockWrite.Volume(VolumeLevel.High))

        /** SPEC U6: the write did not happen, the reading did not move, and the reason is named. */
        val VolumeFailed = Locked.copy(
            writeFailure = WriteFailure(LockWrite.Volume(VolumeLevel.High), LockError.Offline),
        )

        /** The door answered and the volume did not (ADR-026). */
        val VolumeUnknown = LockUiState.Ready(
            deviceName = LOCK_NAME,
            lock = LockState(isOpen = false, isRemoteOpenEnabled = true, volume = null),
        )

        /**
         * `smarthome.lockWritesEnabled=false`: the selector is dead and the enable action is a
         * sentence rather than a button, because nothing here would reach the door.
         */
        val WritesDisabled = RemoteOpenDisabled.copy(areWritesEnabled = false)

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

@Preview(name = "LockScreen_CommandSent")
@Preview(name = "LockScreen_CommandSent_Dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun LockScreenCommandSentPreview() = LockScreenPreview(LockUiStateProvider.CommandSent)

@Preview(name = "LockScreen_CommandExpired")
@Preview(name = "LockScreen_CommandExpired_Dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun LockScreenCommandExpiredPreview() = LockScreenPreview(LockUiStateProvider.CommandExpired)

@Preview(name = "LockScreen_CommandFailed")
@Preview(name = "LockScreen_CommandFailed_Dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun LockScreenCommandFailedPreview() = LockScreenPreview(LockUiStateProvider.CommandFailed)

@Preview(name = "LockScreen_RemoteOpenDisabled")
@Preview(name = "LockScreen_RemoteOpenDisabled_Dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun LockScreenRemoteOpenDisabledPreview() = LockScreenPreview(LockUiStateProvider.RemoteOpenDisabled)

@Preview(name = "LockScreen_VolumeChanging")
@Preview(name = "LockScreen_VolumeChanging_Dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun LockScreenVolumeChangingPreview() = LockScreenPreview(LockUiStateProvider.VolumeChanging)

@Preview(name = "LockScreen_VolumeFailed")
@Preview(name = "LockScreen_VolumeFailed_Dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun LockScreenVolumeFailedPreview() = LockScreenPreview(LockUiStateProvider.VolumeFailed)

@Preview(name = "LockScreen_WritesDisabled")
@Preview(name = "LockScreen_WritesDisabled_Dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun LockScreenWritesDisabledPreview() = LockScreenPreview(LockUiStateProvider.WritesDisabled)

@Preview(name = "LockScreen_Offline")
@Preview(name = "LockScreen_Offline_Dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun LockScreenOfflinePreview() = LockScreenPreview(LockUiStateProvider.Offline)

@Preview(name = "LockScreen_Error")
@Preview(name = "LockScreen_Error_Dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun LockScreenErrorPreview() = LockScreenPreview(LockUiStateProvider.Error)

@Preview(name = "LockScreen_VolumeUnknown")
@Preview(name = "LockScreen_VolumeUnknown_Dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun LockScreenVolumeUnknownPreview() = LockScreenPreview(LockUiStateProvider.VolumeUnknown)

/** All states side by side, straight from the provider. */
@Preview(name = "LockScreen_AllStates")
@Composable
private fun LockScreenAllStatesPreview(
    @PreviewParameter(LockUiStateProvider::class) state: LockUiState,
) = LockScreenPreview(state)

@Composable
private fun LockScreenPreview(state: LockUiState) {
    AppTheme {
        LockScreenContent(
            state = state,
            onRetry = {},
            onCommand = {},
            onVerify = {},
            onChangeVolume = {},
            onEnableRemoteOpen = {},
            onBack = {},
        )
    }
}
