package io.github.npauloj.mibosmart.app.session

import app.cash.turbine.test
import io.github.npauloj.mibosmart.data.session.InMemorySessionStore
import io.github.npauloj.mibosmart.domain.error.SmartHomeException
import io.github.npauloj.mibosmart.domain.session.Token
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * SPEC S2 (first half), S3 and S4: what validating a token does to the session and to the screen.
 *
 * The use case is driven through its ViewModel because the outcomes the SPEC names are observable
 * there: the navigation event and the input that survives a failure.
 */
class AuthenticateTokenTest {

    @Test
    fun validTokenIsStoredAndSucceeds() = runTest {
        val repository = FakeSessionRepository()
        val store = InMemorySessionStore()
        val viewModel = viewModelWith(repository, store)

        viewModel.onTokenChange(TOKEN)
        viewModel.openDeviceList.test {
            viewModel.onValidate()
            awaitItem()
        }

        assertEquals(1, repository.calls, "validation must cost exactly one request")
        assertEquals(Token(TOKEN), store.read())
        assertNull(viewModel.state.value.error)
        assertEquals(false, viewModel.state.value.isValidating)
    }

    @Test
    fun rejectedTokenIsNotStored() = runTest {
        val repository = FakeSessionRepository { throw SmartHomeException.TokenRejected() }
        val store = InMemorySessionStore()
        val viewModel = viewModelWith(repository, store)

        viewModel.onTokenChange(TOKEN)
        viewModel.onValidate()

        assertNull(store.read(), "a refused token must leave nothing behind")
        assertEquals(TokenEntryError.TokenRejected, viewModel.state.value.error)
        assertEquals(TOKEN, viewModel.state.value.token)
    }

    @Test
    fun networkFailureKeepsInput() = runTest {
        val repository = FakeSessionRepository { throw SmartHomeException.Offline(IllegalStateException("no route")) }
        val store = InMemorySessionStore()
        val viewModel = viewModelWith(repository, store)

        viewModel.onTokenChange(TOKEN)
        viewModel.onValidate()

        assertEquals(TokenEntryError.Offline, viewModel.state.value.error)
        assertEquals(TOKEN, viewModel.state.value.token)
        assertTrue(viewModel.state.value.canSubmit, "the same button is the retry of SPEC S4")
        assertNull(store.read())
    }

    @Test
    fun otherFailuresAreNamedAndNotStored() = runTest {
        val unexpected = FakeSessionRepository { throw SmartHomeException.UnexpectedResponse("no envelope") }
        val storeForUnexpected = InMemorySessionStore()
        val viewModelForUnexpected = viewModelWith(unexpected, storeForUnexpected)

        viewModelForUnexpected.onTokenChange(TOKEN)
        viewModelForUnexpected.onValidate()

        assertEquals(TokenEntryError.UnexpectedResponse, viewModelForUnexpected.state.value.error)
        assertNull(storeForUnexpected.read())

        val apiError = FakeSessionRepository { throw SmartHomeException.ApiError("Plano inativo") }
        val storeForApiError = InMemorySessionStore()
        val viewModelForApiError = viewModelWith(apiError, storeForApiError)

        viewModelForApiError.onTokenChange(TOKEN)
        viewModelForApiError.onValidate()

        assertEquals(TokenEntryError.Failed, viewModelForApiError.state.value.error)
        assertNull(storeForApiError.read())
    }

    private fun viewModelWith(repository: FakeSessionRepository, store: InMemorySessionStore) =
        TokenEntryViewModel(AuthenticateToken(repository, store))

    private companion object {
        /** Well-formed: since S1.2 the screen refuses to submit anything else (see [TokenSamples]). */
        val TOKEN = TokenSamples.Valid
    }
}
