package io.github.npauloj.mibosmart.data.remote

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * How many partner requests this run of the app has spent (ADR-006).
 *
 * The test account has a budget of roughly 300 requests for the whole case, and the rules built on
 * that — cache the list, classify before asking, never poll — are invisible until something counts.
 * This is that something: one increment at the single point every call passes through, read back by
 * the account screen in a debug build.
 *
 * It counts **requests sent**, not answers received: a call that timed out still spent one.
 */
class RequestCounter {

    private val sent = MutableStateFlow(0)

    /**
     * The count so far, never reset — a run of the app is the unit ADR-006 talks about.
     *
     * A [StateFlow] rather than a plain `Int` for the atomicity, not for the stream: `update` is a
     * compare-and-set loop, and Ktor can finish two calls on two threads at once. It is exposed as
     * one so a future surface can watch it without this class growing a second way to be read.
     */
    val requests: StateFlow<Int> = sent.asStateFlow()

    /** One more request left the app. Called from whichever thread Ktor happens to be on. */
    fun increment() {
        sent.update { it + 1 }
    }
}
