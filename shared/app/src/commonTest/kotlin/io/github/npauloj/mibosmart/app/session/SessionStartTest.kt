package io.github.npauloj.mibosmart.app.session

import io.github.npauloj.mibosmart.app.AppDestination
import io.github.npauloj.mibosmart.app.FixedClock
import io.github.npauloj.mibosmart.app.appViewModel
import io.github.npauloj.mibosmart.data.session.InMemorySessionStore
import io.github.npauloj.mibosmart.domain.session.Token
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * SPEC S5: which screen a cold start opens, decided by the vault and by nothing else.
 *
 * Read from `state.value` off virtual time — never with Turbine, which this repository reserves for
 * one-shot event flows. `runCurrent()` and not `advanceUntilIdle()`, because the ViewModel keeps one
 * task pending on purpose (the sleep until the expiry warning, SPEC S7) and advancing until idle
 * would silently jump the clock 1 h 50 min into these assertions. `Dispatchers.setMain` is required
 * because the startup read runs in `viewModelScope`, which is pinned to the main dispatcher.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionStartTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /**
     * A stored session goes straight to the device list, and costs the account nothing.
     *
     * **The "costs nothing" half is structural, not asserted here, and that is deliberate.**
     * [SessionStartup] is constructed from a [io.github.npauloj.mibosmart.domain.session.SessionStore]
     * and nothing else — there is no repository, no client, no seam through which startup could reach
     * the partner. A counter would have nothing to count.
     *
     * This used to be a `FakeSessionRepository` and an `assertEquals(0, repository.calls)`. The fake
     * was never passed to the ViewModel, so the assertion was true by construction and could not
     * fail: it read as proof and verified nothing. Removed on 2026-09-22 rather than left as false
     * comfort.
     *
     * **If [SessionStartup] ever gains a repository, this test must gain a real counter** — that is
     * the moment SPEC S5 (startup makes no validation call) and ADR-006 stop being free.
     */
    @Test
    fun storedTokenSkipsEntry() = runTest(dispatcher) {
        val store = InMemorySessionStore().apply { write(Token(TOKEN), TokenSamples.Now) }
        val viewModel = appViewModel(store, FixedClock(TokenSamples.Now))

        runCurrent()

        assertEquals(AppDestination.DeviceList, viewModel.state.value.destination)
    }

    @Test
    fun noStoredTokenOpensTokenScreen() = runTest(dispatcher) {
        val viewModel = appViewModel(InMemorySessionStore(), FixedClock(TokenSamples.Now))

        runCurrent()

        assertEquals(AppDestination.TokenEntry, viewModel.state.value.destination)
        assertFalse(viewModel.state.value.expiringSoon, "there is no session to warn about")
    }

    /** The token screen's accepted token is what moves the app on, re-read from the store (SPEC S2). */
    @Test
    fun anAcceptedTokenOpensTheDeviceList() = runTest(dispatcher) {
        val store = InMemorySessionStore()
        val viewModel = appViewModel(store, FixedClock(TokenSamples.Now))
        runCurrent()
        assertEquals(AppDestination.TokenEntry, viewModel.state.value.destination)

        store.write(Token(TOKEN), TokenSamples.Now)
        viewModel.onAuthenticated()
        runCurrent()

        assertEquals(AppDestination.DeviceList, viewModel.state.value.destination)
    }

    private companion object {
        val TOKEN = TokenSamples.Valid
    }
}
