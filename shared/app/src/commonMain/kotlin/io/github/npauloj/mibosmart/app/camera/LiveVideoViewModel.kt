package io.github.npauloj.mibosmart.app.camera

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.npauloj.mibosmart.app.AppCoroutineScope
import io.github.npauloj.mibosmart.app.camera.platform.PlayerEvent
import io.github.npauloj.mibosmart.domain.camera.StreamSession
import io.github.npauloj.mibosmart.domain.camera.StreamState
import io.github.npauloj.mibosmart.domain.device.Device
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The live-video screen: one state, and a session this class is responsible for closing (ADR-003,
 * ADR-005 — the ViewModel owns the session, the composable owns the player).
 *
 * Its intents are plain functions rather than the suspend functions the other screens expose, and
 * that is the one thing worth knowing about it: the stream runs in a job this class must be able to
 * **cancel** from `stop()`, so it cannot be a coroutine the caller awaits. Tests drive it with
 * `advanceUntilIdle()` on a `StandardTestDispatcher` and read `state.value` (SPEC V3).
 */
class LiveVideoViewModel(
    private val watchLiveVideo: WatchLiveVideo,
    private val endStreamSession: EndStreamSession,
    private val appScope: AppCoroutineScope,
) : ViewModel() {

    private val mutableState = MutableStateFlow<StreamState>(StreamState.Idle)
    val state: StateFlow<StreamState> = mutableState.asStateFlow()

    private var camera: Device? = null
    private var streamJob: Job? = null

    /**
     * Which attempt the state belongs to, bumped by every [release].
     *
     * It exists because [WatchLiveVideo] finishes creating a session even after the screen is gone —
     * it has to, or the session would be unclosable — and that late answer must be able to register
     * the session without putting a dead screen back on `Live`.
     */
    private var attempt = 0

    /**
     * The session the partner has open right now, or null.
     *
     * Written by [publish] the instant the session exists and read by [release] on the way out. It is
     * the app's only handle on the account's quota: lose it and the session runs until the partner's
     * own cap ends it (SPEC V8).
     */
    private var openSession: StreamSession? = null

    /** Opening a camera. Entering the same one again — a recomposition, a rotation — costs nothing. */
    fun open(camera: Device) {
        if (this.camera == camera && streamJob != null) return
        this.camera = camera
        watch(camera)
    }

    /** The one action the error and expired states offer; it is the user's choice, never a timer (E5). */
    fun retry() {
        watch(camera ?: return)
    }

    /**
     * SPEC V8: the app came back to the foreground on a screen it had torn down.
     *
     * Only a torn-down screen is resumed — a live one already has a session, and creating a second is
     * how a phone in a pocket burns the account's quota.
     */
    fun resume() {
        if (state.value == StreamState.Idle) watch(camera ?: return)
    }

    /** Leaving, or going to the background: detach the player and give the quota back (SPEC V8). */
    fun stop() {
        release(StreamState.Idle)
    }

    /** What the player reports about the url it was handed (ADR-005). */
    fun onPlayerEvent(event: PlayerEvent) {
        val live = state.value as? StreamState.Live ?: return
        when (event) {
            PlayerEvent.FirstFrame -> mutableState.value = live.copy(firstFrame = true)
            // The stream stopped, whatever the reason. Telling those reasons apart to retry one and
            // offer the web player for another is V-02's; here every one of them ends the session,
            // because a stream that is not playing must not keep spending quota.
            PlayerEvent.Ended, PlayerEvent.NetworkError, PlayerEvent.DecodeError ->
                release(StreamState.Expired)
        }
    }

    override fun onCleared() {
        stop()
    }

    private fun watch(camera: Device) {
        release(StreamState.Idle)
        val mine = attempt
        streamJob = viewModelScope.launch { watchLiveVideo(camera) { publish(mine, it) } }
    }

    /**
     * The one write to the state, and the one place the session becomes known.
     *
     * Both happen in the same non-suspending step, which is what SPEC V2 asks for: the url reaches
     * the player inside its 15-second window, and no cancellation can slip between the session
     * existing and this class knowing about it.
     *
     * The session is recorded even when the attempt is stale, because a session the app forgets is a
     * session it cannot close; only the *screen* is spared the late news (SPEC V8).
     */
    private fun publish(attempt: Int, next: StreamState) {
        if (next is StreamState.Live) openSession = next.session
        if (attempt != this.attempt) return
        mutableState.value = next
    }

    /**
     * Detach the player, stop the stream job, and close whatever session is open — from [appScope],
     * because `viewModelScope` is already cancelled when this runs from [onCleared] (SPEC V8).
     *
     * The `join` is not politeness: a session being created right now is registered by [publish] only
     * when the creation returns, so deciding there is nothing to close before then would leak exactly
     * the session the user paid for by leaving mid-creation.
     */
    private fun release(next: StreamState) {
        val job = streamJob
        streamJob = null
        attempt++
        job?.cancel()
        mutableState.value = next
        appScope.launch {
            job?.join()
            val session = openSession ?: return@launch
            openSession = null
            endStreamSession(session.id)
        }
    }
}
