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
    /** The partner's own sentence, when the failure carried one worth showing (SPEC S3.1). */
    val serverMessage: String? = null,
) {
    /** How many characters of [TokenFormat.LENGTH] the field holds, for the counter of SPEC S1.1. */
    val characterCount: Int get() = TokenFormat.characterCount(token)

    /** Something was entered, and it cannot be a token (SPEC S1.2). */
    val hasInvalidFormat: Boolean get() = token.isNotBlank() && !TokenFormat.isValid(token)

    /**
     * "Validar" is enabled only for a token that matches the documented format, and never while
     * a validation is running (SPEC S1, S1.2).
     */
    val canSubmit: Boolean get() = TokenFormat.isValid(token) && !isValidating
}

/** The reasons a validation can fail, one user-facing message each (SPEC E2, ADR-012). */
enum class TokenEntryError { TokenRejected, TokenExpired, Offline, UnexpectedResponse, Failed }

/**
 * The token screen: one state, and intents as suspend functions rather than a second stream
 * (ADR-003).
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

    /** What "Validar" does, launched in `viewModelScope` (ADR-003). */
    fun validate() {
        viewModelScope.launch { onValidate() }
    }

    /**
     * Validates what was typed: exactly one partner call, then either the navigation event or a
     * named error with the input untouched (SPEC S2–S4).
     */
    suspend fun onValidate() {
        if (!mutableState.value.canSubmit) return
        mutableState.update { it.copy(isValidating = true, error = null) }
        val result = authenticateToken(Token(mutableState.value.token.trim()))
        mutableState.update {
            it.copy(
                isValidating = false,
                error = result.toError(),
                serverMessage = (result as? AuthenticationResult.TokenExpired)?.serverMessage,
            )
        }
        if (result == AuthenticationResult.Success) {
            mutableOpenDeviceList.emit(Unit)
        }
    }
}

private fun AuthenticationResult.toError(): TokenEntryError? = when (this) {
    AuthenticationResult.Success -> null
    AuthenticationResult.TokenRejected -> TokenEntryError.TokenRejected
    is AuthenticationResult.TokenExpired -> TokenEntryError.TokenExpired
    AuthenticationResult.Offline -> TokenEntryError.Offline
    AuthenticationResult.UnexpectedResponse -> TokenEntryError.UnexpectedResponse
    AuthenticationResult.Failed -> TokenEntryError.Failed
}
