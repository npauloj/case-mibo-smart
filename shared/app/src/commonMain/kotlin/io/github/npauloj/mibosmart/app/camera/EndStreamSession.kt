package io.github.npauloj.mibosmart.app.camera

import io.github.npauloj.mibosmart.domain.camera.StreamingRepository
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Giving the streaming quota back (SPEC V8).
 *
 * The whole point of this class is *when* it is allowed to fail. It runs under [NonCancellable]
 * because it is called precisely when everything around it is being cancelled — the screen is gone,
 * the ViewModel is cleared — and a cancelled `encerrar-sessao` leaves a session billing the account.
 * It is bounded by a timeout because the user has already left: waiting forever on a dead network
 * would keep a coroutine alive for the rest of the process.
 *
 * A failure is swallowed deliberately. There is no screen left to tell, nothing the user could do,
 * and the partner ends the session at `stream_gb` on its own; retrying here would only spend more
 * requests (ADR-006).
 */
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
