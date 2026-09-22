package io.github.npauloj.mibosmart.domain.session

import io.github.npauloj.mibosmart.domain.error.SmartHomeException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

/**
 * SPEC S6 and the routing half of U5: what a refused request does to the stored session, and what it
 * hands the app to route with.
 *
 * The policy is a pure function of the vault plus one refusal, so it is tested here rather than
 * through a ViewModel — the case that matters most (a refusal carrying a token the vault no longer
 * holds) is one the UI cannot easily stage.
 */
class SessionGuardTest {

    /** A 401 for the token the vault is holding: the session goes, and the user is routed back. */
    @Test
    fun rejectionClearsAndRoutes() = runTest {
        val store = RecordingSessionStore(CURRENT)
        val guard = SessionGuard(store)

        val ended = guard.onRefusal(refusalOf(SmartHomeException.TokenRejected()), returnTo = SCREEN)

        assertNotNull(ended, "a refusal of the stored token must end the session")
        assertEquals(SessionEndReason.Expired, ended.reason)
        assertTrue(store.cleared, "the credential stayed in the vault after a 401")
        assertNull(store.read())
    }

    /**
     * A 403 carries the partner's own sentence, and it is shown as-is.
     *
     * It is the one category SPEC U6 allows quoting (ADR-012): "Token expirado, por favor gere um
     * novo token" already says what to do, and rewording it would only make it vaguer.
     */
    @Test
    fun expiryClearsAndRoutesWithServerMessage() = runTest {
        val store = RecordingSessionStore(CURRENT)
        val guard = SessionGuard(store)

        val ended = guard.onRefusal(refusalOf(SmartHomeException.TokenExpired(PARTNER_SENTENCE)), returnTo = SCREEN)

        assertEquals(SessionEndReason.StatedByPartner(PARTNER_SENTENCE), ended?.reason)
        assertTrue(store.cleared, "the credential stayed in the vault after a 403")
    }

    /**
     * The scenario the identity comparison exists for (SPEC S6).
     *
     * A request left with the old token, the session was replaced while it was in flight, and the
     * answer came back refused. Comparing timestamps would clear a session that works; comparing
     * credentials keeps it.
     */
    @Test
    fun rejectionOfRotatedTokenDoesNotClearVault() = runTest {
        val store = RecordingSessionStore(RENEWED)
        val guard = SessionGuard(store)

        val ended = guard.onRefusal(refusalOf(SmartHomeException.TokenRejected()), returnTo = SCREEN)

        assertNull(ended, "a refusal of a replaced token must not end the current session")
        assertFalse(store.cleared, "the renewed credential was wiped by a stale refusal")
        assertEquals(RENEWED, store.read()?.token)
    }

    /** SPEC U5: one value carries both the sentence to show and the screen to come back to. */
    @Test
    fun rejectionCarriesReasonAndReturnDestination() = runTest {
        val guard = SessionGuard(RecordingSessionStore(CURRENT))

        val ended = guard.onRefusal(refusalOf(SmartHomeException.TokenExpired(PARTNER_SENTENCE)), returnTo = SCREEN)

        assertEquals(SessionEnded(SessionEndReason.StatedByPartner(PARTNER_SENTENCE), SCREEN), ended)
    }

    /** A blank `msg` is no message: the app's own sentence beats an empty line (SPEC U6). */
    @Test
    fun anExpiryWithoutAMessageFallsBackToTheAppsWording() = runTest {
        val guard = SessionGuard(RecordingSessionStore(CURRENT))

        val ended = guard.onRefusal(refusalOf(SmartHomeException.TokenExpired("   ")), returnTo = SCREEN)

        assertEquals(SessionEndReason.Expired, ended?.reason)
    }

    /** Signed out already: there is no session to end and nothing to route away from. */
    @Test
    fun aRefusalWithoutAStoredSessionChangesNothing() = runTest {
        val store = RecordingSessionStore(token = null)

        val ended = SessionGuard(store)
            .onRefusal(refusalOf(SmartHomeException.TokenRejected()), returnTo = SCREEN)

        assertNull(ended)
        assertFalse(store.cleared)
    }

    /**
     * The correction of ADR-012, as a test: `cota-disponivel` answers 403 with the **gateway's**
     * `{"message": "Forbidden"}` for a perfectly valid token. Classifying that as an expiry threw
     * away a working session and sent the user to the token screen for nothing.
     */
    @Test
    fun aForbiddenEndpointIsNotARefusalOfTheSession() {
        assertNull(SmartHomeException.Forbidden("Forbidden").asTokenRefusal(CURRENT))
    }

    /** Everything that is not a 401/403 about the session leaves the credential alone (SPEC E1). */
    @Test
    fun otherFailuresAreNotRefusals() {
        assertNull(SmartHomeException.Offline(cause = null).asTokenRefusal(CURRENT))
        assertNull(SmartHomeException.DeviceNotFound().asTokenRefusal(CURRENT))
        assertNull(SmartHomeException.QuotaExceeded().asTokenRefusal(CURRENT))
        assertNull(SmartHomeException.UnexpectedResponse("not an envelope").asTokenRefusal(CURRENT))
        assertNull(SmartHomeException.ApiError("Erro qualquer").asTokenRefusal(CURRENT))
    }

    private fun refusalOf(failure: SmartHomeException): TokenRefusal =
        requireNotNull(failure.asTokenRefusal(sentWith = CURRENT)) { "$failure is not a session refusal" }

    private companion object {

        /** The token every refusal in these tests was sent with. */
        val CURRENT = Token("token-em-uso")

        /** What the vault holds after a renewal that completed mid-flight (SPEC S6, S10). */
        val RENEWED = Token("token-renovado")

        /** The screen the user was on; the guard only carries it, so any value proves the point. */
        const val SCREEN = "device-list"

        /** The partner's real 403 body, probed 2026-09-21 (ADR-012). */
        const val PARTNER_SENTENCE = "Token expirado, por favor gere um novo token"
    }

    /** A vault that remembers whether it was emptied — the assertion of the rotation case. */
    private class RecordingSessionStore(token: Token?) : SessionStore {

        private var stored: Session? = token?.let { Session(it, ISSUED_AT) }

        var cleared: Boolean = false
            private set

        override suspend fun read(): Session? = stored

        override suspend fun write(token: Token, issuedAt: Instant, lifetime: Duration) {
            stored = Session(token, issuedAt, lifetime)
        }

        override suspend fun clear() {
            cleared = true
            stored = null
        }

        private companion object {
            /** Fixed: nothing here reads a clock, and a session needs an instant to exist. */
            val ISSUED_AT: Instant = Instant.fromEpochMilliseconds(1_758_456_000_000)
        }
    }
}
