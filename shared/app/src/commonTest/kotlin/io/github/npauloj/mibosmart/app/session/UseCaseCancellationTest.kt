package io.github.npauloj.mibosmart.app.session

import io.github.npauloj.mibosmart.app.FixedClock
import io.github.npauloj.mibosmart.data.session.InMemorySessionStore
import io.github.npauloj.mibosmart.domain.session.Token
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

/** SPEC E4 / ADR-002: cancellation is not an error. */
@OptIn(ExperimentalCoroutinesApi::class)
class UseCaseCancellationTest {

    @Test
    fun propagates() = runTest {
        val authenticateToken = AuthenticateToken(
            FakeSessionRepository { awaitCancellation() },
            InMemorySessionStore(),
            FixedClock(TokenSamples.Now),
        )
        var result: AuthenticationResult? = null

        val validation = launch { result = authenticateToken(Token("um-token")) }
        advanceUntilIdle()
        validation.cancelAndJoin()

        assertTrue(validation.isCancelled)
        assertNull(result, "cancellation was swallowed and turned into a result")
    }

    /** The same rule for "Renovar" (SPEC S10): an abandoned renewal is not a failed one. */
    @Test
    fun renewalPropagates() = runTest {
        val store = InMemorySessionStore().apply { write(Token("um-token"), TokenSamples.Now) }
        val renewToken = RenewToken(
            FakeSessionRepository(renewal = { awaitCancellation() }),
            store,
            FixedClock(TokenSamples.Now),
        )
        var result: RenewalResult? = null

        val renewal = launch { result = renewToken() }
        advanceUntilIdle()
        renewal.cancelAndJoin()

        assertTrue(renewal.isCancelled)
        assertNull(result, "cancellation was swallowed and turned into a result")
        assertEquals(Token("um-token"), store.read()?.token, "a cancelled renewal wrote to the vault")
    }
}
