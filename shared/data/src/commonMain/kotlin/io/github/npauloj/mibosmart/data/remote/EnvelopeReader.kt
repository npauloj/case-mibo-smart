package io.github.npauloj.mibosmart.data.remote

import io.github.npauloj.mibosmart.domain.error.SmartHomeException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Turns a partner response body into its `data` payload, or into a typed failure (ADR-002).
 *
 * The HTTP status is always 200, so this is the single place that decides whether a call succeeded
 * (`docs/api-contract.md` §1.1). Token rejection is a business reading of a deliberately generic
 * message and only holds for the flat shape — the one every authentication failure uses (§1.2); the
 * same text inside a wrapped envelope is a server error, not a verdict on the credential.
 */
internal class EnvelopeReader(private val json: Json) {

    fun read(rawBody: String): JsonElement {
        val envelope = try {
            json.decodeFromString<ApiEnvelopeDto>(rawBody)
        } catch (malformed: SerializationException) {
            throw SmartHomeException.UnexpectedResponse("response body is not a partner envelope", malformed)
        }
        val payload = envelope.body ?: envelope
        return when (payload.status) {
            STATUS_SUCCESS -> payload.data
                ?: throw SmartHomeException.UnexpectedResponse("envelope reports success without `data`")

            STATUS_ERROR -> throw payload.toFailure(wrapped = envelope.body != null)
            else -> throw SmartHomeException.UnexpectedResponse("envelope without a known `status`")
        }
    }

    private fun ApiEnvelopeDto.toFailure(wrapped: Boolean): SmartHomeException {
        val serverMessage = msg.orEmpty()
        val rejectsToken = !wrapped && serverMessage.startsWith(UNKNOWN_ERROR_PREFIX)
        return if (rejectsToken) SmartHomeException.TokenRejected() else SmartHomeException.ApiError(serverMessage)
    }

    private companion object {
        const val STATUS_SUCCESS = "sucesso"
        const val STATUS_ERROR = "erro"
        const val UNKNOWN_ERROR_PREFIX = "Erro desconhecido"
    }
}
