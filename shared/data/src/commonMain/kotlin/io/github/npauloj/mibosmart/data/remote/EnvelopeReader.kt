package io.github.npauloj.mibosmart.data.remote

import io.github.npauloj.mibosmart.domain.error.SmartHomeException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Turns a partner response into its `data` payload, or into a typed failure (ADR-002, ADR-012).
 *
 * **The status is read before the body.** Authentication failures answer `401` and `403` with a bare
 * JSON string rather than the documented envelope (`docs/api-contract.md` §1.2, probed 2026-09-21), so
 * a reader that parsed first would turn a rejected token into "resposta inesperada" — which is exactly
 * what the previous version did. Business outcomes still live inside a `2xx` body, where `status`
 * decides.
 */
internal class EnvelopeReader(private val json: Json) {

    /**
     * @param statusCode the HTTP status of the response, which classifies authentication on its own.
     * @param rawBody the response body; on `401`/`403` it is advisory and may fail to parse.
     */
    fun read(statusCode: Int, rawBody: String): JsonElement {
        when (statusCode) {
            HTTP_UNAUTHORIZED -> throw SmartHomeException.TokenRejected()
            HTTP_FORBIDDEN -> throw SmartHomeException.TokenExpired(serverMessageOrNull(rawBody))
        }
        val envelope = try {
            json.decodeFromString<ApiEnvelopeDto>(rawBody)
        } catch (malformed: SerializationException) {
            throw SmartHomeException.UnexpectedResponse("response body is not a partner envelope", malformed)
        }
        // The wrapped shape carries its own outcome code while HTTP still says 200 (api-contract §1.1):
        // `statusCode: 404` is the partner saying the device is not there, and it must not arrive on a
        // screen as a generic API error (SPEC E2).
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
     * The partner's own sentence from a `403` body, or null when it is not there.
     *
     * A `403` is already classified by the time this runs, so an unparseable body must never change the
     * verdict — it only costs the nicer message (SPEC S3.1). The observed body is a normal envelope
     * (`{"status":"erro","msg":"Token expirado, …"}`), but a bare JSON string is accepted too, since
     * that is the shape `401` uses and nothing guarantees `403` will not switch to it.
     */
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
        const val ENVELOPE_NOT_FOUND = 404
    }
}
