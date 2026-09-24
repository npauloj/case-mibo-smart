package io.github.npauloj.mibosmart.app.session

import io.github.npauloj.mibosmart.app.FixedClock
import io.github.npauloj.mibosmart.data.session.InMemorySessionStore
import io.github.npauloj.mibosmart.domain.error.SmartHomeException
import io.github.npauloj.mibosmart.domain.session.RenewedSession
import io.github.npauloj.mibosmart.domain.session.Session
import io.github.npauloj.mibosmart.domain.session.SessionStore
import io.github.npauloj.mibosmart.domain.session.Token
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest

/** SPEC S10: what "Renovar" does to the stored session. */
class RenewTokenTest {

    /** The credential the vault holds afterwards is the one the partner just issued. */
    @Test
    fun replacesStoredToken() = runTest {
        val store = storeHolding(CURRENT)
        val repository = FakeSessionRepository(renewal = { RenewedSession(Token(RENEWED), DEFAULT_LIFETIME) })

        val result = renewToken(repository, store)()

        assertEquals(RenewalResult.Success, result)
        assertEquals(Token(RENEWED), store.read()?.token, "the renewed credential did not reach the vault")
        assertEquals(Token(CURRENT), repository.lastToken, "the renewal was not sent with the current token")
    }

    /** A renewal that fails leaves the session exactly as it was (SPEC S10). */
    @Test
    fun failureKeepsCurrentToken() = runTest {
        val store = storeHolding(CURRENT)
        val repository = FakeSessionRepository(renewal = { throw SmartHomeException.Offline(cause = null) })

        val result = renewToken(repository, store)()

        assertEquals(RenewalResult.Failed, result)
        assertEquals(
            Session(Token(CURRENT), ISSUED_AT),
            store.read(),
            "a failed renewal changed the session it was supposed to leave alone",
        )
    }

    /** The deadline is the server's, not a local two-hour count (SPEC S7, S10). */
    @Test
    fun usesServerSuppliedDeadline() = runTest {
        val store = storeHolding(CURRENT)
        val repository = FakeSessionRepository(renewal = { RenewedSession(Token(RENEWED), SHORT_LIFETIME) })

        renewToken(repository, store)()

        val renewed = assertNotNull(store.read())
        assertEquals(SHORT_LIFETIME, renewed.remainingLife(NOW), "the deadline was not the one the server gave")
        assertEquals(NOW, renewed.issuedAt, "the renewed session starts counting down from the renewal")
    }

    /**
     * The warning keeps its ten minutes even when the server hands back a short session (SPEC
     * S7).
     */
    @Test
    fun aShortRenewedSessionStillWarnsBeforeItEnds() = runTest {
        val store = storeHolding(CURRENT)
        val repository = FakeSessionRepository(renewal = { RenewedSession(Token(RENEWED), SHORT_LIFETIME) })

        renewToken(repository, store)()

        val renewed = assertNotNull(store.read())
        assertEquals(5.minutes, renewed.remainingUntilWarning(NOW))
    }

    /** Nothing to renew: the vault was emptied while the tap was in flight (SPEC S6). */
    @Test
    fun withoutASessionThereIsNothingToRenew() = runTest {
        val repository = FakeSessionRepository()

        val result = renewToken(repository, InMemorySessionStore())()

        assertEquals(RenewalResult.NoSession, result)
    }

    private suspend fun storeHolding(token: String): SessionStore =
        InMemorySessionStore().apply { write(Token(token), ISSUED_AT) }

    private fun renewToken(repository: FakeSessionRepository, store: SessionStore) =
        RenewToken(sessionRepository = repository, sessionStore = store, clock = FixedClock(NOW))

    private companion object {
        val CURRENT = TokenSamples.Valid
        val RENEWED = TokenSamples.ValidHexBody

        /** When the session under test was issued, and — a renewal later — "now". */
        val ISSUED_AT = TokenSamples.Now
        val NOW = TokenSamples.Now

        /** The 7199 s the partner answered on the probe, as a duration (`api-contract.md` §2). */
        val DEFAULT_LIFETIME: Duration = 7199.seconds

        /** Deliberately not two hours: a `tempoExpiracao` no local count could have produced. */
        val SHORT_LIFETIME: Duration = 15.minutes
    }
}
