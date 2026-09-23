package io.github.npauloj.mibosmart.app.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.npauloj.mibosmart.app.camera.platform.LiveVideoPlayer
import io.github.npauloj.mibosmart.app.camera.platform.PlayerEvent
import io.github.npauloj.mibosmart.app.camera.platform.WebPlayerFallback
import io.github.npauloj.mibosmart.app.resources.Res
import io.github.npauloj.mibosmart.app.resources.live_back
import io.github.npauloj.mibosmart.app.resources.live_badge
import io.github.npauloj.mibosmart.app.resources.live_camera_offline
import io.github.npauloj.mibosmart.app.resources.live_close_web_player
import io.github.npauloj.mibosmart.app.resources.live_error_expired
import io.github.npauloj.mibosmart.app.resources.live_error_failed
import io.github.npauloj.mibosmart.app.resources.live_error_offline
import io.github.npauloj.mibosmart.app.resources.live_error_playback
import io.github.npauloj.mibosmart.app.resources.live_error_rejected
import io.github.npauloj.mibosmart.app.resources.live_error_unexpected
import io.github.npauloj.mibosmart.app.resources.live_no_capability
import io.github.npauloj.mibosmart.app.resources.live_open_web_player
import io.github.npauloj.mibosmart.app.resources.live_quota
import io.github.npauloj.mibosmart.app.resources.live_quota_exceeded
import io.github.npauloj.mibosmart.app.resources.live_retry
import io.github.npauloj.mibosmart.app.resources.live_step_capability
import io.github.npauloj.mibosmart.app.resources.live_step_connecting
import io.github.npauloj.mibosmart.app.resources.live_step_reconnecting
import io.github.npauloj.mibosmart.app.resources.live_step_session
import io.github.npauloj.mibosmart.app.ui.StateRail
import io.github.npauloj.mibosmart.app.ui.StateTone
import io.github.npauloj.mibosmart.domain.camera.StreamError
import io.github.npauloj.mibosmart.domain.camera.StreamState
import io.github.npauloj.mibosmart.domain.camera.StreamStep
import io.github.npauloj.mibosmart.domain.device.Device
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Watching one camera live (SPEC V1, V2, V3, V6, V7, V8).
 *
 * The lifecycle wiring is the safety-critical part of this file. A stream left running while the
 * phone is in a pocket spends the account's quota for nothing, so `ON_STOP` tears the session down
 * and `ON_START` brings it back only if it was torn down (ADR-005, SPEC V8).
 */
@Composable
fun LiveVideoScreen(
    camera: Device,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LiveVideoViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(camera) { viewModel.open(camera) }
    LiveVideoLifecycle(viewModel)

    LiveVideoScreenContent(
        cameraName = camera.name,
        state = state,
        onPlayerEvent = viewModel::onPlayerEvent,
        onRetry = viewModel::retry,
        onWebPlayer = viewModel::openWebPlayer,
        onCloseWebPlayer = viewModel::closeWebPlayer,
        onBack = {
            viewModel.stop()
            onBack()
        },
        modifier = modifier,
    )
}

/**
 * The half of SPEC V8 the screen owns: a stream must not survive the app going to the background.
 *
 * It is its own composable, and emits nothing, so it can be composed on its own in a test with a fake
 * `LifecycleOwner` (`LiveVideoScreenLifecycleTest`) — the wiring that silently costs the account a
 * gigabyte when it breaks is the wiring worth covering.
 */
@Composable
internal fun LiveVideoLifecycle(viewModel: LiveVideoViewModel) {
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.stop() }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.resume() }
}

/** The screen as a pure function of its state, so every state has a preview and no ViewModel. */
@Composable
fun LiveVideoScreenContent(
    cameraName: String,
    state: StreamState,
    onPlayerEvent: (PlayerEvent) -> Unit,
    onRetry: () -> Unit,
    onWebPlayer: () -> Unit,
    onCloseWebPlayer: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TextButton(onClick = onBack) { Text(stringResource(Res.string.live_back)) }
        Text(text = cameraName, style = MaterialTheme.typography.headlineSmall)

        // The rail is the only thing on this screen that reports state without words, which matters
        // here more than anywhere: the picture is the content, and a person looking at a frame that is
        // not moving needs to know which kind of "not moving" it is.
        StateRail(tone = state.tone()) {
            when (state) {
                // The frame before the first step, and the frame after teardown: an empty surface, not a
                // collapsed layout, so the screen never jumps as the states go by.
                StreamState.Idle -> VideoSurface {}
                is StreamState.Creating -> VideoSurface { StepLabel(state.step) }
                is StreamState.Live -> LiveSurface(state, onPlayerEvent)
                // The attempt is counted in words over the same frame the picture will land on, so the
                // screen neither jumps nor pretends to know a percentage (SPEC V3, U1).
                is StreamState.Reconnecting -> VideoSurface {
                    Label(
                        stringResource(
                            Res.string.live_step_reconnecting,
                            state.attempt.toString(),
                            state.total.toString(),
                        ),
                    )
                }
                StreamState.NoLiveCapability -> Explanation(Res.string.live_no_capability)
                StreamState.QuotaExceeded -> Explanation(Res.string.live_quota_exceeded)
                StreamState.CameraOffline -> Explanation(Res.string.live_camera_offline)
                // The web player is offered only when the session carried a `monitor_url`: with none
                // there is nothing to open, and a dead button is worse than no button (SPEC V9, ADR-005).
                is StreamState.Failed ->
                    Explanation(state.error.message, onRetry, state.monitorUrl?.let { onWebPlayer })
                is StreamState.WebFallback -> WebFallbackSurface(state.url, onCloseWebPlayer)
            }
        }
    }
}

/**
 * What kind of "no picture" this is.
 *
 * `Live` gets the brand's own colour because it is the one state on this screen where everything is
 * working. Creating and reconnecting are [StateTone.Waiting] — the app is trying and does not know
 * yet. A camera that is offline or has no live capability is [StateTone.Settled], not a failure:
 * nothing went wrong, the answer is simply no (SPEC V7).
 */
private fun StreamState.tone(): StateTone = when (this) {
    StreamState.Idle -> StateTone.Settled
    is StreamState.Creating -> StateTone.Waiting
    is StreamState.Reconnecting -> StateTone.Waiting
    is StreamState.Live -> StateTone.Live
    is StreamState.WebFallback -> StateTone.Live
    StreamState.NoLiveCapability -> StateTone.Settled
    StreamState.CameraOffline -> StateTone.Settled
    StreamState.QuotaExceeded -> StateTone.Failed
    is StreamState.Failed -> StateTone.Failed
}

/**
 * The stream, with the wait named on top of it until the first frame arrives (SPEC V2, V3).
 *
 * The player is composed as soon as there is a url — that is the whole of V2, since the url stops
 * working 15 seconds after the partner minted it.
 */
@Composable
private fun LiveSurface(state: StreamState.Live, onPlayerEvent: (PlayerEvent) -> Unit) {
    VideoSurface {
        LiveVideoPlayer(
            url = state.session.url,
            monitorUrl = state.session.monitorUrl,
            onEvent = onPlayerEvent,
            modifier = Modifier.fillMaxSize(),
        )
        if (state.firstFrame) {
            Text(
                text = stringResource(Res.string.live_badge),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
            )
        } else {
            StepLabel(StreamStep.Connecting)
        }
    }
    state.session.quotaGb?.let {
        Text(
            text = stringResource(Res.string.live_quota, it.toString()),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** One 16:9 frame, so the screen does not jump when the picture replaces the wait. */
@Composable
private fun VideoSurface(content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().aspectRatio(SURFACE_RATIO)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/**
 * SPEC V3: the wait is a sentence, not a percentage and not a bare spinner.
 *
 * The partner's own app is the reason this rule exists — its stream stuck at "97 %" is the most-cited
 * complaint in the reviews this case was researched from (`docs/research/user-feedback.md`).
 */
@Composable
private fun StepLabel(step: StreamStep) {
    Label(stringResource(step.label))
}

/** The wait, in words, over the video frame. */
@Composable
private fun Label(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium)
}

/**
 * The partner's own player page, and the way back to the screen that offered it (SPEC V9).
 *
 * The way back matters on iOS, where the surface hands the url to Safari and closes itself: without
 * it the user would return from the browser to a frame with nothing in it.
 */
@Composable
private fun WebFallbackSurface(url: String, onClose: () -> Unit) {
    VideoSurface { WebPlayerFallback(url, onClose, Modifier.fillMaxSize()) }
    TextButton(onClick = onClose) { Text(stringResource(Res.string.live_close_web_player)) }
}

/**
 * One named cause, and an action only when there is one worth offering (SPEC U6, V6).
 *
 * At most one *primary* action (U6): "Tentar novamente" is the button, and the web player — which
 * exists only when the session carried a `monitor_url` — is the quieter second way out.
 */
@Composable
private fun Explanation(
    message: StringResource,
    onRetry: (() -> Unit)? = null,
    onWebPlayer: (() -> Unit)? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text = stringResource(message), style = MaterialTheme.typography.bodyMedium)
        // Quota, capability and an offline camera have no retry on purpose: asking again cannot
        // change any of them, and a button that does nothing is worse than no button (SPEC V6).
        onRetry?.let { Button(onClick = it) { Text(stringResource(Res.string.live_retry)) } }
        onWebPlayer?.let { TextButton(onClick = it) { Text(stringResource(Res.string.live_open_web_player)) } }
    }
}

private val StreamStep.label: StringResource
    get() = when (this) {
        StreamStep.CheckingCapability -> Res.string.live_step_capability
        StreamStep.CreatingSession -> Res.string.live_step_session
        StreamStep.Connecting -> Res.string.live_step_connecting
    }

/** One friendly sentence per category, never the server's own words (SPEC U6). */
private val StreamError.message: StringResource
    get() = when (this) {
        StreamError.TokenRejected -> Res.string.live_error_rejected
        StreamError.TokenExpired -> Res.string.live_error_expired
        StreamError.Offline -> Res.string.live_error_offline
        StreamError.UnexpectedResponse -> Res.string.live_error_unexpected
        StreamError.Failed -> Res.string.live_error_failed
        StreamError.Playback -> Res.string.live_error_playback
    }

private const val SURFACE_RATIO = 16f / 9f
