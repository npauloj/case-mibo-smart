package io.github.npauloj.mibosmart.domain.error

/**
 * What can go wrong between the app and the partner platform, as types (ADR-002).
 *
 * `:shared:data` raises these; every use case catches them and maps them to its own sealed result, so
 * no screen ever sees a transport detail. The taxonomy holds only the categories the app can already
 * observe: device-not-found, quota-exceeded and operation-rejected arrive as new subtypes with the
 * first slice that can actually receive them, never as a refactor of these.
 */
sealed class SmartHomeException(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /**
     * The partner does not recognise the token: **HTTP 401** (SPEC S3, ADR-012).
     *
     * The status is the signal. An earlier version of this class read it from the message text
     * (`"Erro desconhecido"`), which the real API never sends — the rule could not fire and a rejected
     * token surfaced as [UnexpectedResponse] instead.
     */
    class TokenRejected : SmartHomeException("the partner rejected the access token")

    /**
     * The token was recognised and has expired: **HTTP 403** (SPEC S3.1, S6, ADR-012).
     *
     * Distinct from [TokenRejected] because the platform distinguishes them, and because the user can
     * act on it: [serverMessage] is the partner's own sentence ("Token expirado, por favor gere um novo
     * token"), which is fit to show — unlike a generic [ApiError] message (SPEC U6). It is null when
     * the 403 body did not parse.
     */
    class TokenExpired(val serverMessage: String?) :
        SmartHomeException(serverMessage ?: "the access token has expired")

    /** The request never produced an answer: no connectivity, timeout, DNS or TLS failure (SPEC S4). */
    class Offline(cause: Throwable?) : SmartHomeException("the partner API could not be reached", cause)

    /** HTTP 200 carrying something that is not the documented envelope, or an envelope without `data` (SPEC E3). */
    class UnexpectedResponse(detail: String, cause: Throwable? = null) : SmartHomeException(detail, cause)

    /**
     * An error the partner named itself. [serverMessage] is its raw `msg`: it belongs in logs and in
     * this exception, never verbatim on a screen (SPEC U6).
     */
    class ApiError(val serverMessage: String) : SmartHomeException(serverMessage)
}
