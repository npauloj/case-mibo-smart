package io.github.npauloj.mibosmart.data.remote

import io.github.npauloj.mibosmart.domain.error.SmartHomeException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Turns a partner response into its `data` payload, or into a typed failure (ADR-002, ADR-012).
 */
internal class EnvelopeReader(private val json: Json) {

    /**
     * @param statusCode the HTTP status of the response, which classifies authentication on its
     * own.
     * @param rawBody the response body; on `401`/`403` it is advisory and may fail to parse.
     */
    fun read(statusCode: Int, rawBody: String): JsonElement {
        when (statusCode) {
            HTTP_UNAUTHORIZED -> throw SmartHomeException.TokenRejected()
            HTTP_FORBIDDEN -> throw forbidden(rawBody)
            HTTP_PAYMENT_REQUIRED -> throw SmartHomeException.QuotaExceeded()
        }
        val envelope = try {
            json.decodeFromString<ApiEnvelopeDto>(rawBody)
        } catch (malformed: SerializationException) {
            throw SmartHomeException.UnexpectedResponse("response body is not a partner envelope", malformed)
        }
        if (envelope.statusCode == ENVELOPE_NOT_FOUND) throw SmartHomeException.DeviceNotFound()
        val payload = envelope.body ?: envelope
        return when (payload.status) {
            STATUS_SUCCESS -> payload.data
                ?: throw SmartHomeException.UnexpectedResponse("envelope reports success without `data`")

            STATUS_ERROR -> throw SmartHomeException.ApiError(payload.msg.orEmpty())
            else -> throw SmartHomeException.UnexpectedResponse("envelope without a known `status`")
        }
    }

    /**
     * What a `403` actually is: an expired session, or a call this account is not allowed to
     * make.
     */
    private fun forbidden(rawBody: String): SmartHomeException {
        val gatewayMessage = try {
            json.decodeFromString<GatewayErrorDto>(rawBody).message
        } catch (_: SerializationException) {
            null
        }
        return if (gatewayMessage != null) {
            SmartHomeException.Forbidden(gatewayMessage)
        } else {
            SmartHomeException.TokenExpired(serverMessageOrNull(rawBody))
        }
    }

    /** The partner's own sentence from a `403` body, or null when it is not there. */
    private fun serverMessageOrNull(rawBody: String): String? = try {
        json.decodeFromString<ApiEnvelopeDto>(rawBody).let { it.body ?: it }.msg?.takeIf { it.isNotBlank() }
    } catch (_: SerializationException) {
        try {
            json.decodeFromString<String>(rawBody).takeIf { it.isNotBlank() }
        } catch (_: SerializationException) {
            null
        }
    }

    private companion object {
        const val STATUS_SUCCESS = "sucesso"
        const val STATUS_ERROR = "erro"
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
        const val HTTP_PAYMENT_REQUIRED = 402
        const val ENVELOPE_NOT_FOUND = 404
    }
}
