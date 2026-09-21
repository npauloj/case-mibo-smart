package io.github.npauloj.mibosmart.app.camera

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.npauloj.mibosmart.app.AppCoroutineScope
import io.github.npauloj.mibosmart.app.camera.platform.PlayerEvent
import io.github.npauloj.mibosmart.domain.camera.StreamState
import io.github.npauloj.mibosmart.domain.camera.StreamStep
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * SPEC V3 and V8 — the two things that decide whether this feature costs the account money.
 *
 * The screen `StateFlow` is read as `state.value` after `advanceUntilIdle()` on a
 * `StandardTestDispatcher`; Turbine is for one-shot event flows only (`CLAUDE.md`).
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
        advanceUntilIdle()
        assertEquals(StreamState.Creating(StreamStep.CheckingCapability), viewModel.state.value)

        capability.complete(Unit)
        advanceUntilIdle()
        assertEquals(StreamState.Creating(StreamStep.CreatingSession), viewModel.state.value)

        creation.complete(Unit)
        advanceUntilIdle()
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
        advanceUntilIdle()
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
        advanceUntilIdle()
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
        advanceUntilIdle()
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
        advanceUntilIdle()
        viewModel.stop()
        advanceUntilIdle()

        viewModel.resume()
        viewModel.resume()
        advanceUntilIdle()

        assertIs<StreamState.Live>(viewModel.state.value)
        assertEquals(2, partner.opened.size, "resuming a live screen created a second session")
    }

    /** The stream stopped playing: the state says so and the session stops costing (SPEC V8). */
    @Test
    fun aStreamThatStopsPlayingEndsItsSession() = runTest(dispatcher) {
        val partner = FakeStreamingRepository()
        val viewModel = viewModelWith(partner)
        viewModel.open(CameraSamples.camera())
        advanceUntilIdle()

        viewModel.onPlayerEvent(PlayerEvent.NetworkError)
        advanceUntilIdle()

        assertEquals(StreamState.Expired, viewModel.state.value)
        assertEquals(listOf(CameraSamples.SESSION.id), partner.closed)
    }

    private fun viewModelWith(partner: FakeStreamingRepository) = LiveVideoViewModel(
        watchLiveVideo = WatchLiveVideo(partner, LiveVideoSwitch(isOn = true)),
        endStreamSession = EndStreamSession(partner),
        // The real one is a `SupervisorJob` on `Dispatchers.Default`; here it is the same shape on a
        // scheduler the test controls, so "the teardown ran" is an assertion and not a wait.
        appScope = AppCoroutineScope(SupervisorJob() + dispatcher),
    )
}
