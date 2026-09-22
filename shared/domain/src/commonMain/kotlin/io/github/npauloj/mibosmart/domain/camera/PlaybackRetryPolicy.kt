package io.github.npauloj.mibosmart.domain.camera

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Why a stream stopped playing, in the domain's words (SPEC V4, V5).
 *
 * The player surface has its own vocabulary — `PlayerEvent` in `:shared:app`, which knows about
 * Media3 and WebKit — and it stays there: the rule that decides whether a failure is worth another
 * attempt is a business rule about the account's quota, not about a decoder (ADR-001, ADR-005).
 */
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
     * Try again, which is [attempt] of [PlaybackRetryPolicy.MAX_ATTEMPTS] — the number the overlay
     * shows in words ("Reconectando (2/3)…", SPEC V3).
     *
     * @property after how long to wait first. The wait is the point: an immediate retry on a network
     *   that is still down burns quota to fail twice as fast.
     */
    data class Retry(val attempt: Int, val after: Duration, val source: RetrySource) : PlaybackRecovery

    /** Stop. The screen says so and offers the web player where there is one (SPEC V4, V5, V9). */
    data object GiveUp : PlaybackRecovery
}

/**
 * How long the app is allowed to keep trying by itself (SPEC V4, V5, U1).
 *
 * The loudest complaint in the partner's reviews is a stream that hangs with no named error
 * (`docs/research/user-feedback.md`, cluster 1), and the second cost of that bug is the quota an
 * app spends retrying something that cannot work. So the ladder is short, it grows, and it stops:
 * three attempts, 1 s / 3 s / 7 s, and only the last two pay for a new session.
 *
 * It is a pure function of the failure and of how many attempts were already made, which is what
 * lets the whole ladder be asserted on a virtual clock without a player, a socket or a camera.
 */
object PlaybackRetryPolicy {

    /** SPEC V4: "retry up to 3 times". The screen counts the attempt against this number. */
    const val MAX_ATTEMPTS = 3

    /**
     * What to do after [failure], given the [attemptsMade] this visit has already spent.
     *
     * The first attempt re-prepares the url the app already has: a drop a second long is usually the
     * radio reconnecting, and a session that is still valid costs nothing to reuse. Only when that
     * fails does the app pay for a new one — the url expires 15 s after the partner minted it
     * (SPEC V2), so past the first attempt reusing it would mostly be a request spent on a dead url.
     */
    fun next(failure: PlaybackFailure, attemptsMade: Int): PlaybackRecovery {
        // SPEC V5: the same bytes decoded three times are still the same bytes. The user gets the
        // web player instead, which is a different decoder on the same session.
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
