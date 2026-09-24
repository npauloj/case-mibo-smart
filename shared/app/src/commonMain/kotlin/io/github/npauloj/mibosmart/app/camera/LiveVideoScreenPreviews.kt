package io.github.npauloj.mibosmart.app.camera

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.github.npauloj.mibosmart.app.ui.AppTheme
import io.github.npauloj.mibosmart.domain.camera.PlaybackRetryPolicy
import io.github.npauloj.mibosmart.domain.camera.StreamError
import io.github.npauloj.mibosmart.domain.camera.StreamSession
import io.github.npauloj.mibosmart.domain.camera.StreamState
import io.github.npauloj.mibosmart.domain.camera.StreamStep

private const val UI_MODE_NIGHT_YES = 0x20

/**
 * Every state `LiveVideoScreenContent` can render (SPEC §3 "Visual acceptance"), declared once.
 */
internal class StreamStateProvider : PreviewParameterProvider<StreamState> {

    override val values: Sequence<StreamState> = sequenceOf(
        Creating,
        Live,
        Reconnecting,
        QuotaExceeded,
        Offline,
        NoLiveCapability,
        Failed,
        FailedWithoutFallback,
        WebFallback,
    )

    internal companion object {
        const val CAMERA_NAME = "Câmera da varanda"

        private const val MONITOR_URL = "https://portal.example.invalid/monitor/preview"

        private val SESSION = StreamSession(
            id = "preview-session",
            url = "https://portal.example.invalid/stream/preview",
            monitorUrl = MONITOR_URL,
            quotaGb = 0.5,
        )

        val Creating = StreamState.Creating(StreamStep.CreatingSession)
        val Live = StreamState.Live(SESSION, firstFrame = true)
        val Reconnecting = StreamState.Reconnecting(attempt = 2, total = PlaybackRetryPolicy.MAX_ATTEMPTS)
        val QuotaExceeded = StreamState.QuotaExceeded
        val Offline = StreamState.CameraOffline
        val NoLiveCapability = StreamState.NoLiveCapability

        /** The ladder ran out on a session that carried a `monitor_url`: both ways out are offered. */
        val Failed = StreamState.Failed(StreamError.Playback, MONITOR_URL)

        /**
         * The same failure on a session without a `monitor_url` — the case ADR-005 leaves open
         * and this preview makes visible: one action, and no button that would open nothing
         * (SPEC V9).
         */
        val FailedWithoutFallback = StreamState.Failed(StreamError.Playback)

        val WebFallback = StreamState.WebFallback(MONITOR_URL)
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

@Preview(name = "LiveVideoScreen_Reconnecting")
@Preview(name = "LiveVideoScreen_Reconnecting_Dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun LiveVideoScreenReconnectingPreview() =
    LiveVideoScreenPreview(StreamStateProvider.Reconnecting)

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

@Preview(name = "LiveVideoScreen_FailedWithoutFallback")
@Preview(name = "LiveVideoScreen_FailedWithoutFallback_Dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun LiveVideoScreenFailedWithoutFallbackPreview() =
    LiveVideoScreenPreview(StreamStateProvider.FailedWithoutFallback)

@Preview(name = "LiveVideoScreen_WebFallback")
@Preview(name = "LiveVideoScreen_WebFallback_Dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun LiveVideoScreenWebFallbackPreview() = LiveVideoScreenPreview(StreamStateProvider.WebFallback)

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
            onWebPlayer = {},
            onCloseWebPlayer = {},
            onBack = {},
        )
    }
}
