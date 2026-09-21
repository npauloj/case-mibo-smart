package io.github.npauloj.mibosmart.app.session

import io.github.npauloj.mibosmart.app.FixedClock
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

/** SPEC S1, S1.2: what the screen allows before and during a validation. */
@OptIn(ExperimentalCoroutinesApi::class)
class TokenEntryViewModelTest {

    @Test
    fun emptyInputKeepsSubmitDisabled() = runTest {
        val repository = FakeSessionRepository()
        val viewModel = TokenEntryViewModel(AuthenticateToken(repository, InMemorySessionStore(), FixedClock(TokenSamples.Now)))

        assertFalse(viewModel.state.value.canSubmit, "a cold start has nothing to validate")

        viewModel.onTokenChange("   ")
        assertFalse(viewModel.state.value.canSubmit, "whitespace is not a token")

        viewModel.onValidate()
        assertEquals(0, repository.calls, "a blank field must never reach the API")
        assertFalse(viewModel.state.value.hasInvalidFormat, "an untouched field is a start, not a mistake")

        viewModel.onTokenChange(TokenSamples.Valid)
        assertTrue(viewModel.state.value.canSubmit)
    }

    @Test
    fun malformedTokenCostsNoRequest() = runTest {
        val repository = FakeSessionRepository()
        val viewModel = TokenEntryViewModel(AuthenticateToken(repository, InMemorySessionStore(), FixedClock(TokenSamples.Now)))

        viewModel.onTokenChange(TokenSamples.Truncated)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.canSubmit, "a truncated paste must not enable Validar")
        assertTrue(viewModel.state.value.hasInvalidFormat, "and the field has to say why")
        assertEquals(20, viewModel.state.value.characterCount, "the counter shows how far off it is")

        viewModel.onValidate()
        advanceUntilIdle()

        assertEquals(0, repository.calls, "the whole point of S1.2: a typo costs nothing from the budget")
        assertEquals(null, viewModel.state.value.error, "and no failure is attributed to the partner")
    }

    @Test
    fun aPastedTokenIsAcceptedWithItsTrailingNewline() = runTest {
        val repository = FakeSessionRepository()
        val viewModel = TokenEntryViewModel(AuthenticateToken(repository, InMemorySessionStore(), FixedClock(TokenSamples.Now)))

        viewModel.onTokenChange("${TokenSamples.Valid}\n")
        advanceUntilIdle()

        assertTrue(viewModel.state.value.canSubmit, "the clipboard's newline is not the user's mistake")

        viewModel.onValidate()
        advanceUntilIdle()

        assertEquals(1, repository.calls)
        assertEquals(TokenSamples.Valid, repository.lastToken?.value, "the partner gets the trimmed token")
    }

    @Test
    fun validationLocksTheScreenAndCostsOneRequest() = runTest {
        val partnerAnswered = CompletableDeferred<Unit>()
        val repository = FakeSessionRepository { partnerAnswered.await() }
        val viewModel = TokenEntryViewModel(AuthenticateToken(repository, InMemorySessionStore(), FixedClock(TokenSamples.Now)))
        viewModel.onTokenChange(TokenSamples.Valid)

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
