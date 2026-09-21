package io.github.npauloj.mibosmart.domain.error

/**
 * What can go wrong between the app and the partner platform, as types (ADR-002).
 *
 * `:shared:data` raises these; every use case catches them and maps them to its own sealed result, so
 * no screen ever sees a transport detail. The taxonomy holds only the categories the app can already
 * observe: quota-exceeded and operation-rejected arrive as new subtypes with the first slice that can
 * actually receive them, never as a refactor of these — which is how [DeviceNotFound] arrived with
 * the device list.
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

    /**
     * The partner does not know the device the call named: a wrapped envelope with
     * `statusCode: 404` (SPEC E2, `docs/api-contract.md` §1.1).
     *
     * Reachable for the first time with the device list, which is why it joins the taxonomy here and
     * not earlier (ADR-002): a device can vanish between two list loads, and addressing a lock by the
     * wrong `ns` answers exactly this (api-contract §3, `funcoes` with a lock's plain serial).
     */
    class DeviceNotFound : SmartHomeException("the partner does not know this device")

    /**
     * The account has no streaming quota left: **HTTP 402**, or a `criar-fluxo-video` body that says
     * so (SPEC V6, `docs/api-contract.md` §6).
     *
     * It joins the taxonomy with the video slice, which is the first code that can receive it — the
     * arrival ADR-002 predicted. It is deliberately not an [ApiError]: quota is the one failure with
     * no retry, and a screen must be able to tell it apart without reading a sentence.
     */
    class QuotaExceeded : SmartHomeException("the account has no streaming quota left")

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
