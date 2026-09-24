package io.github.npauloj.mibosmart.data.session

import io.github.npauloj.mibosmart.domain.session.Session
import io.github.npauloj.mibosmart.domain.session.Token
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest

/**
 * SPEC S2, persistence half: what the app stores and reads back, independent of which vault is
 * underneath. The Keystore and Keychain actuals are proven on a device (ADR-008 §Confirmation).
 */
class VaultSessionStoreTest {

    @Test
    fun roundTripsSession() = runTest {
        val store = VaultSessionStore(FakeSecureTokenStore())

        store.write(Token(TOKEN), ISSUED_AT)

        assertEquals(
            Session(Token(TOKEN), ISSUED_AT),
            store.read(),
            "the credential and the instant it started counting down travel together (SPEC S7)",
        )
    }

    /** A renewed session's deadline is the partner's, and it survives a cold start (SPEC S10). */
    @Test
    fun roundTripsTheServerSuppliedLifetime() = runTest {
        val store = VaultSessionStore(FakeSecureTokenStore())

        store.write(Token(TOKEN), ISSUED_AT, RENEWED_LIFETIME)

        assertEquals(Session(Token(TOKEN), ISSUED_AT, RENEWED_LIFETIME), store.read())
    }

    @Test
    fun overwriteKeepsLatest() = runTest {
        val store = VaultSessionStore(FakeSecureTokenStore())

        store.write(Token(TOKEN), ISSUED_AT)
        store.write(Token(RENEWED_TOKEN), ISSUED_AT + 1.hours)

        assertEquals(
            Session(Token(RENEWED_TOKEN), ISSUED_AT + 1.hours),
            store.read(),
            "a second write replaces the credential and restarts the countdown",
        )
    }

    /** The token's own alphabet is not relied on: the first separator ends the timestamp, not the last. */
    @Test
    fun tokenContainingTheSeparatorSurvives() = runTest {
        val store = VaultSessionStore(FakeSecureTokenStore())

        store.write(Token("um:token:esquisito"), ISSUED_AT)

        assertEquals(Session(Token("um:token:esquisito"), ISSUED_AT), store.read())
    }

    /** SPEC S8: "Sair" takes the credential off the device, and a cold start asks for a new one. */
    @Test
    fun clearRemovesTheStoredSession() = runTest {
        val vault = FakeSecureTokenStore()
        val store = VaultSessionStore(vault)
        store.write(Token(TOKEN), ISSUED_AT)

        store.clear()

        assertNull(store.read(), "the session survived a logout")
        assertNull(vault.read(), "the credential is still in the vault after a logout")
    }

    /** Signing out of a session the vault has already lost still ends on the token screen. */
    @Test
    fun clearWithoutASessionIsNotAFailure() = runTest {
        VaultSessionStore(FakeSecureTokenStore()).clear()
    }

    @Test
    fun absentKeyReturnsNull() = runTest {
        val store = VaultSessionStore(FakeSecureTokenStore())

        assertNull(store.read(), "a vault that was never written is an app without a session")
    }

    /** A bare token is what a build from before the `issuedAt` contract left behind (ADR-010). */
    @Test
    fun undecodableValueIsTreatedAsNoSession() = runTest {
        val vault = FakeSecureTokenStore().apply { write(TOKEN) }

        assertNull(VaultSessionStore(vault).read())
    }

    /**
     * The same fail-safe one contract later: an entry with an `issuedAt` but no lifetime
     * (ADR-020).
     */
    @Test
    fun anEntryWithoutALifetimeIsTreatedAsNoSession() = runTest {
        val vault = FakeSecureTokenStore().apply { write("${ISSUED_AT.toEpochMilliseconds()}:$TOKEN") }

        assertNull(VaultSessionStore(vault).read())
    }

    @Test
    fun readFailureIsTreatedAsNoSession() = runTest {
        val store = VaultSessionStore(FakeSecureTokenStore(IllegalStateException("invalidated key")))

        assertNull(store.read(), "a broken vault must not crash the app on startup")
    }

    /** House rule: a `catch (Throwable)` never swallows a cancellation (ADR-002). */
    @Test
    fun cancellationPropagates() = runTest {
        val store = VaultSessionStore(FakeSecureTokenStore(CancellationException("cancelled")))

        assertFailsWith<CancellationException> { store.read() }
    }

    private companion object {
        const val TOKEN = "um-token"
        const val RENEWED_TOKEN = "outro-token"
        val ISSUED_AT = SessionSamples.IssuedAt

        /** A `tempoExpiracao` that is not the 2 h default, so an ignored value cannot pass (S10). */
        val RENEWED_LIFETIME = 900.seconds
    }
}
