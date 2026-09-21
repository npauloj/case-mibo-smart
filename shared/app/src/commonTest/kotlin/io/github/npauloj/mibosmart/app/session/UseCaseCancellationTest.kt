package io.github.npauloj.mibosmart.app.session

import io.github.npauloj.mibosmart.data.session.InMemorySessionStore
import io.github.npauloj.mibosmart.domain.session.Token
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

/**
 * SPEC E4 / ADR-002: cancellation is not an error.
 *
 * If the use case mapped `CancellationException` like any other failure, the screen would show an
 * error for a call the user themselves abandoned — and the coroutine would stop being cancellable.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UseCaseCancellationTest {

    @Test
    fun propagates() = runTest {
        val authenticateToken = AuthenticateToken(
            FakeSessionRepository { awaitCancellation() },
            InMemorySessionStore(),
        )
        var result: AuthenticationResult? = null

        val validation = launch { result = authenticateToken(Token("um-token")) }
        advanceUntilIdle()
        validation.cancelAndJoin()

        assertTrue(validation.isCancelled)
        assertNull(result, "cancellation was swallowed and turned into a result")
    }
}
