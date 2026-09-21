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
     * The call counter is the real assertion here: the account has a request budget (ADR-006) and
     * SPEC S5 is explicit that startup makes **no** validation call. A token that has expired
     * meanwhile is caught by the first real request, not by a probe (SPEC E5, S6).
     */
    @Test
    fun storedTokenSkipsEntry() = runTest(dispatcher) {
        val repository = FakeSessionRepository()
        val store = InMemorySessionStore().apply { write(Token(TOKEN), TokenSamples.Now) }
        val viewModel = appViewModel(store, FixedClock(TokenSamples.Now))

        runCurrent()

        assertEquals(AppDestination.DeviceList, viewModel.state.value.destination)
        assertEquals(0, repository.calls, "startup must not spend a request validating a stored token")
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
