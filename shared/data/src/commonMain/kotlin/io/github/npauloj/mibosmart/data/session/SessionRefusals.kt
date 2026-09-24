package io.github.npauloj.mibosmart.data.session

import io.github.npauloj.mibosmart.domain.session.RefusedRequests
import io.github.npauloj.mibosmart.domain.session.TokenRefusal
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** The one stream every refused request is announced on (SPEC S6, ADR-018). */
internal class SessionRefusals : RefusedRequests {

    private val reported = MutableSharedFlow<TokenRefusal>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    override val refusals: SharedFlow<TokenRefusal> = reported.asSharedFlow()

    override fun report(refusal: TokenRefusal) {
        reported.tryEmit(refusal)
    }
}
