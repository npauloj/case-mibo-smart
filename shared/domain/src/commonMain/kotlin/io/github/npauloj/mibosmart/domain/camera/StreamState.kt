package io.github.npauloj.mibosmart.domain.camera

/**
 * Where one camera's live stream is right now — the closed set of things the video screen can
 * show (ADR-002: one exhaustive `when`, and a state added later stops the screen compiling
 * until someone decides what it looks like).
 */
sealed interface StreamState {

    /** Nothing is running: before the first attempt, and after teardown detached the player (V8). */
    data object Idle : StreamState

    /** V3: the wait has a name. Never a percentage, never an unbounded spinner. */
    data class Creating(val step: StreamStep) : StreamState

    /**
     * V2: [session] carries the url the player is attached to, published in the same coroutine
     * that created it so playback starts inside the 15-second window.
     * @property firstFrame false until the player says it drew something — the screen keeps the
     * "Conectando…" step over the surface until then, which is the difference between a black
     * rectangle and a stream that is still arriving.
     */
    data class Live(val session: StreamSession, val firstFrame: Boolean = false) : StreamState

    /**
     * V4: the app is trying again by itself, and says which attempt this is ("Reconectando
     * (2/3)…").
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
     * created, the retry ladder ran out (V4), the bytes could not be decoded (V5), or no first
     * frame arrived inside U1's budget.
     * @property monitorUrl the partner's own player page for the session that failed, when
     * there was one.
     */
    data class Failed(val error: StreamError, val monitorUrl: String? = null) : StreamState

    /** V9: the user asked for the partner's own player page, and it is on screen now. */
    data class WebFallback(val url: String) : StreamState
}

/** The step [StreamState.Creating] is on, as words the screen can show (SPEC V3). */
enum class StreamStep { CheckingCapability, CreatingSession, Connecting }

/** Why there is no picture, one user-facing sentence each (SPEC U6, E2, ADR-012). */
enum class StreamError { TokenRejected, TokenExpired, Offline, UnexpectedResponse, Failed, Playback }
