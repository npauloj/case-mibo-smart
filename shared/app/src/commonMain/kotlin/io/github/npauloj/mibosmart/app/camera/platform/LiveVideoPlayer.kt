package io.github.npauloj.mibosmart.app.camera.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The surface that plays one live stream, per platform (ADR-005, architecture rule 8).
 * @param url the fragmented-MP4 stream from `criar-fluxo-video`.
 * @param monitorUrl the partner's ready player page for the same session, or null when it sent
 * none.
 * @param onEvent what the surface saw.
 */
@Composable
expect fun LiveVideoPlayer(
    url: String,
    monitorUrl: String?,
    onEvent: (PlayerEvent) -> Unit,
    modifier: Modifier,
)

/** What a player surface can tell the app (ADR-005). */
enum class PlayerEvent {
    /** Something was drawn. The wait is over and the overlay comes off. */
    FirstFrame,

    /** The stream reached its end — usually the `stream_gb` cap the session was created with. */
    Ended,

    /** The stream dropped. V-02 retries this one. */
    NetworkError,

    /** The stream arrived but could not be decoded. V-02 offers the web player for this one. */
    DecodeError,
}
