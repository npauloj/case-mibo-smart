package io.github.npauloj.mibosmart.app.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.npauloj.mibosmart.domain.session.Token
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Everything the token screen shows, in one immutable value (ADR-003). */
data class TokenEntryUiState(
    val token: String = "",
    val isValidating: Boolean = false,
    val error: TokenEntryError? = null,
) {
    /**
     * "Validar" is disabled while the field is blank and while a validation is running (SPEC S1).
     *
     * It is also the retry of SPEC S4: a failed validation keeps what was typed, so pressing the same
     * button again is the one action the error state offers (SPEC U6).
     */
    val canSubmit: Boolean get() = token.isNotBlank() && !isValidating
}

/** The reasons a validation can fail, one user-facing message each (SPEC E2, limited to what S-01a can observe). */
enum class TokenEntryError { TokenRejected, Offline, UnexpectedResponse, Failed }

/**
 * The token screen: one state, and intents as suspend functions rather than a second stream (ADR-003).
 */
class TokenEntryViewModel(private val authenticateToken: AuthenticateToken) : ViewModel() {

    private val mutableState = MutableStateFlow(TokenEntryUiState())
    val state: StateFlow<TokenEntryUiState> = mutableState.asStateFlow()

    private val mutableOpenDeviceList = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Emitted once per accepted token; the destination is the device list (SPEC S2). */
    val openDeviceList: SharedFlow<Unit> = mutableOpenDeviceList.asSharedFlow()

    fun onTokenChange(token: String) {
        mutableState.update { it.copy(token = token, error = null) }
    }

    /**
     * What "Validar" does, launched in `viewModelScope` (ADR-003).
     *
     * The scope matters: `viewModelScope` outlives a configuration change, while the composition's
     * scope dies with it and would cancel the request halfway through [onValidate] — before the line
     * that clears `isValidating`, leaving the restored screen locked on a request already paid for.
     */
    fun validate() {
        viewModelScope.launch { onValidate() }
    }

    /**
     * Validates what was typed: exactly one partner call, then either the navigation event or a named
     * error with the input untouched (SPEC S2–S4). Screens call [validate]; this is the same intent as
     * a suspend function, so a test can await it.
     *
     * A second call while one is in flight is ignored — the screen already locks the field and the
     * button, and the account pays for every request (ADR-006).
     */
    suspend fun onValidate() {
        if (!mutableState.value.canSubmit) return
        mutableState.update { it.copy(isValidating = true, error = null) }
        val result = authenticateToken(Token(mutableState.value.token.trim()))
        mutableState.update { it.copy(isValidating = false, error = result.toError()) }
        if (result == AuthenticationResult.Success) {
            mutableOpenDeviceList.emit(Unit)
        }
    }
}

private fun AuthenticationResult.toError(): TokenEntryError? = when (this) {
    AuthenticationResult.Success -> null
    AuthenticationResult.TokenRejected -> TokenEntryError.TokenRejected
    AuthenticationResult.Offline -> TokenEntryError.Offline
    AuthenticationResult.UnexpectedResponse -> TokenEntryError.UnexpectedResponse
    AuthenticationResult.Failed -> TokenEntryError.Failed
}
