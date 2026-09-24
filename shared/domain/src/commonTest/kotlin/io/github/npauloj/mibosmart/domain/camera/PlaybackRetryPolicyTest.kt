package io.github.npauloj.mibosmart.domain.camera

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.seconds

/**
 * SPEC V4 and V5 — how long the app is allowed to keep trying, and what that costs the account.
 */
class PlaybackRetryPolicyTest {

    /** SPEC V4: three attempts, growing delays, the free one first. */
    @Test
    fun networkLadderRePreparesThenRecreates() {
        val first = PlaybackRetryPolicy.next(PlaybackFailure.Dropped, attemptsMade = 0)
        val second = PlaybackRetryPolicy.next(PlaybackFailure.Dropped, attemptsMade = 1)
        val third = PlaybackRetryPolicy.next(PlaybackFailure.Dropped, attemptsMade = 2)

        assertEquals(
            PlaybackRecovery.Retry(attempt = 1, after = 1.seconds, source = RetrySource.SameSession),
            first,
            "the first attempt must reuse the session the app already paid for",
        )
        assertEquals(
            PlaybackRecovery.Retry(attempt = 2, after = 3.seconds, source = RetrySource.NewSession),
            second,
        )
        assertEquals(
            PlaybackRecovery.Retry(attempt = 3, after = 7.seconds, source = RetrySource.NewSession),
            third,
        )
    }

    /** The end of a stream is the same ladder: the `stream_gb` cap looks exactly like a drop. */
    @Test
    fun endOfStreamClimbsTheSameLadder() {
        assertEquals(
            PlaybackRetryPolicy.next(PlaybackFailure.Dropped, attemptsMade = 0),
            PlaybackRetryPolicy.next(PlaybackFailure.Ended, attemptsMade = 0),
        )
    }

    /** SPEC V4: after the third attempt the app stops trying by itself and says so. */
    @Test
    fun theLadderEndsAfterThreeAttempts() {
        assertEquals(
            PlaybackRecovery.GiveUp,
            PlaybackRetryPolicy.next(PlaybackFailure.Dropped, attemptsMade = PlaybackRetryPolicy.MAX_ATTEMPTS),
        )
    }

    /** SPEC V5: a decode error never climbs the ladder. */
    @Test
    fun decodeErrorNoRetry() {
        (0 until PlaybackRetryPolicy.MAX_ATTEMPTS).forEach { attemptsMade ->
            assertEquals(
                PlaybackRecovery.GiveUp,
                PlaybackRetryPolicy.next(PlaybackFailure.Undecodable, attemptsMade),
                "a decode error was retried after $attemptsMade attempts",
            )
        }
    }

    /** The ladder's delays grow: an immediate retry on a network that is still down fails faster. */
    @Test
    fun everyDelayIsLongerThanTheOneBefore() {
        val delays = (0 until PlaybackRetryPolicy.MAX_ATTEMPTS).map {
            assertIs<PlaybackRecovery.Retry>(PlaybackRetryPolicy.next(PlaybackFailure.Dropped, it)).after
        }

        assertEquals(delays.sorted(), delays, "the ladder must back off, not hammer")
    }
}
