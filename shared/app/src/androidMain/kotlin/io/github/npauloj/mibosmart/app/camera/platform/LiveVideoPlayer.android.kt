package io.github.npauloj.mibosmart.app.camera.platform

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/**
 * Media3 on the partner's fragmented-MP4 stream (ADR-005).
 *
 * The default extractors are the decision ADR-005 recorded; nothing here is a custom `MediaSource`.
 * The wave-2 checkpoint that had to confirm it against a real camera was spent on ADR-025 instead:
 * the stream was being asked of the wrong host, and the one that answers correctly had an empty
 * upstream that day. **No frame has been decoded from a real camera yet** — the media source is
 * confirmed against the contract, not against a picture.
 *
 * The player is created by `remember(url)` and released by `DisposableEffect`, so leaving the screen,
 * a configuration change, or a new url all release the decoder — the half of SPEC V8 the ViewModel
 * cannot do, because it does not own a player.
 *
 * `monitorUrl` is the partner's own player page and this actual ignores it: Android decodes the
 * stream itself. It is on the surface because iOS plays that page instead (SPEC V10).
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
actual fun LiveVideoPlayer(
    url: String,
    @Suppress("UNUSED_PARAMETER") monitorUrl: String?,
    onEvent: (PlayerEvent) -> Unit,
    modifier: Modifier,
) {
    if (LocalInspectionMode.current) {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant))
        return
    }
    val context = LocalContext.current
    // The listener outlives a recomposition; without this it would keep calling yesterday's lambda.
    val latestOnEvent by rememberUpdatedState(onEvent)
    val player = remember(url) {
        // `setMediaItem`, not a hand-built `ProgressiveMediaSource`: let the media source be
        // chosen from the url rather than fixed here, which is what `DefaultMediaSourceFactory`
        // does. The partner's url is fragmented MP4 over HTTPS (ADR-005, confirmed), so the hand-
        // built one worked — until a session came back pointing somewhere else, and then it threw
        // `MalformedURLException` reported as ERROR_CODE_IO_NETWORK_CONNECTION_FAILED. The retry
        // ladder read that as a flaky network and paid for a new session each time (ADR-025).
        // A wrong url should fail as a wrong url, not as bad weather.
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() = latestOnEvent(PlayerEvent.FirstFrame)

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) latestOnEvent(PlayerEvent.Ended)
            }

            override fun onPlayerError(error: PlaybackException) {
                val isNetwork = error.errorCode in NETWORK_ERROR_CODES
                // The whole reason this log exists: on a real camera the picture failed to appear and
                // the app could say nothing about why — the only instrumentation in the build was the
                // HTTP logger, and `criar-fluxo-video` was answering 200 every time. The error name and
                // code are what separate "the stream dropped" from "this build cannot decode it".
                Log.w(
                    TAG,
                    "error " + error.errorCodeName + " (" + error.errorCode + ")" +
                        " cause=" + (error.cause?.let { it::class.simpleName } ?: "none") +
                        " -> " + (if (isNetwork) "NetworkError" else "DecodeError"),
                    error,
                )
                latestOnEvent(if (isNetwork) PlayerEvent.NetworkError else PlayerEvent.DecodeError)
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    AndroidView(
        factory = { PlayerView(it).apply { useController = false } },
        modifier = modifier,
        update = { it.player = player },
    )
}

/**
 * The errors that mean "the stream dropped" rather than "this stream cannot be played".
 * V-02 retries the first kind and offers the web player for the second (SPEC V4, V5).
 */
private val NETWORK_ERROR_CODES = setOf(
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
    PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
)

/** The player's own tag, so `adb logcat -s MiboSmartPlayer` shows playback failures and nothing else. */
private const val TAG = "MiboSmartPlayer"
