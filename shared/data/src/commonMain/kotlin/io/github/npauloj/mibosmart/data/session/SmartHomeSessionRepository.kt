package io.github.npauloj.mibosmart.data.session

import io.github.npauloj.mibosmart.data.remote.RenewTokenRequestDto
import io.github.npauloj.mibosmart.data.remote.RenewedTokenDto
import io.github.npauloj.mibosmart.data.remote.SmartHomeApi
import io.github.npauloj.mibosmart.domain.error.SmartHomeException
import io.github.npauloj.mibosmart.domain.session.RenewedSession
import io.github.npauloj.mibosmart.domain.session.SessionRepository
import io.github.npauloj.mibosmart.domain.session.Token
import kotlin.time.Duration.Companion.seconds
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** The two session calls against the partner API (SPEC S2, S10). */
internal class SmartHomeSessionRepository(
    private val api: SmartHomeApi,
    private val json: Json,
) : SessionRepository {

    override suspend fun validateToken(token: Token) {
        api.listDevices(token, pageSize = VALIDATION_PAGE_SIZE, page = FIRST_PAGE)
    }

    /** SPEC S10: one call, and the deadline comes back with the credential. */
    override suspend fun renewToken(token: Token): RenewedSession {
        val payload = api.renewToken(token, RenewTokenRequestDto(token.value))
        val renewed = try {
            json.decodeFromJsonElement(RenewedTokenDto.serializer(), payload)
        } catch (malformed: SerializationException) {
            throw SmartHomeException.UnexpectedResponse("the renewal payload is not the documented shape", malformed)
        }
        return RenewedSession(Token(renewed.token), renewed.lifetimeSeconds.seconds)
    }

    private companion object {
        const val VALIDATION_PAGE_SIZE = 1
        const val FIRST_PAGE = 1
    }
}
