package io.github.npauloj.mibosmart.app.camera

import io.github.npauloj.mibosmart.domain.camera.StreamingRepository
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Giving the streaming quota back (SPEC V8). */
class EndStreamSession(private val streaming: StreamingRepository) {

    suspend operator fun invoke(sessionId: String) {
        withContext(NonCancellable) {
            withTimeoutOrNull(TIMEOUT) {
                runCatching { streaming.closeSession(sessionId) }
            }
        }
    }

    private companion object {
        val TIMEOUT = 5.seconds
    }
}
