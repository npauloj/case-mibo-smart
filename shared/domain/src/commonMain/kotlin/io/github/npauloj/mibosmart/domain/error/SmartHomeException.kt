package io.github.npauloj.mibosmart.domain.error

/** What can go wrong between the app and the partner platform, as types (ADR-002). */
sealed class SmartHomeException(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /** The partner does not recognise the token: **HTTP 401** (SPEC S3, ADR-012). */
    class TokenRejected : SmartHomeException("the partner rejected the access token")

    /** The token was recognised and has expired: **HTTP 403** (SPEC S3.1, S6, ADR-012). */
    class TokenExpired(val serverMessage: String?) :
        SmartHomeException(serverMessage ?: "the access token has expired")

    /**
     * The partner does not know the device the call named: a wrapped envelope with `statusCode:
     * 404` (SPEC E2, `docs/api-contract.md` §1.1).
     */
    class DeviceNotFound : SmartHomeException("the partner does not know this device")

    /**
     * The account has no streaming quota left: **HTTP 402**, or a `criar-fluxo-video` body that
     * says so (SPEC V6, `docs/api-contract.md` §6).
     */
    class QuotaExceeded : SmartHomeException("the account has no streaming quota left")

    /** The call is not allowed for this account — **not** a problem with the session. */
    class Forbidden(val gatewayMessage: String) :
        SmartHomeException("the partner refused the call: $gatewayMessage")

    /** The request never produced an answer: no connectivity, timeout, DNS or TLS failure (SPEC S4). */
    class Offline(cause: Throwable?) : SmartHomeException("the partner API could not be reached", cause)

    /** HTTP 200 carrying something that is not the documented envelope, or an envelope without `data` (SPEC E3). */
    class UnexpectedResponse(detail: String, cause: Throwable? = null) : SmartHomeException(detail, cause)

    /**
     * An error the partner named itself. [serverMessage] is its raw `msg`: it belongs in logs
     * and in this exception, never verbatim on a screen (SPEC U6).
     */
    class ApiError(val serverMessage: String) : SmartHomeException(serverMessage)
}
