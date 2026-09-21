package io.github.npauloj.mibosmart.app.session

import io.github.npauloj.mibosmart.data.session.InMemorySessionStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

/** SPEC S1: what the screen allows before and during a validation. */
@OptIn(ExperimentalCoroutinesApi::class)
class TokenEntryViewModelTest {

    @Test
    fun emptyInputKeepsSubmitDisabled() = runTest {
        val repository = FakeSessionRepository()
        val viewModel = TokenEntryViewModel(AuthenticateToken(repository, InMemorySessionStore()))

        assertFalse(viewModel.state.value.canSubmit, "a cold start has nothing to validate")

        viewModel.onTokenChange("   ")
        assertFalse(viewModel.state.value.canSubmit, "whitespace is not a token")

        viewModel.onValidate()
        assertEquals(0, repository.calls, "a blank field must never reach the API")

        viewModel.onTokenChange("um-token")
        assertTrue(viewModel.state.value.canSubmit)
    }

    @Test
    fun validationLocksTheScreenAndCostsOneRequest() = runTest {
        val partnerAnswered = CompletableDeferred<Unit>()
        val repository = FakeSessionRepository { partnerAnswered.await() }
        val viewModel = TokenEntryViewModel(AuthenticateToken(repository, InMemorySessionStore()))
        viewModel.onTokenChange("um-token")

        val validation = launch { viewModel.onValidate() }
        advanceUntilIdle()

        assertTrue(viewModel.state.value.isValidating, "the spinner state is what locks the input")
        assertFalse(viewModel.state.value.canSubmit)

        viewModel.onValidate() // a second tap while the first call is in flight
        advanceUntilIdle()
        assertEquals(1, repository.calls)

        partnerAnswered.complete(Unit)
        validation.join()
        assertFalse(viewModel.state.value.isValidating)
    }
}
