package io.github.npauloj.mibosmart.app.lock

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.github.npauloj.mibosmart.app.ui.AppTheme
import io.github.npauloj.mibosmart.domain.lock.LockCommand
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

        /**
         * SPEC L3: the command is out and nothing has confirmed it.
         *
         * The state above still reads "Fechada" and every control is dead. That gap is the
         * acceptance criterion made visible: the screen is waiting on hardware, not on itself.
         */
        val CommandSent = LockUiState.CommandSent(Locked, LockCommand.Open)

        /**
         * SPEC L4: the command was taken and the device never agreed.
         *
         * The single most important frame in this screen — the one that stops the app from claiming
         * a door opened. The only way forward is the "Verificar" tap beside it.
         */
        val CommandExpired = LockUiState.CommandExpired(Locked, LockCommand.Open)

        /**
         * SPEC U6 on the one action `CommandExpired` offers: the check could not answer either.
         *
         * It has no named preview of its own — the SPEC's visual acceptance list does not ask for
         * one — but it is in the sequence, so `LockScreen_AllStates` still renders it.
         */
        val CommandCheckFailed = CommandExpired.copy(checkFailure = LockError.Offline)

        /** SPEC L5: the command never left; the readings under the notice are untouched. */
        val CommandFailed =
            LockUiState.CommandFailed(Locked, LockCommand.Open, LockError.Offline)

        /** SPEC L2: the precondition explained, with the one action that grants it. */
        val RemoteOpenDisabled = LockUiState.Ready(
            deviceName = LOCK_NAME,
            lock = LockState(isOpen = false, isRemoteOpenEnabled = false, volume = VolumeLevel.Mute),
        )

        /**
         * SPEC L7: `mudar-volume` is in flight.
         *
         * The selected chip is still Medium — the level the lock reported — while the line below
         * names the level being asked for. That gap is the acceptance criterion made visible.
         */
        val VolumeChanging = Locked.copy(writeInFlight = LockWrite.Volume(VolumeLevel.High))

        /** SPEC U6: the write did not happen, the reading did not move, and the reason is named. */
        val VolumeFailed = Locked.copy(
            writeFailure = WriteFailure(LockWrite.Volume(VolumeLevel.High), LockError.Offline),
        )

        /**
         * The door answered and the volume did not (ADR-026).
         *
         * The frame that proves the screen survives a partial read: "Fechada" is on it, every command
         * is available, and only the selector is out — amber, not red, and saying which reading is
         * missing. Measured 2026-09-23, this is the state of five of the six locks in the test
         * account, so it is the common case rather than an edge one.
         */
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
