package io.github.npauloj.mibosmart.data.session

import io.github.npauloj.mibosmart.domain.session.RefusedRequests
import io.github.npauloj.mibosmart.domain.session.TokenRefusal
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * The one stream every refused request is announced on (SPEC S6, ADR-018).
 *
 * `:shared:data` is where the token a request was sent with is still in scope when the partner
 * refuses it, so this is where the fact is published; the guard above decides what it means.
 *
 * No `replay`: a refusal is an event, not a state. Replaying one would send the user back to the
 * token screen a second time — after they had already pasted a new token — for a request that failed
 * before it existed.
 */
internal class SessionRefusals : RefusedRequests {

    private val reported = MutableSharedFlow<TokenRefusal>(
        extraBufferCapacity = 1,
        // The guard collects from the app's first frame, so there is a subscriber long before any
        // request can be sent. Dropping is the honest answer if that ever stops being true: a
        // refusal the guard missed costs one more refusal on the next request, while suspending
        // here would block the HTTP call that is reporting it.
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    override val refusals: SharedFlow<TokenRefusal> = reported.asSharedFlow()

    override fun report(refusal: TokenRefusal) {
        reported.tryEmit(refusal)
    }
}
