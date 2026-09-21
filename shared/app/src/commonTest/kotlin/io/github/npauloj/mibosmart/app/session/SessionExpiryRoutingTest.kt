package io.github.npauloj.mibosmart.app.session

import io.github.npauloj.mibosmart.app.AppDestination
import io.github.npauloj.mibosmart.app.FixedClock
import io.github.npauloj.mibosmart.app.appViewModel
import io.github.npauloj.mibosmart.data.session.InMemorySessionStore
import io.github.npauloj.mibosmart.domain.error.SmartHomeException
import io.github.npauloj.mibosmart.domain.session.SessionEndReason
import io.github.npauloj.mibosmart.domain.session.Token
import io.github.npauloj.mibosmart.domain.session.asTokenRefusal
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * SPEC S6 and U5 as the user meets them: a refused request anywhere in the app ends on the token
 * screen with a reason, and re-validating comes back to the screen they were on.
 *
 * `SessionGuardTest` proves the policy; this proves the wiring — that a refusal reported by the
 * transport actually reaches the guard, and that what the guard answers becomes a route.
 *
 * `runCurrent()` and not `advanceUntilIdle()`: the ViewModel keeps one task pending on purpose (the
 * sleep until the expiry warning, SPEC S7) and advancing until idle would jump the clock 1 h 50 min.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionExpiryRoutingTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /** A 401 on any request: the vault is emptied and the user is back at the way in (SPEC S6). */
    @Test
    fun aRejectedRequestEndsTheSessionAndRoutesToTheTokenScreen() = runTest(dispatcher) {
        val store = signedIn()
        val refusals = FakeRefusedRequests()
        val viewModel = appViewModel(store, FixedClock(TokenSamples.Now), refusals)
        runCurrent()

        refusals.report(SmartHomeException.TokenRejected())
        runCurrent()

        assertEquals(AppDestination.TokenEntry, viewModel.state.value.destination)
        assertEquals(SessionEndReason.Expired, viewModel.state.value.sessionEnded)
        assertNull(store.read(), "the refused credential stayed in the vault")
    }

    /** A 403 routes the same way and carries the partner's own sentence (SPEC U6, ADR-012). */
    @Test
    fun anExpiredSessionShowsThePartnersSentence() = runTest(dispatcher) {
        val refusals = FakeRefusedRequests()
        val viewModel = appViewModel(signedIn(), FixedClock(TokenSamples.Now), refusals)
        runCurrent()

        refusals.report(SmartHomeException.TokenExpired(PARTNER_SENTENCE))
        runCurrent()

        assertEquals(
            SessionEndReason.StatedByPartner(PARTNER_SENTENCE),
            viewModel.state.value.sessionEnded,
        )
    }

    /**
     * SPEC U5: the screen the user was on is where a new token puts them back.
     *
     * Staged on the account screen because it is the one destination this slice adds, and because
     * dropping the user at the device list is exactly the "lost context" U5 names.
     */
    @Test
    fun revalidatingReturnsToTheScreenTheUserWasOn() = runTest(dispatcher) {
        val store = signedIn()
        val refusals = FakeRefusedRequests()
        val viewModel = appViewModel(store, FixedClock(TokenSamples.Now), refusals)
        runCurrent()
        viewModel.openAccount()

        refusals.report(SmartHomeException.TokenRejected())
        runCurrent()
        assertEquals(AppDestination.TokenEntry, viewModel.state.value.destination)

        store.write(Token(RENEWED), TokenSamples.Now)
        viewModel.onAuthenticated()
        runCurrent()

        assertEquals(AppDestination.Account, viewModel.state.value.destination)
        assertNull(viewModel.state.value.sessionEnded, "the expiry banner outlived the expiry")
    }

    /**
     * The rotation case, end to end (SPEC S6): the refusal names a token the vault no longer holds,
     * so nothing happens — the user stays where they are with the session that works.
     */
    @Test
    fun aRefusalOfARotatedTokenChangesNothing() = runTest(dispatcher) {
        val store = InMemorySessionStore().apply { write(Token(RENEWED), TokenSamples.Now) }
        val refusals = FakeRefusedRequests()
        val viewModel = appViewModel(store, FixedClock(TokenSamples.Now), refusals)
        runCurrent()

        refusals.report(SmartHomeException.TokenRejected())
        runCurrent()

        assertEquals(AppDestination.DeviceList, viewModel.state.value.destination)
        assertNull(viewModel.state.value.sessionEnded)
        assertEquals(Token(RENEWED), store.read()?.token)
    }

    /** "Sair" routes to the token screen with nothing to explain — the user asked for it (SPEC S8). */
    @Test
    fun signingOutRoutesToTheTokenScreenWithoutAReason() = runTest(dispatcher) {
        val viewModel = appViewModel(signedIn(), FixedClock(TokenSamples.Now))
        runCurrent()
        viewModel.openAccount()

        viewModel.onSignedOut()

        assertEquals(AppDestination.TokenEntry, viewModel.state.value.destination)
        assertNull(viewModel.state.value.sessionEnded, "Sair is not something to apologise for")
    }

    /** The account screen is reachable from the device list and closes back onto it (SPEC S8). */
    @Test
    fun theAccountScreenOpensAndCloses() = runTest(dispatcher) {
        val viewModel = appViewModel(signedIn(), FixedClock(TokenSamples.Now))
        runCurrent()

        viewModel.openAccount()
        assertEquals(AppDestination.Account, viewModel.state.value.destination)

        viewModel.closeAccount()
        assertEquals(AppDestination.DeviceList, viewModel.state.value.destination)
    }

    private suspend fun signedIn() =
        InMemorySessionStore().apply { write(Token(CURRENT), TokenSamples.Now) }

    /** Reports the failure as the transport would: carrying the token the request was sent with. */
    private fun FakeRefusedRequests.report(failure: SmartHomeException) {
        report(requireNotNull(failure.asTokenRefusal(Token(CURRENT))) { "$failure is not a refusal" })
    }

    private companion object {
        val CURRENT = TokenSamples.Valid
        val RENEWED = TokenSamples.ValidHexBody

        /** The partner's real 403 body, probed 2026-09-21 (ADR-012). */
        const val PARTNER_SENTENCE = "Token expirado, por favor gere um novo token"
    }
}
