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
     * The partner refused the token (SPEC S3): a flat envelope with `status: "erro"` and the generic
     * "Erro desconhecido" message, which is the only signal a rejected token leaves
     * (`docs/api-contract.md` §1.2).
     */
    class TokenRejected : SmartHomeException("the partner rejected the access token")

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
