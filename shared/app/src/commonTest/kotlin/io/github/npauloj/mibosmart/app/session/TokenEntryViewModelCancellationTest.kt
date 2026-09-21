package io.github.npauloj.mibosmart.app.session

import io.github.npauloj.mibosmart.app.FixedClock
import io.github.npauloj.mibosmart.data.session.InMemorySessionStore
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.Dispatchers

/**
 * ADR-003 (§Confirmation): the ViewModel owns its in-flight work, so cancelling the composition that
 * started it must not leave the screen locked.
 *
 * A rotation destroys the composition. If the validation ran on the composition's scope, its
 * cancellation would propagate out of [TokenEntryViewModel.onValidate] before the line that clears
 * `isValidating`, and the restored screen would come back with the field, the paste action and
 * "Validar" disabled for good — one request of the account's budget already spent (ADR-006).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TokenEntryViewModelCancellationTest {

    private val mainDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun validationSurvivesTheCompositionThatStartedIt() = runTest(mainDispatcher) {
        val partnerAnswered = CompletableDeferred<Unit>()
        val repository = FakeSessionRepository { partnerAnswered.await() }
        val viewModel = TokenEntryViewModel(AuthenticateToken(repository, InMemorySessionStore(), FixedClock(TokenSamples.Now)))
        viewModel.onTokenChange(TokenSamples.Valid)

        // Stands in for the composition: the scope the screen's tap arrives on, and the one a
        // configuration change cancels.
        val composition = CoroutineScope(coroutineContext + Job())
        composition.launch { viewModel.validate() }
        advanceUntilIdle()
        assertTrue(viewModel.state.value.isValidating, "the validation did not start")

        composition.cancel() // the rotation
        advanceUntilIdle()
        partnerAnswered.complete(Unit)
        advanceUntilIdle()

        assertFalse(
            viewModel.state.value.isValidating,
            "the screen came back locked: nobody cleared the spinner state the composition abandoned",
        )
        assertTrue(viewModel.state.value.canSubmit, "the user can no longer retry what they typed")
        assertTrue(repository.calls <= 1, "the account pays for every request (ADR-006)")
    }
}
