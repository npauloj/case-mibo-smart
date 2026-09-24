package io.github.npauloj.mibosmart.data.remote

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** How many partner requests this run of the app has spent (ADR-006). */
class RequestCounter {

    private val sent = MutableStateFlow(0)

    /** The count so far, never reset — a run of the app is the unit ADR-006 talks about. */
    val requests: StateFlow<Int> = sent.asStateFlow()

    /** One more request left the app. Called from whichever thread Ktor happens to be on. */
    fun increment() {
        sent.update { it + 1 }
    }
}
