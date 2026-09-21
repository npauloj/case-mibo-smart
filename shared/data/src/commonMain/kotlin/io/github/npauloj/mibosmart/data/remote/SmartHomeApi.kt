package io.github.npauloj.mibosmart.data.remote

import io.github.npauloj.mibosmart.domain.error.SmartHomeException
import io.github.npauloj.mibosmart.domain.session.RefusedRequests
import io.github.npauloj.mibosmart.domain.session.Token
import io.github.npauloj.mibosmart.domain.session.asTokenRefusal
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.json.JsonElement

/**
 * The partner endpoints the app calls.
 *
 * Every call spends one request from the account budget (ADR-006), so each function is exactly one
 * HTTP call and never retries on its own — retrying is a decision for the screen that can explain it.
 */
internal class SmartHomeApi(
    private val httpClient: HttpClient,
    private val baseUrl: String,
    private val envelopeReader: EnvelopeReader,
    private val requestCounter: RequestCounter,
    private val refusedRequests: RefusedRequests,
) {

    /**
     * `POST /produtos/listar-dispositivos/v1`, returning the raw `data` payload of the envelope.
     *
     * @param origin the `origem` filter on the wire — `"todos"`, `"vinculados"` or `"compartilhados"`
     *   (`docs/api-contract.md` §3). The default is the one the partner documents, so a caller that
     *   only wants "whatever the account has" does not have to know the vocabulary.
     */
    suspend fun listDevices(
        token: Token,
        pageSize: Int,
        page: Int,
        origin: String = ALL_ORIGINS,
    ): JsonElement = post(LIST_DEVICES_PATH, token) {
        setBody(ListDevicesRequestDto(pageSize = pageSize, page = page, origin = origin))
    }

    /** `POST /produtos/funcoes/v1` — what a device announces it can do (SPEC V1). */
    suspend fun readDeviceFunctions(token: Token, request: CameraNamespaceRequestDto): JsonElement =
        post(FUNCTIONS_PATH, token) { setBody(request) }

    /** `POST /cameras/criar-fluxo-video/v1` — opens a session and spends streaming quota (SPEC V2). */
    suspend fun createVideoStream(token: Token, request: CreateStreamRequestDto): JsonElement =
        post(CREATE_STREAM_PATH, token) { setBody(request) }

    /** `POST /streaming/encerrar-sessao/v1` — gives the quota back (SPEC V8). */
    suspend fun endStreamSession(token: Token, request: EndSessionRequestDto): JsonElement =
        post(END_SESSION_PATH, token) { setBody(request) }

    /** `POST /fechaduras/status-abertura/v1` — whether the door is open (SPEC L1). */
    suspend fun readLockOpenState(token: Token, request: LockReadRequestDto): JsonElement =
        post(LOCK_OPEN_STATE_PATH, token) { setBody(request) }

    /** `POST /fechaduras/status-abrir-remoto/v1` — the precondition for any command (SPEC L2). */
    suspend fun readLockRemoteOpen(token: Token, request: LockReadRequestDto): JsonElement =
        post(LOCK_REMOTE_OPEN_PATH, token) { setBody(request) }

    /** `POST /fechaduras/volume/v1` — the level, read with the doubled product id (SPEC L8). */
    suspend fun readLockVolume(token: Token, request: LockVolumeRequestDto): JsonElement =
        post(LOCK_VOLUME_PATH, token) { setBody(request) }

    /** `POST /fechaduras/mudar-volume/v1` — the first write of the lock screen (SPEC L7). */
    suspend fun changeLockVolume(token: Token, request: LockChangeVolumeRequestDto): JsonElement =
        post(LOCK_CHANGE_VOLUME_PATH, token) { setBody(request) }

    /**
     * `POST /fechaduras/habilitar-abrir-remoto/v1` — grants the app the right to command the lock
     * (SPEC L2).
     *
     * The request type can only say `habilitar: true`, so this function has no way to take the
     * permission away: the app enables, never disables.
     */
    suspend fun enableLockRemoteOpen(token: Token, request: LockEnableRemoteOpenRequestDto): JsonElement =
        post(LOCK_ENABLE_REMOTE_OPEN_PATH, token) { setBody(request) }

    /**
     * One call: the shared shape of every partner request (`docs/api-contract.md` §1) — the token in
     * the `Authorization` header, a JSON body, and an answer that only [EnvelopeReader] may interpret.
     *
     * It is also the only place that knows **which token a given request was sent with**, which is
     * what SPEC S6 needs and why the refusal is announced from here rather than from each repository:
     * a guard wired per use case would miss the next slice's endpoint (ADR-018).
     */
    private suspend fun post(
        path: String,
        token: Token,
        body: HttpRequestBuilder.() -> Unit,
    ): JsonElement {
        // Counted before the wire, not after: an answer that never comes has still spent a request
        // from the account's budget (ADR-006).
        requestCounter.increment()
        val response = try {
            httpClient.post("${baseUrl.trimEnd('/')}$path") {
                contentType(ContentType.Application.Json)
                bearerAuth(token.value)
                body()
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (transport: Throwable) {
            // Nothing thrown here is an answer: the client is configured with `expectSuccess = false`,
            // so a status the API chose — 200, 401, 403 — arrives as a response, not an exception.
            // Reaching this branch means the call never completed: connectivity, timeout, DNS or TLS.
            throw SmartHomeException.Offline(transport)
        }
        // Status first, body second (ADR-012): a 401/403 body is a bare JSON string, not an envelope.
        return try {
            envelopeReader.read(response.status.value, response.bodyAsText())
        } catch (failure: SmartHomeException) {
            // Only a 401/403 about the session produces a refusal; a forbidden endpoint or a business
            // error produces none, and the credential is left alone (ADR-012 amended, SPEC S6).
            failure.asTokenRefusal(token)?.let(refusedRequests::report)
            throw failure
        }
    }

    internal companion object {
        /** The documented default of the `origem` filter, read by the device slice (SPEC D1). */
        const val ALL_ORIGINS = "todos"
        private const val LIST_DEVICES_PATH = "/produtos/listar-dispositivos/v1"
        private const val FUNCTIONS_PATH = "/produtos/funcoes/v1"
        private const val CREATE_STREAM_PATH = "/cameras/criar-fluxo-video/v1"
        private const val END_SESSION_PATH = "/streaming/encerrar-sessao/v1"
        private const val LOCK_OPEN_STATE_PATH = "/fechaduras/status-abertura/v1"
        private const val LOCK_REMOTE_OPEN_PATH = "/fechaduras/status-abrir-remoto/v1"
        private const val LOCK_VOLUME_PATH = "/fechaduras/volume/v1"
        private const val LOCK_CHANGE_VOLUME_PATH = "/fechaduras/mudar-volume/v1"
        private const val LOCK_ENABLE_REMOTE_OPEN_PATH = "/fechaduras/habilitar-abrir-remoto/v1"
    }
}
