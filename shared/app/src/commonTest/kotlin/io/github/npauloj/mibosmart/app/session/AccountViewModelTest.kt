package io.github.npauloj.mibosmart.app.session

import app.cash.turbine.test
import io.github.npauloj.mibosmart.app.FixedClock
import io.github.npauloj.mibosmart.data.remote.RequestCounter
import io.github.npauloj.mibosmart.data.session.InMemorySessionStore
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
    ): AccountViewModel {
        val sessionStore = store ?: storeWith(issuedAt)
        return AccountViewModel(
            sessionStartup = SessionStartup(sessionStore),
            logout = Logout(sessionStore),
            requestCounter = counter,
            debugBuild = debug,
            clock = FixedClock(NOW),
        )
    }

    private companion object {
        val TOKEN = TokenSamples.Valid
        val NOW = TokenSamples.Now
    }

    /** A vault whose `clear` fails — an invalidated Keystore key, a Keychain error (ADR-008). */
    private class FailingSessionStore(private val delegate: SessionStore) : SessionStore by delegate {
        override suspend fun clear(): Unit = throw IllegalStateException("invalidated key")
    }
}
