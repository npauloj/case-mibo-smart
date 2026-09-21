package io.github.npauloj.mibosmart.app.camera

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.github.npauloj.mibosmart.app.ui.AppTheme
import io.github.npauloj.mibosmart.domain.camera.StreamError
import io.github.npauloj.mibosmart.domain.camera.StreamSession
import io.github.npauloj.mibosmart.domain.camera.StreamState
import io.github.npauloj.mibosmart.domain.camera.StreamStep

// android.content.res.Configuration.UI_MODE_NIGHT_YES, which commonMain cannot import (rule 4).
private const val UI_MODE_NIGHT_YES = 0x20

/**
 * Every state `LiveVideoScreenContent` can render (SPEC §3 "Visual acceptance"), declared once.
 *
 * The url is a placeholder and never reaches a decoder: the player surface renders a plain rectangle
 * under `LocalInspectionMode` (ADR-005), so a preview opens no socket and spends no quota. No serial
 * of the test account appears here (ADR-008).
 */
internal class StreamStateProvider : PreviewParameterProvider<StreamState> {

    override val values: Sequence<StreamState> =
        sequenceOf(Creating, Live, Expired, QuotaExceeded, Offline, NoLiveCapability, Failed)

    internal companion object {
        const val CAMERA_NAME = "Câmera da varanda"

        private val SESSION = StreamSession(
            id = "preview-session",
            url = "https://portal.example.invalid/stream/preview",
            monitorUrl = null,
            quotaGb = 0.5,
        )

        val Creating = StreamState.Creating(StreamStep.CreatingSession)
        val Live = StreamState.Live(SESSION, firstFrame = true)
        val Expired = StreamState.Expired
        val QuotaExceeded = StreamState.QuotaExceeded
        val Offline = StreamState.CameraOffline
        val NoLiveCapability = StreamState.NoLiveCapability
        val Failed = StreamState.Failed(StreamError.Offline)
    }
}

@Preview(name = "LiveVideoScreen_Creating")
@Preview(name = "LiveVideoScreen_Creating_Dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun LiveVideoScreenCreatingPreview() = LiveVideoScreenPreview(StreamStateProvider.Creating)

@Preview(name = "LiveVideoScreen_Live")
@Preview(name = "LiveVideoScreen_Live_Dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun LiveVideoScreenLivePreview() = LiveVideoScreenPreview(StreamStateProvider.Live)

@Preview(name = "LiveVideoScreen_Expired")
@Preview(name = "LiveVideoScreen_Expired_Dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun LiveVideoScreenExpiredPreview() = LiveVideoScreenPreview(StreamStateProvider.Expired)

@Preview(name = "LiveVideoScreen_QuotaExceeded")
@Preview(name = "LiveVideoScreen_QuotaExceeded_Dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun LiveVideoScreenQuotaExceededPreview() = LiveVideoScreenPreview(StreamStateProvider.QuotaExceeded)

@Preview(name = "LiveVideoScreen_Offline")
@Preview(name = "LiveVideoScreen_Offline_Dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun LiveVideoScreenOfflinePreview() = LiveVideoScreenPreview(StreamStateProvider.Offline)

@Preview(name = "LiveVideoScreen_NoLiveCapability")
@Preview(name = "LiveVideoScreen_NoLiveCapability_Dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun LiveVideoScreenNoLiveCapabilityPreview() =
    LiveVideoScreenPreview(StreamStateProvider.NoLiveCapability)

@Preview(name = "LiveVideoScreen_Failed")
@Preview(name = "LiveVideoScreen_Failed_Dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun LiveVideoScreenFailedPreview() = LiveVideoScreenPreview(StreamStateProvider.Failed)

/** All states side by side, straight from the provider. */
@Preview(name = "LiveVideoScreen_AllStates")
@Composable
private fun LiveVideoScreenAllStatesPreview(
    @PreviewParameter(StreamStateProvider::class) state: StreamState,
) = LiveVideoScreenPreview(state)

@Composable
private fun LiveVideoScreenPreview(state: StreamState) {
    AppTheme {
        LiveVideoScreenContent(
            cameraName = StreamStateProvider.CAMERA_NAME,
            state = state,
            onPlayerEvent = {},
            onRetry = {},
            onBack = {},
        )
    }
}
