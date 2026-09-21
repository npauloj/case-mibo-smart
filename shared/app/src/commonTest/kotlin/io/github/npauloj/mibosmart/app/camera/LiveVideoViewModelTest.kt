package io.github.npauloj.mibosmart.app.camera

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.npauloj.mibosmart.app.AppCoroutineScope
import io.github.npauloj.mibosmart.app.camera.platform.PlayerEvent
import io.github.npauloj.mibosmart.domain.camera.PlaybackRetryPolicy
import io.github.npauloj.mibosmart.domain.camera.StreamError
import io.github.npauloj.mibosmart.domain.camera.StreamState
import io.github.npauloj.mibosmart.domain.camera.StreamStep
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * SPEC V3, V4, V5, V8, V9 and U1 — the things that decide whether this feature costs the account
 * money, and whether a user ever gets an answer.
 *
 * The screen `StateFlow` is read as `state.value` after the scheduler has run on a
 * `StandardTestDispatcher`; Turbine is for one-shot event flows only (`CLAUDE.md`).
 *
 * Which scheduler call matters here. Since V-02 every attempt arms U1's 20-second first-frame
 * watchdog, so `advanceUntilIdle()` does not mean "let the pending work run" any more — it means
 * "let the wait run out". A test that only wants the fakes to answer calls `runCurrent()`; a test
 * about a delay names the delay with `advanceTimeBy`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LiveVideoViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** SPEC V3: every wait has a name, and the last one comes off when the first frame arrives. */
    @Test
    fun stateSequenceOnHappyPath() = runTest(dispatcher) {
        val capability = CompletableDeferred<Unit>()
        val creation = CompletableDeferred<Unit>()
        val partner = FakeStreamingRepository(
            onCapability = { capability.await() },
            onOpen = { creation.await() },
        )
        val viewModel = viewModelWith(partner)

        viewModel.open(CameraSamples.camera())
        runCurrent()
        assertEquals(StreamState.Creating(StreamStep.CheckingCapability), viewModel.state.value)

        capability.complete(Unit)
        runCurrent()
        assertEquals(StreamState.Creating(StreamStep.CreatingSession), viewModel.state.value)

        creation.complete(Unit)
        runCurrent()
        assertEquals(
            StreamState.Live(CameraSamples.SESSION, firstFrame = false),
            viewModel.state.value,
            "the url must be on screen before the player has drawn anything (SPEC V2)",
        )

        viewModel.onPlayerEvent(PlayerEvent.FirstFrame)
        assertEquals(StreamState.Live(CameraSamples.SESSION, firstFrame = true), viewModel.state.value)
    }

    /** SPEC V8: leaving detaches the player and gives the quota back. */
    @Test
    fun stopEndsSessionAndDetachesPlayer() = runTest(dispatcher) {
        val partner = FakeStreamingRepository()
        val viewModel = viewModelWith(partner)
        viewModel.open(CameraSamples.camera())
        runCurrent()
        assertIs<StreamState.Live>(viewModel.state.value)

        viewModel.stop()
        advanceUntilIdle()

        assertEquals(StreamState.Idle, viewModel.state.value, "the player is still attached to a url")
        assertEquals(listOf(CameraSamples.SESSION.id), partner.closed)
    }

    /**
     * SPEC V8, the expensive case: the user leaves while the partner is still minting the session.
     *
     * The session is created anyway — the app cannot un-ask — so the only way not to leak it is to
     * learn its id and close it. A screen that gave up at the cancellation would leave a stream
     * running against the account until the partner's own cap ended it.
     */
    @Test
    fun cancellationMidCreationStillEndsSession() = runTest(dispatcher) {
        val creation = CompletableDeferred<Unit>()
        val partner = FakeStreamingRepository(onOpen = { creation.await() })
        val viewModel = viewModelWith(partner)
        viewModel.open(CameraSamples.camera())
        runCurrent()
        assertEquals(StreamState.Creating(StreamStep.CreatingSession), viewModel.state.value)

        viewModel.stop()
        creation.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf(CameraSamples.SESSION.id), partner.closed, "the session was never closed")
        assertEquals(
            StreamState.Idle,
            viewModel.state.value,
            "a screen the user already left came back to life on a late answer",
        )
    }

    /**
     * SPEC V8: the teardown outlives the ViewModel.
     *
     * `ViewModelStore.clear()` is exactly what the framework does when the screen is gone: it cancels
     * `viewModelScope` and then calls `onCleared`. A teardown launched on that scope would never send
     * its request, and nothing but this assertion would say so.
     */
    @Test
    fun teardownUsesAppScopeNotViewModelScope() = runTest(dispatcher) {
        val partner = FakeStreamingRepository()
        val store = ViewModelStore()
        val viewModel = ViewModelProvider.create(
            store,
            viewModelFactory { initializer { viewModelWith(partner) } },
        )[LiveVideoViewModel::class]
        viewModel.open(CameraSamples.camera())
        runCurrent()
        assertIs<StreamState.Live>(viewModel.state.value)

        store.clear()
        advanceUntilIdle()

        assertEquals(
            listOf(CameraSamples.SESSION.id),
            partner.closed,
            "`encerrar-sessao` never left the device: the teardown ran on a scope already cancelled",
        )
    }

    /** SPEC V8: coming back to a screen that was torn down creates one new session, not a second one. */
    @Test
    fun resumeAfterStopCreatesOneNewSession() = runTest(dispatcher) {
        val partner = FakeStreamingRepository()
        val viewModel = viewModelWith(partner)
        viewModel.open(CameraSamples.camera())
        runCurrent()
        viewModel.stop()
        advanceUntilIdle()

        viewModel.resume()
        viewModel.resume()
        runCurrent()

        assertIs<StreamState.Live>(viewModel.state.value)
        assertEquals(2, partner.opened.size, "resuming a live screen created a second session")
    }

    /**
     * SPEC V4, the ladder's free attempt: a drop re-prepares the url the app already has.
     *
     * The session stays open on purpose — it is the same session, and only consumed bandwidth is
     * billed — so this attempt costs the account nothing at all. Ending it here and creating another
     * would spend a request on the most common failure there is, a radio reconnecting.
     */
    @Test
    fun aDropRePreparesTheSameSessionBeforePayingForAnother() = runTest(dispatcher) {
        val partner = FakeStreamingRepository()
        val viewModel = viewModelWith(partner)
        viewModel.open(CameraSamples.camera())
        runCurrent()

        viewModel.onPlayerEvent(PlayerEvent.NetworkError)
        runCurrent()
        assertEquals(
            StreamState.Reconnecting(attempt = 1, total = PlaybackRetryPolicy.MAX_ATTEMPTS),
            viewModel.state.value,
            "the attempt must be on screen, in words, while the app waits (SPEC V3)",
        )

        advanceTimeBy(2.seconds)
        runCurrent()

        assertEquals(StreamState.Live(CameraSamples.SESSION, firstFrame = false), viewModel.state.value)
        assertEquals(1, partner.opened.size, "the first retry paid for a session it did not need")
        assertEquals(emptyList(), partner.closed, "the session it was about to reuse was closed")
    }

    /**
     * SPEC V4 and ADR-006: a full ladder costs three creations per visit, and not one more.
     *
     * The count is the whole assertion. The account has ~300 requests for the entire case, and a
     * retry loop is the classic way to spend them without anybody noticing.
     */
    @Test
    fun ladderCreatesAtMostTwoExtraSessions() = runTest(dispatcher) {
        val partner = FakeStreamingRepository()
        val viewModel = viewModelWith(partner)
        viewModel.open(CameraSamples.camera())
        runCurrent()

        // Three drops: re-prepare (free), new session (3 s), new session (7 s).
        listOf(2.seconds, 4.seconds, 8.seconds).forEach { wait ->
            assertIs<StreamState.Live>(viewModel.state.value, "the ladder stopped before its last rung")
            viewModel.onPlayerEvent(PlayerEvent.NetworkError)
            advanceTimeBy(wait)
            runCurrent()
        }
        viewModel.onPlayerEvent(PlayerEvent.NetworkError)
        advanceUntilIdle()

        assertEquals(
            StreamState.Failed(StreamError.Playback, CameraSamples.SESSION.monitorUrl),
            viewModel.state.value,
            "after the third attempt the screen must name the failure and offer the web player",
        )
        assertEquals(3, partner.opened.size, "the ladder spent more than the two creations it may")
    }

    /** SPEC V5: bytes that could not be decoded are not decoded better on the second try. */
    @Test
    fun aDecodeErrorGoesStraightToTheFallbackOffer() = runTest(dispatcher) {
        val partner = FakeStreamingRepository()
        val viewModel = viewModelWith(partner)
        viewModel.open(CameraSamples.camera())
        runCurrent()

        viewModel.onPlayerEvent(PlayerEvent.DecodeError)
        advanceUntilIdle()

        assertEquals(
            StreamState.Failed(StreamError.Playback, CameraSamples.SESSION.monitorUrl),
            viewModel.state.value,
        )
        assertEquals(1, partner.opened.size, "a decode error was retried")
        assertEquals(listOf(CameraSamples.SESSION.id), partner.closed, "an unplayable stream kept billing")
    }

    /**
     * SPEC U1: the wait is bounded, and what ends it is a clock, not the user's patience.
     *
     * This is the "trava em 97 %" of the partner's own reviews, answered: 20 seconds without a frame
     * is a failure with a name and two ways out, not a spinner that turns until the app is killed.
     */
    @Test
    fun firstFrameTimeoutBecomesFailed() = runTest(dispatcher) {
        val partner = FakeStreamingRepository()
        val viewModel = viewModelWith(partner)
        viewModel.open(CameraSamples.camera())
        runCurrent()
        assertIs<StreamState.Live>(viewModel.state.value)

        advanceTimeBy(21.seconds)
        runCurrent()

        assertEquals(
            StreamState.Failed(StreamError.Playback, CameraSamples.SESSION.monitorUrl),
            viewModel.state.value,
            "a stream that never drew a frame kept the screen waiting",
        )
        assertEquals(listOf(CameraSamples.SESSION.id), partner.closed, "the silent session kept billing")
    }

    /** The frame arrives in time: the watchdog is off and nothing fails behind the user's back. */
    @Test
    fun aFirstFrameDisarmsTheTimeout() = runTest(dispatcher) {
        val partner = FakeStreamingRepository()
        val viewModel = viewModelWith(partner)
        viewModel.open(CameraSamples.camera())
        runCurrent()

        viewModel.onPlayerEvent(PlayerEvent.FirstFrame)
        advanceTimeBy(60.seconds)
        runCurrent()

        assertEquals(StreamState.Live(CameraSamples.SESSION, firstFrame = true), viewModel.state.value)
    }

    /**
     * SPEC V9 and ADR-005: a session without a `monitor_url` has nothing to fall back to, and the
     * screen says so instead of offering a button that would open nothing.
     *
     * This closes the loop ADR-005 left open: on iOS a null `monitor_url` is reported as a decode
     * error at once, and V5 sends a decode error to a fallback that would have no url to load.
     */
    @Test
    fun failedWithoutMonitorUrlOffersRetryOnly() = runTest(dispatcher) {
        val partner = FakeStreamingRepository(session = CameraSamples.SESSION.copy(monitorUrl = null))
        val viewModel = viewModelWith(partner)
        viewModel.open(CameraSamples.camera())
        runCurrent()

        viewModel.onPlayerEvent(PlayerEvent.DecodeError)
        advanceUntilIdle()

        assertEquals(StreamState.Failed(StreamError.Playback, monitorUrl = null), viewModel.state.value)

        viewModel.openWebPlayer()
        runCurrent()
        assertIs<StreamState.Failed>(
            viewModel.state.value,
            "there is no page to open, so asking for one must change nothing",
        )
    }

    /** SPEC V9: the web player is a detour, and the way back still offers "Tentar novamente". */
    @Test
    fun theWebPlayerIsOpenedAndClosedOnTheSameScreen() = runTest(dispatcher) {
        val partner = FakeStreamingRepository()
        val viewModel = viewModelWith(partner)
        viewModel.open(CameraSamples.camera())
        runCurrent()
        viewModel.onPlayerEvent(PlayerEvent.DecodeError)
        advanceUntilIdle()

        viewModel.openWebPlayer()
        assertEquals(StreamState.WebFallback(CameraSamples.SESSION.monitorUrl!!), viewModel.state.value)

        viewModel.closeWebPlayer()
        assertEquals(
            StreamState.Failed(StreamError.Playback, CameraSamples.SESSION.monitorUrl),
            viewModel.state.value,
        )
    }

    /**
     * SPEC V8 and ADR-006: coming back to the foreground does not buy a new allowance.
     *
     * A phone going in and out of a pocket is not a user asking for anything, and it is the one way
     * an app can spend a shared account's quota all night without a single tap.
     */
    @Test
    fun aForegroundReturnSpendsFromTheSameAllowance() = runTest(dispatcher) {
        val partner = FakeStreamingRepository()
        val viewModel = viewModelWith(partner)
        viewModel.open(CameraSamples.camera())
        runCurrent()
        listOf(2.seconds, 4.seconds, 8.seconds).forEach { wait ->
            viewModel.onPlayerEvent(PlayerEvent.NetworkError)
            advanceTimeBy(wait)
            runCurrent()
        }
        viewModel.stop()
        advanceUntilIdle()

        viewModel.resume()
        advanceUntilIdle()

        assertEquals(
            StreamState.Failed(StreamError.Playback, CameraSamples.SESSION.monitorUrl),
            viewModel.state.value,
            "the visit's allowance was spent, so the screen must say so instead of waiting",
        )
        assertEquals(3, partner.opened.size, "the foreground return created a fourth session")
    }

    /**
     * SPEC V4 and V6: the button the failed screen offers has to work.
     *
     * The ladder's allowance is what ADR-006 bounds — what the app spends *by itself*. A user who
     * taps "Tentar novamente" after the third failure is not a loop, and a button that the ladder's
     * own budget had already spent would be exactly the dead action SPEC V6 refuses.
     */
    @Test
    fun retryAfterAFullLadderStartsTheAllowanceOver() = runTest(dispatcher) {
        val partner = FakeStreamingRepository()
        val viewModel = viewModelWith(partner)
        viewModel.open(CameraSamples.camera())
        runCurrent()
        listOf(2.seconds, 4.seconds, 8.seconds).forEach { wait ->
            viewModel.onPlayerEvent(PlayerEvent.NetworkError)
            advanceTimeBy(wait)
            runCurrent()
        }
        viewModel.onPlayerEvent(PlayerEvent.NetworkError)
        advanceUntilIdle()
        assertIs<StreamState.Failed>(viewModel.state.value)

        viewModel.retry()
        runCurrent()

        assertIs<StreamState.Live>(viewModel.state.value, "the retry the screen offers did nothing")
        assertEquals(4, partner.opened.size)
    }

    private fun viewModelWith(partner: FakeStreamingRepository) = LiveVideoViewModel(
        watchLiveVideo = WatchLiveVideo(partner, LiveVideoSwitch(isOn = true)),
        endStreamSession = EndStreamSession(partner),
        // The real one is a `SupervisorJob` on `Dispatchers.Default`; here it is the same shape on a
        // scheduler the test controls, so "the teardown ran" is an assertion and not a wait.
        appScope = AppCoroutineScope(SupervisorJob() + dispatcher),
    )
}
