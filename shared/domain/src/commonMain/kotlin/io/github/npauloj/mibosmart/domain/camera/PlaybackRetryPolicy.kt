package io.github.npauloj.mibosmart.domain.camera

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Why a stream stopped playing, in the domain's words (SPEC V4, V5). */
enum class PlaybackFailure {

    /** The stream dropped: the network went away, or the partner closed the connection. */
    Dropped,

    /** The stream reached its end, usually the `stream_gb` the session was created with. */
    Ended,

    /** The bytes arrived and could not be played. Trying again plays the same bytes (SPEC V5). */
    Undecodable,
}

/** Where the next attempt's url comes from — the difference between a free retry and a paid one. */
enum class RetrySource {

    /** The session the app already has. Costs no partner request at all. */
    SameSession,

    /** A new `criar-fluxo-video`, which spends one request and some streaming quota (ADR-006). */
    NewSession,
}

/** What to do after a [PlaybackFailure] (SPEC V4, V5, U1). */
sealed interface PlaybackRecovery {

    /**
     * Try again, which is [attempt] of [PlaybackRetryPolicy.MAX_ATTEMPTS] — the number the
     * overlay shows in words ("Reconectando (2/3)…", SPEC V3).
     * @property after how long to wait first.
     */
    data class Retry(val attempt: Int, val after: Duration, val source: RetrySource) : PlaybackRecovery

    /** Stop. The screen says so and offers the web player where there is one (SPEC V4, V5, V9). */
    data object GiveUp : PlaybackRecovery
}

/** How long the app is allowed to keep trying by itself (SPEC V4, V5, U1). */
object PlaybackRetryPolicy {

    /** SPEC V4: "retry up to 3 times". The screen counts the attempt against this number. */
    const val MAX_ATTEMPTS = 3

    /** What to do after [failure], given the [attemptsMade] this visit has already spent. */
    fun next(failure: PlaybackFailure, attemptsMade: Int): PlaybackRecovery {
        if (failure == PlaybackFailure.Undecodable) return PlaybackRecovery.GiveUp
        if (attemptsMade >= MAX_ATTEMPTS) return PlaybackRecovery.GiveUp
        return PlaybackRecovery.Retry(
            attempt = attemptsMade + 1,
            after = DELAYS[attemptsMade],
            source = if (attemptsMade == 0) RetrySource.SameSession else RetrySource.NewSession,
        )
    }

    /** `[ASSUMED]` (SPEC V4): tuned against the real camera, not measured yet. */
    private val DELAYS = listOf(1.seconds, 3.seconds, 7.seconds)
}
