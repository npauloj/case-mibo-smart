package io.github.npauloj.mibosmart.app.session

import io.github.npauloj.mibosmart.app.AppViewModel
import io.github.npauloj.mibosmart.app.FixedClock
import io.github.npauloj.mibosmart.app.appViewModel
import io.github.npauloj.mibosmart.data.session.InMemorySessionStore
import io.github.npauloj.mibosmart.domain.session.Session
import io.github.npauloj.mibosmart.domain.session.SessionState
import io.github.npauloj.mibosmart.domain.session.Token
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/** SPEC S7: the session's own countdown, and the banner it raises on the device list. */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionStateTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /**
     * The banner appears at 1 h 50 min of a session that was fresh when the app opened — and
     * not a millisecond earlier.
     */
    @Test
    fun warnsBeforeExpiry() = runTest(dispatcher) {
        val viewModel = viewModelFor(issuedAt = NOW, now = NOW)
        runCurrent()

        advanceTimeBy(Session.WARN_AFTER)
        assertFalse(viewModel.state.value.expiringSoon, "the warning is not due until 1 h 50 min")

        runCurrent()
        assertTrue(viewModel.state.value.expiringSoon, "at 1 h 50 min the device list warns (SPEC S7)")
    }

    @Test
    fun freshSessionShowsNoBanner() = runTest(dispatcher) {
        val viewModel = viewModelFor(issuedAt = NOW, now = NOW)

        runCurrent()

        assertFalse(viewModel.state.value.expiringSoon, "a session that just started has nothing to say")
    }

    /** A cold start into a session that aged while the app was closed warns on the first frame. */
    @Test
    fun storedSessionPastTheThresholdWarnsImmediately() = runTest(dispatcher) {
        val viewModel = viewModelFor(issuedAt = NOW, now = NOW + Session.WARN_AFTER)

        runCurrent()

        assertTrue(viewModel.state.value.expiringSoon)
    }

    /** The policy itself, with no coroutine in the way: the boundary is inclusive. */
    @Test
    fun theThresholdIsTheFirstInstantThatWarns() {
        val session = Session(Token(TokenSamples.Valid), NOW)

        assertEquals(SessionState.Valid, session.stateAt(NOW))
        assertEquals(SessionState.Valid, session.stateAt(NOW + Session.WARN_AFTER - 1.milliseconds))
        assertEquals(SessionState.ExpiringSoon, session.stateAt(NOW + Session.WARN_AFTER))
    }

    private suspend fun viewModelFor(issuedAt: Instant, now: Instant): AppViewModel {
        val store = InMemorySessionStore().apply { write(Token(TokenSamples.Valid), issuedAt) }

        return appViewModel(store, FixedClock(now))
    }

    private companion object {
        val NOW = TokenSamples.Now
    }
}
