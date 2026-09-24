package io.github.npauloj.mibosmart.app.session

import io.github.npauloj.mibosmart.domain.session.RefusedRequests
import io.github.npauloj.mibosmart.domain.session.TokenRefusal
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * The refusal stream without a partner: a test says "this request came back refused" and the
 * guard above reacts exactly as it would in the app.
 */
internal class FakeRefusedRequests : RefusedRequests {

    private val reported = MutableSharedFlow<TokenRefusal>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    override val refusals: SharedFlow<TokenRefusal> = reported.asSharedFlow()

    override fun report(refusal: TokenRefusal) {
        reported.tryEmit(refusal)
    }
}
