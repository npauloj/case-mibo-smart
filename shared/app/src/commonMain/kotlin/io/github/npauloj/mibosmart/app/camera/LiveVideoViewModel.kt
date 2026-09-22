package io.github.npauloj.mibosmart.app.camera

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.npauloj.mibosmart.app.AppCoroutineScope
import io.github.npauloj.mibosmart.app.camera.platform.PlayerEvent
import io.github.npauloj.mibosmart.domain.camera.PlaybackFailure
import io.github.npauloj.mibosmart.domain.camera.PlaybackRecovery
import io.github.npauloj.mibosmart.domain.camera.PlaybackRetryPolicy
import io.github.npauloj.mibosmart.domain.camera.RetrySource
import io.github.npauloj.mibosmart.domain.camera.StreamError
import io.github.npauloj.mibosmart.domain.camera.StreamSession
import io.github.npauloj.mibosmart.domain.camera.StreamState
import io.github.npauloj.mibosmart.domain.device.Device
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
 *
 * Since V-02 this class also owns the **waiting**: no attempt here is unbounded. Every attempt has
 * [FIRST_FRAME_BUDGET] to draw something (SPEC U1), the ladder of `PlaybackRetryPolicy` bounds how
 * often the app tries by itself (SPEC V4, V5), and [CREATIONS_PER_VISIT] bounds what that costs the
 * account (ADR-006).
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

    /** The watchdog of SPEC U1, for the attempt currently on screen. Cancelled by the first frame. */
    private var firstFrameJob: Job? = null

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

    /**
     * The monitor page of the session this visit opened, remembered after the session is closed.
     *
     * Every failure ends the session (SPEC V8) and only then does the screen offer the page, so the
     * url has to outlive the session it came from — otherwise whether "Abrir no player web" appears
     * would depend on which failure got there first, not on whether there is a page (SPEC V9).
     */
    private var monitorPage: String? = null

    /** Attempts the ladder has already spent on this visit (SPEC V4). Reset only by the user. */
    private var retries = 0

    /**
     * Sessions the app may still create **by itself** on this visit (ADR-006).
     *
     * Three: the one that opens the screen plus the ladder's two. A foreground return spends from
     * the same allowance instead of resetting it, because a phone going in and out of a pocket must
     * not be able to buy itself a new budget every time (SPEC V8).
     */
    private var creationsLeft = CREATIONS_PER_VISIT

    /** The failure the web player was opened from, so closing it lands back on a screen with actions. */
    private var lastFailure: StreamState.Failed? = null

    /** Opening a camera. Entering the same one again — a recomposition, a rotation — costs nothing. */
    fun open(camera: Device) {
        if (this.camera == camera && streamJob != null) return
        this.camera = camera
        startVisit()
    }

    /**
     * The one action the error states offer; it is the user's choice, never a timer (E5).
     *
     * A tap starts the visit's allowance over. It has to: SPEC V4 says the screen after the third
     * failure offers "Tentar novamente", and a button that the ladder's own budget had already
     * spent would do nothing at all (SPEC V6). What ADR-006 bounds is what the app spends **on its
     * own** — nothing here retries without the user asking.
     */
    fun retry() {
        camera ?: return
        startVisit()
    }

    /**
     * SPEC V8: the app came back to the foreground on a screen it had torn down.
     *
     * Only a torn-down screen is resumed — a live one already has a session, and creating a second is
     * how a phone in a pocket burns the account's quota.
     */
    fun resume() {
        if (state.value == StreamState.Idle) create(Duration.ZERO, StreamState.Idle)
    }

    /** Leaving, or going to the background: detach the player and give the quota back (SPEC V8). */
    fun stop() {
        release(StreamState.Idle)
    }

    /** SPEC V9: the user asked for the partner's own page. Only [StreamState.Failed] offers it. */
    fun openWebPlayer() {
        val failed = state.value as? StreamState.Failed ?: return
        val url = failed.monitorUrl ?: return
        lastFailure = failed
        mutableState.value = StreamState.WebFallback(url)
    }

    /** Back from the web player to the failure that offered it, which still offers "Tentar novamente". */
    fun closeWebPlayer() {
        if (state.value !is StreamState.WebFallback) return
        mutableState.value = lastFailure ?: StreamState.Idle
    }

    /** What the player reports about the url it was handed (ADR-005). */
    fun onPlayerEvent(event: PlayerEvent) {
        val live = state.value as? StreamState.Live ?: return
        when (event) {
            PlayerEvent.FirstFrame -> {
                firstFrameJob?.cancel()
                mutableState.value = live.copy(firstFrame = true)
            }
            PlayerEvent.Ended -> recover(PlaybackFailure.Ended, live.session)
            PlayerEvent.NetworkError -> recover(PlaybackFailure.Dropped, live.session)
            PlayerEvent.DecodeError -> recover(PlaybackFailure.Undecodable, live.session)
        }
    }

    override fun onCleared() {
        stop()
    }

    /** A visit the user asked for: the ladder and the request allowance both start over. */
    private fun startVisit() {
        retries = 0
        creationsLeft = CREATIONS_PER_VISIT
        lastFailure = null
        monitorPage = null
        create(Duration.ZERO, StreamState.Idle)
    }

    /** SPEC V4, V5: what the app does about a stream that stopped playing. */
    private fun recover(failure: PlaybackFailure, session: StreamSession) {
        when (val recovery = PlaybackRetryPolicy.next(failure, retries)) {
            PlaybackRecovery.GiveUp -> fail()
            is PlaybackRecovery.Retry -> {
                retries = recovery.attempt
                val showing = StreamState.Reconnecting(recovery.attempt, PlaybackRetryPolicy.MAX_ATTEMPTS)
                when (recovery.source) {
                    RetrySource.SameSession -> rePrepare(session, recovery.after, showing)
                    RetrySource.NewSession -> create(recovery.after, showing)
                }
            }
        }
    }

    /**
     * An attempt that pays: end whatever session is open, wait, and create a new one.
     *
     * The allowance is checked *before* the request, not after, so a visit that has spent it says
     * "Não foi possível carregar o vídeo" instead of quietly showing a wait nobody will end.
     */
    private fun create(after: Duration, showing: StreamState) {
        val camera = camera ?: return
        release(showing)
        if (creationsLeft == 0) {
            mutableState.value = StreamState.Failed(StreamError.Playback, monitorPage)
            return
        }
        creationsLeft--
        val mine = attempt
        streamJob = viewModelScope.launch {
            delay(after)
            watchLiveVideo(camera) { publish(mine, it) }
        }
        armFirstFrame(mine)
    }

    /**
     * The ladder's free attempt (SPEC V4): the session stays open and only the player is rebuilt.
     *
     * Rebuilding is what leaving [StreamState.Live] does — the surface is `remember`ed by url, so the
     * round trip through `Reconnecting` releases the decoder and prepares the same url again. It
     * costs no partner request, which is why it is the attempt the ladder makes first.
     */
    private fun rePrepare(session: StreamSession, after: Duration, showing: StreamState) {
        firstFrameJob?.cancel()
        streamJob?.cancel()
        val mine = ++attempt
        mutableState.value = showing
        streamJob = viewModelScope.launch {
            delay(after)
            publish(mine, StreamState.Live(session))
        }
        armFirstFrame(mine)
    }

    /**
     * SPEC U1: an attempt that has drawn nothing within [FIRST_FRAME_BUDGET] is over.
     *
     * This is the rule the whole slice exists for — the partner's own app is the one that "trava em
     * 97 %", and the honest answer to a stream that never starts is to say so and offer the web
     * player, not to keep a spinner turning on a session that is still being billed.
     */
    private fun armFirstFrame(mine: Int) {
        firstFrameJob?.cancel()
        firstFrameJob = viewModelScope.launch {
            delay(FIRST_FRAME_BUDGET)
            if (mine != attempt) return@launch
            if (state.value.isWaitingForPicture) fail()
        }
    }

    /** Nothing more will be tried: end the session and offer what there is to offer (V4, V5, V9). */
    private fun fail() {
        release(StreamState.Failed(StreamError.Playback, monitorPage))
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
        if (next is StreamState.Live) {
            openSession = next.session
            monitorPage = next.session.monitorUrl
        }
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
        firstFrameJob?.cancel()
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

    private companion object {

        /** `[ASSUMED]` (SPEC U1): tuned against the real camera, enforced here on a virtual clock. */
        val FIRST_FRAME_BUDGET = 20.seconds

        /** ADR-006: the one that opens the screen, plus the ladder's two. Nothing automatic beyond. */
        const val CREATIONS_PER_VISIT = 3
    }
}

/** Every state in which the user is looking at a frame that has not arrived yet (SPEC U1). */
private val StreamState.isWaitingForPicture: Boolean
    get() = this is StreamState.Creating ||
        this is StreamState.Reconnecting ||
        (this is StreamState.Live && !firstFrame)
