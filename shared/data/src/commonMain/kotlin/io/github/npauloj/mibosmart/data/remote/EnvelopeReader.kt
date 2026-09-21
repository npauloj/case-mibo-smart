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
            // A 403 means one of two unrelated things, and only the body tells them apart (probed
            // 2026-09-21, ADR-012 amended): the partner's own envelope
            // `{"status":"erro","msg":"Token expirado…"}` is an expired session, while the gateway's
            // `{"message":"Forbidden"}` is an endpoint this account may not call — `cota-disponivel`
            // answers exactly that. Treating the second as an expiry would throw away a valid token
            // and send the user back to the token screen for no reason (SPEC S6).
            HTTP_FORBIDDEN -> throw forbidden(rawBody)
            // The one status the Swagger documents for a business outcome that the app must tell apart
            // without reading a sentence (§6, SPEC V6). It is classified here, beside the other two
            // statuses that mean something, rather than by matching words later.
            HTTP_PAYMENT_REQUIRED -> throw SmartHomeException.QuotaExceeded()
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
     * What a `403` actually is: an expired session, or a call this account is not allowed to make.
     *
     * The partner answers the first with its own envelope and a sentence fit to show the user; the
     * gateway answers the second with `{"message": "..."}`, a shape the partner's API never uses.
     * Anything else on a 403 is treated as expiry, which is the safe default: the worst case is one
     * unnecessary re-authentication, against silently swallowing a dead session.
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
        const val HTTP_PAYMENT_REQUIRED = 402
        const val ENVELOPE_NOT_FOUND = 404
    }
}
