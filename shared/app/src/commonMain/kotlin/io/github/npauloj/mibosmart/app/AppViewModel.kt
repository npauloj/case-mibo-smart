package io.github.npauloj.mibosmart.app

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Which of the two destinations the app is on, as one state (ADR-003).
 *
 * The flag lives in a ViewModel and not in `rememberSaveable` so that it has the same lifetime as the
 * session it stands for: `viewModelScope` survives a configuration change and dies with the process,
 * exactly like the in-memory `SessionStore` of this slice (ADR-010). Saved state would outlive the
 * store and bring the app back "authenticated" with no session in it.
 *
 * Deliberately no more than that: routing from a *stored* token at startup, the expiry guard, the
 * expiry banner and logout are S-02's, and the vault that makes a session outlive the process is
 * S-01b's.
 */
class AppViewModel : ViewModel() {

    private val mutableAuthenticated = MutableStateFlow(false)

    /** `true` once the partner accepted a token in this process (SPEC S2). */
    val authenticated: StateFlow<Boolean> = mutableAuthenticated.asStateFlow()

    fun onAuthenticated() {
        mutableAuthenticated.value = true
    }
}
