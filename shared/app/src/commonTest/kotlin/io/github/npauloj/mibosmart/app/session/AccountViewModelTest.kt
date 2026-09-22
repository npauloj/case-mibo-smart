package io.github.npauloj.mibosmart.app.session

import app.cash.turbine.test
import io.github.npauloj.mibosmart.app.FixedClock
import io.github.npauloj.mibosmart.data.remote.RequestCounter
import io.github.npauloj.mibosmart.data.session.InMemorySessionStore
import io.github.npauloj.mibosmart.domain.error.SmartHomeException
import io.github.npauloj.mibosmart.domain.session.RenewedSession
import io.github.npauloj.mibosmart.domain.session.Session
import io.github.npauloj.mibosmart.domain.session.SessionStore
import io.github.npauloj.mibosmart.domain.session.Token
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * SPEC S8, S9 and the ADR-006 counter: what the account screen may say about the session.
 *
 * The state is read from `state.value` after `runCurrent()` on a `StandardTestDispatcher`, never with
 * Turbine — the repository reserves Turbine for one-shot event flows, which here is `signedOut`
 * alone (`CLAUDE.md`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AccountViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /**
     * SPEC S9 / ADR-008: the last 4 characters, and nothing else, ever reach the state.
     *
     * Asserted as "no fragment of the token longer than 4 characters appears anywhere in the state"
     * rather than as an equality, because the rule is about what must *not* be there — an equality
     * would still pass if a second field carried the whole credential.
     */
    @Test
    fun exposesSuffixOnly() = runTest(dispatcher) {
        val viewModel = viewModelFor(issuedAt = NOW)

        runCurrent()

        val state = viewModel.state.value
        assertEquals(TOKEN.takeLast(4), state.tokenSuffix)
        val rendered = state.toString()
        assertFalse(rendered.contains(TOKEN), "the whole token reached the account state: $rendered")
        assertFalse(
            rendered.contains(TOKEN.dropLast(4).takeLast(8)),
            "more than the last 4 characters reached the account state: $rendered",
        )
    }

    /** ADR-006: the budget counter is on in a debug build, and it is the real count. */
    @Test
    fun showsRequestCount() = runTest(dispatcher) {
        val counter = RequestCounter().apply {
            repeat(7) { increment() }
        }
        val viewModel = viewModelFor(issuedAt = NOW, counter = counter, debug = DebugBuild(true))

        runCurrent()

        assertEquals(7, viewModel.state.value.requestCount)
    }

    /** …and off in a delivered build: a number nobody outside the team can act on is noise. */
    @Test
    fun hidesTheRequestCountOutsideDebugBuilds() = runTest(dispatcher) {
        val counter = RequestCounter().apply { increment() }
        val viewModel = viewModelFor(issuedAt = NOW, counter = counter, debug = DebugBuild(false))

        runCurrent()

        assertNull(viewModel.state.value.requestCount)
    }

    /** "Expira em 1 h 47 min": the deadline read off the local clock, with no request (SPEC S7, E5). */
    @Test
    fun showsHowLongTheSessionHasLeft() = runTest(dispatcher) {
        val viewModel = viewModelFor(issuedAt = NOW - 13.minutes)

        runCurrent()

        assertEquals(SessionExpiry.Remaining(hours = 1, minutes = 47, soon = false), viewModel.state.value.expiry)
    }

    /** Inside the last 10 minutes the screen says so too, from the same policy as the banner (S7). */
    @Test
    fun warnsWhenTheSessionIsAboutToExpire() = runTest(dispatcher) {
        val viewModel = viewModelFor(issuedAt = NOW - Session.WARN_AFTER - 1.minutes)

        runCurrent()

        val expiry = viewModel.state.value.expiry as SessionExpiry.Remaining
        assertEquals(0, expiry.hours)
        assertTrue(expiry.soon, "a session in its last minutes was shown as a calm one")
    }

    /** Past the 2 h there is no countdown left to show, only the fact (SPEC S6). */
    @Test
    fun saysSoOnceTheSessionHasExpired() = runTest(dispatcher) {
        val viewModel = viewModelFor(issuedAt = NOW - Session.LIFETIME)

        runCurrent()

        assertEquals(SessionExpiry.Expired, viewModel.state.value.expiry)
    }

    /**
     * SPEC S10: "Renovar" is offered only where it buys something — inside the last 10 minutes.
     *
     * A session with hours left would spend a request of the ~300 the account has to move a deadline
     * nobody is near (ADR-006); an expired one would spend it to be refused, since renewing needs a
     * credential the partner still accepts (SPEC S6).
     */
    @Test
    fun offersRenewalOnlyWhileTheSessionIsAboutToExpire() = runTest(dispatcher) {
        val calm = viewModelFor(issuedAt = NOW)
        val expiring = viewModelFor(issuedAt = NOW - Session.WARN_AFTER)
        val expired = viewModelFor(issuedAt = NOW - Session.LIFETIME)

        runCurrent()

        assertFalse(calm.state.value.canRenew, "a fresh session was asked to renew itself")
        assertTrue(expiring.state.value.canRenew, "a session in its last minutes offered no way out")
        assertFalse(expired.state.value.canRenew, "an expired session offered a renewal it cannot make")
    }

    /**
     * A renewal the partner answered rewrites the card without moving the user (SPEC S10).
     *
     * Both halves are asserted: the suffix, because it proves the *new* credential is what the screen
     * is describing, and the countdown, because it proves the deadline came from `tempoExpiracao`
     * (15 min here) and not from a local two-hour count.
     */
    @Test
    fun renewingReplacesTheSessionOnTheSameScreen() = runTest(dispatcher) {
        val store = storeWith(NOW - Session.WARN_AFTER)
        val viewModel = viewModelFor(
            store = store,
            partner = FakeSessionRepository(renewal = { RenewedSession(Token(RENEWED), 15.minutes) }),
        )
        runCurrent()

        viewModel.onRenew()

        val state = viewModel.state.value
        assertEquals(RENEWED.takeLast(4), state.tokenSuffix, "the card still describes the old credential")
        assertEquals(SessionExpiry.Remaining(hours = 0, minutes = 15, soon = false), state.expiry)
        assertFalse(state.renewing, "the action stayed disabled after the answer came back")
    }

    /**
     * A renewal that failed says so and changes nothing else (SPEC S10).
     *
     * The session is still the one the user had, and still valid — renewal adds a credential rather
     * than replacing one (measured 2026-09-21) — so there is nothing to route away from.
     */
    @Test
    fun aFailedRenewalKeepsTheSessionAndSaysSo() = runTest(dispatcher) {
        val store = storeWith(NOW - Session.WARN_AFTER)
        val viewModel = viewModelFor(
            store = store,
            partner = FakeSessionRepository(renewal = { throw SmartHomeException.Offline(cause = null) }),
        )
        runCurrent()

        viewModel.onRenew()

        assertTrue(viewModel.state.value.renewFailed, "a failed renewal was reported as a success")
        assertTrue(viewModel.state.value.canRenew, "the way to try again disappeared with the failure")
        assertEquals(Token(TOKEN), store.read()?.token, "a failed renewal touched the stored session")
    }

    /** ADR-006: a second tap while the first is in flight is a second request, so it is refused. */
    @Test
    fun aSecondTapWhileRenewingSpendsNothing() = runTest(dispatcher) {
        val partnerAnswered = CompletableDeferred<RenewedSession>()
        val partner = FakeSessionRepository(renewal = { partnerAnswered.await() })
        val viewModel = viewModelFor(issuedAt = NOW - Session.WARN_AFTER, partner = partner)
        runCurrent()

        viewModel.renew()
        runCurrent()
        viewModel.renew()
        runCurrent()

        assertTrue(viewModel.state.value.renewing, "the screen did not show the renewal in flight")
        assertEquals(1, partner.calls, "a second tap sent a second renewal")

        partnerAnswered.complete(RenewedSession(Token(RENEWED), 15.minutes))
        runCurrent()
        assertFalse(viewModel.state.value.renewing)
    }

    /**
     * "Sair" empties the vault and announces it once (SPEC S8).
     *
     * `signedOut` is a one-shot navigation event, which is the one place this repository allows
     * Turbine (`CLAUDE.md`).
     */
    @Test
    fun signOutClearsTheSessionAndRoutesAway() = runTest(dispatcher) {
        val store = storeWith(NOW)
        val viewModel = viewModelFor(store = store)
        runCurrent()

        viewModel.signedOut.test {
            viewModel.onSignOut()

            awaitItem()
            assertNull(store.read(), "Sair left the credential on the device")
        }
    }

    /**
     * A vault that refuses to clear must not be reported as a successful logout (ADR-008).
     *
     * The user stays where they are and is told, because the alternative — a token screen over a
     * token still on disk — is the one outcome that cannot be recovered from on the next screen.
     */
    @Test
    fun aFailedSignOutKeepsTheUserSignedInAndSaysSo() = runTest(dispatcher) {
        val viewModel = viewModelFor(store = FailingSessionStore(storeWith(NOW)))
        runCurrent()

        viewModel.onSignOut()

        assertTrue(viewModel.state.value.signOutFailed, "a failed logout was reported as a success")
    }

    private suspend fun storeWith(issuedAt: Instant): SessionStore =
        InMemorySessionStore().apply { write(Token(TOKEN), issuedAt) }

    private suspend fun viewModelFor(
        issuedAt: Instant = NOW,
        store: SessionStore? = null,
        counter: RequestCounter = RequestCounter(),
        debug: DebugBuild = DebugBuild(true),
        partner: FakeSessionRepository = FakeSessionRepository(),
    ): AccountViewModel {
        val sessionStore = store ?: storeWith(issuedAt)
        return AccountViewModel(
            sessionStartup = SessionStartup(sessionStore),
            logout = Logout(sessionStore),
            renewToken = RenewToken(partner, sessionStore, FixedClock(NOW)),
            requestCounter = counter,
            debugBuild = debug,
            clock = FixedClock(NOW),
        )
    }

    private companion object {
        val TOKEN = TokenSamples.Valid

        /** The credential a renewal hands back — a different suffix, so the card cannot fake it. */
        val RENEWED = TokenSamples.ValidHexBody
        val NOW = TokenSamples.Now
    }

    /** A vault whose `clear` fails — an invalidated Keystore key, a Keychain error (ADR-008). */
    private class FailingSessionStore(private val delegate: SessionStore) : SessionStore by delegate {
        override suspend fun clear(): Unit = throw IllegalStateException("invalidated key")
    }
}
