package io.github.npauloj.mibosmart.app.camera.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The surface that plays one live stream, per platform (ADR-005, architecture rule 8).
 *
 * It is a composable and not a domain contract because a player *is* UI: it owns a view, a lifecycle
 * and a frame. `:shared:domain` knows only `StreamingRepository` and `StreamSession`, so Media3 and a
 * web view can be two spellings of the same feature without the business rules ever hearing about it.
 *
 * @param url the fragmented-MP4 stream from `criar-fluxo-video`. It expires 15 seconds after the
 *   partner minted it, so an actual prepares it on composition and does not wait for anything.
 * @param onEvent what the surface saw. The ViewModel decides what any of it means.
 *
 * Under `LocalInspectionMode` an actual renders a placeholder: previews are this project's visual
 * evidence and must never open a decoder or a socket.
 */
@Composable
expect fun LiveVideoPlayer(url: String, onEvent: (PlayerEvent) -> Unit, modifier: Modifier)

/**
 * What a player surface can tell the app (ADR-005).
 *
 * The three failure-ish events are kept apart although this slice treats them alike, because V-02
 * needs them apart: a network drop is worth retrying, a decode error never is.
 */
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
