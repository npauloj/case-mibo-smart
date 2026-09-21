package io.github.npauloj.mibosmart.app.camera.platform

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
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView

/**
 * Media3 on the partner's fragmented-MP4 stream (ADR-005).
 *
 * `ProgressiveMediaSource` with the default extractors is the decision ADR-005 recorded and the one
 * its wave-2 checkpoint has to confirm against a real camera; nothing here is a custom `MediaSource`.
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
        ExoPlayer.Builder(context).build().apply {
            setMediaSource(
                ProgressiveMediaSource.Factory(DefaultHttpDataSource.Factory())
                    .createMediaSource(MediaItem.fromUri(url)),
            )
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
