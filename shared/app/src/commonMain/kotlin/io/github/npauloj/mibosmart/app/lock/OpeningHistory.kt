package io.github.npauloj.mibosmart.app.lock

import io.github.npauloj.mibosmart.domain.error.SmartHomeException
import io.github.npauloj.mibosmart.domain.lock.LockAddress
import io.github.npauloj.mibosmart.domain.lock.LockRepository
import io.github.npauloj.mibosmart.domain.lock.OpeningEvent
import kotlin.coroutines.cancellation.CancellationException

/**
 * What opening the history tab means: one `historico-abertura`, newest first (SPEC L9).
 *
 * One because the endpoint is not paginated — [ENTRIES] is the whole answer — and because the
 * account pays per request (ADR-006): there is no timer, no poll and no retry here, and only the
 * screen may ask again.
 *
 * The order is applied here rather than trusted from the partner. The observed payload happened to
 * come back newest first, but nothing documents it, and "newest first" is the acceptance criterion:
 * a list that quietly reorders itself on a different day would be the kind of bug nobody reports.
 */
class OpeningHistory(private val lockRepository: LockRepository) {

    suspend operator fun invoke(address: LockAddress): OpeningHistoryResult =
        try {
            OpeningHistoryResult.Loaded(
                lockRepository.readOpeningHistory(address, ENTRIES).sortedByDescending { it.at },
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Throwable) {
            failure.toResult()
        }

    private fun Throwable.toResult(): OpeningHistoryResult = when (this) {
        is SmartHomeException.TokenRejected -> OpeningHistoryResult.TokenRejected
        is SmartHomeException.TokenExpired -> OpeningHistoryResult.TokenExpired(serverMessage)
        is SmartHomeException.Offline -> OpeningHistoryResult.Offline
        is SmartHomeException.UnexpectedResponse -> OpeningHistoryResult.UnexpectedResponse
        else -> OpeningHistoryResult.Failed
    }

    companion object {

        /**
         * How much history one request buys (SPEC L9).
         *
         * It is the partner's own default and the size the contract documents
         * (`docs/api-contract.md` §5). With no pagination there is no second page to ask for, so
         * this number is the whole feature: bigger costs nothing extra in requests, but a door's
         * last 50 openings is already more than a screen can be read through.
         */
        const val ENTRIES = 50
    }
}

/**
 * Everything reading the history can end in (ADR-002), mirroring [LoadLockResult]'s categories.
 *
 * An empty history is [Loaded] with nothing in it, not a category of its own: "this door has not
 * been opened" is an answer, and SPEC L10 shows it as one (`OpeningHistoryUiState.isEmpty`).
 */
sealed interface OpeningHistoryResult {

    /** The partner answered: these are the openings it knows about, newest first (SPEC L9). */
    data class Loaded(val events: List<OpeningEvent>) : OpeningHistoryResult

    /** The partner does not recognise the credential — HTTP 401 (SPEC S3, S6). */
    data object TokenRejected : OpeningHistoryResult

    /** The credential expired — HTTP 403; [serverMessage] is the partner's own sentence (SPEC S3.1). */
    data class TokenExpired(val serverMessage: String?) : OpeningHistoryResult

    /** The call never reached the partner (SPEC S4). */
    data object Offline : OpeningHistoryResult

    /** The answer was not the documented shape (SPEC E3). */
    data object UnexpectedResponse : OpeningHistoryResult

    /** The partner named an error of its own; its message stays in the exception (SPEC U6). */
    data object Failed : OpeningHistoryResult
}
