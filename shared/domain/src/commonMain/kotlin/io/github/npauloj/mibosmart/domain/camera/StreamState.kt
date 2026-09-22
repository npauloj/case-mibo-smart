package io.github.npauloj.mibosmart.domain.camera

/**
 * Where one camera's live stream is right now — the closed set of things the video screen can show
 * (ADR-002: one exhaustive `when`, and a state added later stops the screen compiling until someone
 * decides what it looks like).
 *
 * It lives in the domain rather than beside the ViewModel because it is the *stream's* state, not the
 * screen's decoration: ADR-005 names it, V-01b renders the same set on iOS, and V-02 adds
 * `Reconnecting` to it without touching either screen's vocabulary.
 */
sealed interface StreamState {

    /** Nothing is running: before the first attempt, and after teardown detached the player (V8). */
    data object Idle : StreamState

    /** V3: the wait has a name. Never a percentage, never an unbounded spinner. */
    data class Creating(val step: StreamStep) : StreamState

    /**
     * V2: [session] carries the url the player is attached to, published in the same coroutine that
     * created it so playback starts inside the 15-second window.
     *
     * @property firstFrame false until the player says it drew something — the screen keeps the
     *   "Conectando…" step over the surface until then, which is the difference between a black
     *   rectangle and a stream that is still arriving.
     */
    data class Live(val session: StreamSession, val firstFrame: Boolean = false) : StreamState

    /**
     * V4: the app is trying again by itself, and says which attempt this is ("Reconectando (2/3)…").
     *
     * The number is in the state rather than in the screen because it is the *rule's* number: the
     * ladder of [PlaybackRetryPolicy] decides how many attempts there are, and a screen that counted
     * on its own could disagree with the policy that is spending the account's quota (SPEC V3, U1).
     */
    data class Reconnecting(val attempt: Int, val total: Int) : StreamState

    /** V1: the camera does not announce `RTSV`, or the live-video kill switch is off. */
    data object NoLiveCapability : StreamState

    /** V6: the account's streaming quota is gone. There is nothing to retry. */
    data object QuotaExceeded : StreamState

    /** V7: the camera is offline. Nothing was created and nothing will be. */
    data object CameraOffline : StreamState

    /**
     * Nothing can be played, with a cause the user can read (SPEC U6): the session could not be
     * created, the retry ladder ran out (V4), the bytes could not be decoded (V5), or no first frame
     * arrived inside U1's budget.
     *
     * @property monitorUrl the partner's own player page for the session that failed, when there was
     *   one. It is what decides whether the screen offers "Abrir no player web" at all: `monitor_url`
     *   is nullable and unverified (ADR-005, ADR-006), and an action that would open nothing is worse
     *   than no action (SPEC V6, V9).
     */
    data class Failed(val error: StreamError, val monitorUrl: String? = null) : StreamState

    /** V9: the user asked for the partner's own player page, and it is on screen now. */
    data class WebFallback(val url: String) : StreamState
}

/** The step [StreamState.Creating] is on, as words the screen can show (SPEC V3). */
enum class StreamStep { CheckingCapability, CreatingSession, Connecting }

/**
 * Why there is no picture, one user-facing sentence each (SPEC U6, E2, ADR-012).
 *
 * [Playback] is the only one that does not come from the partner's answer: it is what the app says
 * when the session was created and the video still never played (SPEC V4, V5, U1).
 */
enum class StreamError { TokenRejected, TokenExpired, Offline, UnexpectedResponse, Failed, Playback }
