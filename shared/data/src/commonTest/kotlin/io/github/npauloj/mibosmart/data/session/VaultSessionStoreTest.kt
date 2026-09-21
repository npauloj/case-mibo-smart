package io.github.npauloj.mibosmart.data.session

import io.github.npauloj.mibosmart.domain.session.Token
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

/**
 * SPEC S2, persistence half: what the app stores and reads back, independent of which vault is
 * underneath. The Keystore and Keychain actuals are proven on a device (ADR-008 §Confirmation).
 */
class VaultSessionStoreTest {

    @Test
    fun roundTripsToken() = runTest {
        val store = VaultSessionStore(FakeSecureTokenStore())

        store.write(Token(TOKEN))

        assertEquals(Token(TOKEN), store.read())
    }

    @Test
    fun overwriteKeepsLatest() = runTest {
        val store = VaultSessionStore(FakeSecureTokenStore())

        store.write(Token(TOKEN))
        store.write(Token(RENEWED_TOKEN))

        assertEquals(Token(RENEWED_TOKEN), store.read(), "a second write replaces the credential")
    }

    @Test
    fun absentKeyReturnsNull() = runTest {
        val store = VaultSessionStore(FakeSecureTokenStore())

        assertNull(store.read(), "a vault that was never written is an app without a session")
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
    }
}
