package io.github.npauloj.mibosmart.app

import io.github.npauloj.mibosmart.app.session.AccountViewModel
import io.github.npauloj.mibosmart.app.session.DebugBuild
import io.github.npauloj.mibosmart.app.session.FakeSessionRepository
import io.github.npauloj.mibosmart.app.session.Logout
import io.github.npauloj.mibosmart.app.session.RenewToken
import io.github.npauloj.mibosmart.app.session.SessionStartup
import io.github.npauloj.mibosmart.app.session.TokenSamples
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * The state the app holds between screens (ADR-003, ADR-010). Which screen a session opens is SPEC
 * S5's, and lives in `session/SessionStartTest`; this pins what the state says before it knows.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /**
     * Nothing is routed to until the vault has answered.
     *
     * A default of "token screen" would flash the way-in at a signed-in user for a frame, and a
     * default of "device list" would show a session the store cannot back — the failure ADR-010 is
     * about. The honest default is "not known yet", and it is only one vault read wide.
     */
    @Test
    fun noDestinationIsChosenBeforeTheStoreAnswers() = runTest(dispatcher) {
        val viewModel = appViewModel(InMemorySessionStore(), FixedClock(TokenSamples.Now))

        assertNull(viewModel.state.value.destination, "a destination was guessed before the vault read")
        assertFalse(viewModel.state.value.expiringSoon)
    }

    /**
     * Renewing clears the banner (SPEC S7, S10).
     *
     * This is the lie S-03 made reachable: before this slice [AppUiState.expiringSoon] was computed
     * once at startup and then only ever set to `true`, so a renewed two-hour session still carried
     * the warning — the one action the app offers against expiry looking broken.
     */
    @Test
    fun renewalClearsExpiringSoon() = runTest(dispatcher) {
        val world = renewingWorld(issuedAt = NOW - Session.WARN_AFTER)
        runCurrent()
        assertTrue(world.app.state.value.expiringSoon, "the banner was not up before the renewal")

        world.account.onRenew()
        runCurrent()

        assertFalse(world.app.state.value.expiringSoon, "the banner survived a successful renewal")
    }

    /**
     * The user does not move (SPEC S10).
     *
     * The guard against the obvious wrong fix: `onStart()` would recompute the warning correctly and
     * rebuild [AppUiState] wholesale on the way, resetting the destination and throwing the user off
     * the account screen at the exact moment they renewed. This fails loudly if anyone reaches for it.
     */
    @Test
    fun renewalDoesNotChangeDestination() = runTest(dispatcher) {
        val world = renewingWorld(issuedAt = NOW - Session.WARN_AFTER)
        runCurrent()
        world.app.openAccount()

        world.account.onRenew()
        runCurrent()

        assertEquals(
            AppDestination.Account,
            world.app.state.value.destination,
            "the renewal moved the user off the screen they renewed from",
        )
    }

    /**
     * One timer, rescheduled — not two racing (SPEC S7).
     *
     * The old session would have warned a minute from now; the renewed one warns five minutes from
     * now (15 min of life less the 10 min margin). Both instants are checked: silence at the old
     * deadline proves the first timer was cancelled, and the banner at the new one proves a second
     * was actually scheduled. Without the cancel, the loser of the race raises the banner over a
     * session that has just been renewed.
     */
    @Test
    fun renewalReschedulesTheSingleWarning() = runTest(dispatcher) {
        val world = renewingWorld(issuedAt = NOW - (Session.WARN_AFTER - 1.minutes))
        runCurrent()
        assertFalse(world.app.state.value.expiringSoon, "the session was already expiring before the test began")

        world.account.onRenew()
        runCurrent()

        advanceTimeBy(2.minutes)
        runCurrent()
        assertFalse(world.app.state.value.expiringSoon, "the cancelled timer of the old session still fired")

        advanceTimeBy(4.minutes)
        runCurrent()
        assertTrue(world.app.state.value.expiringSoon, "the renewed session never scheduled its own warning")
    }

    /**
     * A renewal that failed changes nothing at all (SPEC S10).
     *
     * Not just the flag: the credential is untouched and the original timer still fires at its own
     * deadline, because a failure must leave the app in the state it would have had anyway.
     */
    @Test
    fun failedRenewalChangesNothing() = runTest(dispatcher) {
        val world = renewingWorld(
            issuedAt = NOW - (Session.WARN_AFTER - 1.minutes),
            partner = FakeSessionRepository(renewal = { throw SmartHomeException.Offline(cause = null) }),
        )
        runCurrent()

        world.account.onRenew()
        runCurrent()

        assertFalse(world.app.state.value.expiringSoon, "a failed renewal moved the banner")
        assertEquals(Token(TOKEN), world.store.read()?.token, "a failed renewal touched the stored session")

        advanceTimeBy(2.minutes)
        runCurrent()
        assertTrue(
            world.app.state.value.expiringSoon,
            "the original warning was cancelled by a renewal that never happened",
        )
    }

    private class RenewingWorld(
        val app: AppViewModel,
        val account: AccountViewModel,
        val store: SessionStore,
    )

    /**
     * Both ViewModels over **one** store, wired the way `App.kt` wires them.
     *
     * The collector is the point: these tests exercise the whole path — the account announces, some
     * collector carries it, the routing ViewModel recomputes — rather than calling `refreshExpiry()`
     * directly and proving only that the method works in isolation.
     */
    private suspend fun TestScope.renewingWorld(
        issuedAt: Instant,
        partner: FakeSessionRepository =
            FakeSessionRepository(renewal = { RenewedSession(Token(RENEWED), 15.minutes) }),
    ): RenewingWorld {
        val store = InMemorySessionStore().apply { write(Token(TOKEN), issuedAt) }
        val clock = FixedClock(NOW)
        val app = appViewModel(store, clock)
        val account = AccountViewModel(
            sessionStartup = SessionStartup(store),
            logout = Logout(store),
            renewToken = RenewToken(partner, store, clock),
            requestCounter = RequestCounter(),
            debugBuild = DebugBuild(true),
            clock = clock,
        )
        // `backgroundScope`, not `launch`: collecting a SharedFlow never returns, and a plain child
        // job would keep `runTest` waiting for a collector that cannot finish.
        backgroundScope.launch { account.renewed.collect { app.refreshExpiry() } }
        return RenewingWorld(app, account, store)
    }

    private companion object {
        val TOKEN = TokenSamples.Valid

        /** A different suffix, so a stale session cannot pass for the renewed one. */
        val RENEWED = TokenSamples.ValidHexBody
        val NOW = TokenSamples.Now
    }
}
