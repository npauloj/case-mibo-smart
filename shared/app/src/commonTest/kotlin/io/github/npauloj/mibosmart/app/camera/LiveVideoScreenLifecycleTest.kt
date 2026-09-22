package io.github.npauloj.mibosmart.app.camera

import androidx.compose.runtime.Applier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Recomposer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.npauloj.mibosmart.app.AppCoroutineScope
import io.github.npauloj.mibosmart.domain.camera.StreamState
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * SPEC V8, the wiring half: the screen observes the lifecycle, and `ON_STOP` really reaches the
 * ViewModel.
 *
 * A phone going into a pocket with a stream running is the failure that costs the shared account its
 * quota, and it is invisible to every other test here — the ViewModel's own tests call `stop()`
 * directly, so they would pass just as happily if nobody ever called it.
 *
 * The composition is driven by hand with a no-op applier: [LiveVideoLifecycle] emits no UI, so it
 * needs no renderer, and the test needs no instrumentation and no new test dependency.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LiveVideoScreenLifecycleTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun backgroundingTheAppEndsTheSession() = runTest(dispatcher) {
        val partner = FakeStreamingRepository()
        val viewModel = viewModelWith(partner)
        val owner = FakeLifecycleOwner()
        val composition = compose(owner) { LiveVideoLifecycle(viewModel) }

        owner.registry.currentState = Lifecycle.State.STARTED
        viewModel.open(CameraSamples.camera())
        runCurrent()
        assertIs<StreamState.Live>(viewModel.state.value)

        owner.registry.currentState = Lifecycle.State.CREATED // ON_STOP: the app went to the background
        advanceUntilIdle()

        assertEquals(StreamState.Idle, viewModel.state.value, "the player is still attached")
        assertEquals(
            listOf(CameraSamples.SESSION.id),
            partner.closed,
            "a stream in a pocket kept spending the account's quota: ON_STOP is not wired",
        )

        composition.dispose()
    }

    /** Coming back to the foreground puts the screen back on air, with exactly one new session. */
    @Test
    fun returningToTheForegroundStartsOneNewSession() = runTest(dispatcher) {
        val partner = FakeStreamingRepository()
        val viewModel = viewModelWith(partner)
        val owner = FakeLifecycleOwner()
        val composition = compose(owner) { LiveVideoLifecycle(viewModel) }
        owner.registry.currentState = Lifecycle.State.STARTED
        viewModel.open(CameraSamples.camera())
        runCurrent()
        owner.registry.currentState = Lifecycle.State.CREATED
        advanceUntilIdle()

        owner.registry.currentState = Lifecycle.State.STARTED // ON_START
        runCurrent()

        assertIs<StreamState.Live>(viewModel.state.value)
        assertEquals(2, partner.opened.size, "coming back created more than the one session it needs")

        composition.dispose()
    }

    /**
     * A composition with no renderer behind it.
     *
     * The recomposer runs on `backgroundScope` so the test does not wait on a coroutine that, by
     * design, never finishes.
     */
    private fun TestScope.compose(owner: FakeLifecycleOwner, content: @Composable () -> Unit): Composition =
        Composition(NoOpApplier, Recomposer(backgroundScope.coroutineContext)).apply {
            setContent {
                CompositionLocalProvider(LocalLifecycleOwner provides owner) { content() }
            }
        }

    private fun viewModelWith(partner: FakeStreamingRepository) = LiveVideoViewModel(
        watchLiveVideo = WatchLiveVideo(partner, LiveVideoSwitch(isOn = true)),
        endStreamSession = EndStreamSession(partner),
        appScope = AppCoroutineScope(SupervisorJob() + dispatcher),
    )
}

/** A lifecycle nobody owns, driven by the test. `createUnsafe` skips the main-thread check. */
private class FakeLifecycleOwner : LifecycleOwner {
    val registry: LifecycleRegistry = LifecycleRegistry.createUnsafe(this)
    override val lifecycle: Lifecycle get() = registry
}

/** [LiveVideoLifecycle] emits nothing, so there is nothing to apply. */
private object NoOpApplier : Applier<Unit> {
    override val current: Unit = Unit
    override fun down(node: Unit) = Unit
    override fun up() = Unit
    override fun insertTopDown(index: Int, instance: Unit) = Unit
    override fun insertBottomUp(index: Int, instance: Unit) = Unit
    override fun remove(index: Int, count: Int) = Unit
    override fun move(from: Int, to: Int, count: Int) = Unit
    override fun clear() = Unit
}
