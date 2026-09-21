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

    /** V1: the camera does not announce `RTSV`, or the live-video kill switch is off. */
    data object NoLiveCapability : StreamState

    /** V6: the account's streaming quota is gone. There is nothing to retry. */
    data object QuotaExceeded : StreamState

    /** V7: the camera is offline. Nothing was created and nothing will be. */
    data object CameraOffline : StreamState

    /** The stream stopped playing. The retry policy and the web fallback are V-02's. */
    data object Expired : StreamState

    /** The session could not be created, with a cause the user can read (SPEC U6). */
    data class Failed(val error: StreamError) : StreamState
}

/** The step [StreamState.Creating] is on, as words the screen can show (SPEC V3). */
enum class StreamStep { CheckingCapability, CreatingSession, Connecting }

/** Why creating a session failed, one user-facing sentence each (SPEC U6, E2, ADR-012). */
enum class StreamError { TokenRejected, TokenExpired, Offline, UnexpectedResponse, Failed }
