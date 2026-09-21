package io.github.npauloj.mibosmart.data.session

import io.github.npauloj.mibosmart.domain.session.Session
import io.github.npauloj.mibosmart.domain.session.Token
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours
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

    @Test
    fun absentKeyReturnsNull() = runTest {
        val store = VaultSessionStore(FakeSecureTokenStore())

        assertNull(store.read(), "a vault that was never written is an app without a session")
    }

    /**
     * A bare token is what a build from before the `issuedAt` contract left behind (ADR-010).
     *
     * Without an `issuedAt` there is no expiry policy to apply, so the session is not one the app can
     * reason about: it fails safe towards the token screen rather than warning at the wrong moment.
     */
    @Test
    fun undecodableValueIsTreatedAsNoSession() = runTest {
        val vault = FakeSecureTokenStore().apply { write(TOKEN) }

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
    }
}
