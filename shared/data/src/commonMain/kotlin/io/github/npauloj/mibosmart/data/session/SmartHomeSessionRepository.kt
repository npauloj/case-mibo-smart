package io.github.npauloj.mibosmart.data.session

import io.github.npauloj.mibosmart.data.remote.SmartHomeApi
import io.github.npauloj.mibosmart.domain.session.SessionRepository
import io.github.npauloj.mibosmart.domain.session.Token

/**
 * Validates a token with one `listar-dispositivos` call for the smallest possible page (SPEC S2).
 *
 * The endpoint answers for any account, so a success proves the credential and a failure names the
 * reason. The payload is discarded here: the real list, its ordering and its cache belong to the
 * device slice, and fetching it twice would spend the request budget for nothing (ADR-006).
 */
internal class SmartHomeSessionRepository(private val api: SmartHomeApi) : SessionRepository {

    override suspend fun validateToken(token: Token) {
        api.listDevices(token, pageSize = VALIDATION_PAGE_SIZE, page = FIRST_PAGE)
    }

    private companion object {
        const val VALIDATION_PAGE_SIZE = 1
        const val FIRST_PAGE = 1
    }
}
