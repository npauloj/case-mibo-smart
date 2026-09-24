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

/** The partner endpoints the app calls. */
internal class SmartHomeApi(
    private val httpClient: HttpClient,
    private val baseUrl: String,
    /** Where the **streaming** endpoints live, which is not where the rest of the API lives. */
    private val streamingBaseUrl: String,
    private val envelopeReader: EnvelopeReader,
    private val requestCounter: RequestCounter,
    private val refusedRequests: RefusedRequests,
) {

    /**
     * `POST /produtos/listar-dispositivos/v1`, returning the raw `data` payload of the
     * envelope.
     * @param origin the `origem` filter on the wire — `"todos"`, `"vinculados"` or
     * `"compartilhados"` (`docs/api-contract.md` §3).
     */
    suspend fun listDevices(
        token: Token,
        pageSize: Int,
        page: Int,
        origin: String = ALL_ORIGINS,
    ): JsonElement = post(LIST_DEVICES_PATH, token) {
        setBody(ListDevicesRequestDto(pageSize = pageSize, page = page, origin = origin))
    }

    /**
     * `POST /autenticacao/renovar-token/v1` — a second credential for the same account (SPEC
     * S10).
     */
    suspend fun renewToken(token: Token, request: RenewTokenRequestDto): JsonElement =
        post(RENEW_TOKEN_PATH, token) { setBody(request) }

    /** `POST /produtos/funcoes/v1` — what a device announces it can do (SPEC V1). */
    suspend fun readDeviceFunctions(token: Token, request: CameraNamespaceRequestDto): JsonElement =
        post(FUNCTIONS_PATH, token) { setBody(request) }

    /** `POST /cameras/criar-fluxo-video/v1` — opens a session and spends streaming quota (SPEC V2). */
    suspend fun createVideoStream(token: Token, request: CreateStreamRequestDto): JsonElement =
        post(CREATE_STREAM_PATH, token, streamingBaseUrl) { setBody(request) }

    /** `POST /streaming/encerrar-sessao/v1` — gives the quota back (SPEC V8). */
    suspend fun endStreamSession(token: Token, request: EndSessionRequestDto): JsonElement =
        post(END_SESSION_PATH, token, streamingBaseUrl) { setBody(request) }

    /** `POST /fechaduras/status-abertura/v1` — whether the door is open (SPEC L1). */
    suspend fun readLockOpenState(token: Token, request: LockReadRequestDto): JsonElement =
        post(LOCK_OPEN_STATE_PATH, token) { setBody(request) }

    /** `POST /fechaduras/status-abrir-remoto/v1` — the precondition for any command (SPEC L2). */
    suspend fun readLockRemoteOpen(token: Token, request: LockReadRequestDto): JsonElement =
        post(LOCK_REMOTE_OPEN_PATH, token) { setBody(request) }

    /** `POST /fechaduras/volume/v1` — the level, read with the doubled product id (SPEC L8). */
    suspend fun readLockVolume(token: Token, request: LockVolumeRequestDto): JsonElement =
        post(LOCK_VOLUME_PATH, token) { setBody(request) }

    /** `POST /fechaduras/historico-abertura/v1` — the door's recent openings (SPEC L9). */
    suspend fun readLockOpeningHistory(token: Token, request: LockHistoryRequestDto): JsonElement =
        post(LOCK_HISTORY_PATH, token) { setBody(request) }

    /** `POST /fechaduras/controle-fechadura/v1` — opens or locks the door (SPEC L3). */
    suspend fun commandLock(token: Token, request: LockCommandRequestDto): JsonElement =
        post(LOCK_COMMAND_PATH, token) { setBody(request) }

    /** `POST /fechaduras/mudar-volume/v1` — the first write of the lock screen (SPEC L7). */
    suspend fun changeLockVolume(token: Token, request: LockChangeVolumeRequestDto): JsonElement =
        post(LOCK_CHANGE_VOLUME_PATH, token) { setBody(request) }

    /**
     * `POST /fechaduras/habilitar-abrir-remoto/v1` — grants the app the right to command the
     * lock (SPEC L2).
     */
    suspend fun enableLockRemoteOpen(token: Token, request: LockEnableRemoteOpenRequestDto): JsonElement =
        post(LOCK_ENABLE_REMOTE_OPEN_PATH, token) { setBody(request) }

    /**
     * One call: the shared shape of every partner request (`docs/api-contract.md` §1) — the
     * token in the `Authorization` header, a JSON body, and an answer that only
     * [EnvelopeReader] may interpret.
     */
    private suspend fun post(
        path: String,
        token: Token,
        host: String = baseUrl,
        body: HttpRequestBuilder.() -> Unit,
    ): JsonElement {
        requestCounter.increment()
        val response = try {
            httpClient.post("${host.trimEnd('/')}$path") {
                contentType(ContentType.Application.Json)
                bearerAuth(token.value)
                body()
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (transport: Throwable) {
            throw SmartHomeException.Offline(transport)
        }
        return try {
            envelopeReader.read(response.status.value, response.bodyAsText())
        } catch (failure: SmartHomeException) {
            failure.asTokenRefusal(token)?.let(refusedRequests::report)
            throw failure
        }
    }

    internal companion object {
        /** The `origem` of "everything the account has" (`docs/api-contract.md` §3). */
        const val ALL_ORIGINS = "todos"
        private const val RENEW_TOKEN_PATH = "/autenticacao/renovar-token/v1"
        private const val LIST_DEVICES_PATH = "/produtos/listar-dispositivos/v1"
        private const val FUNCTIONS_PATH = "/produtos/funcoes/v1"
        private const val CREATE_STREAM_PATH = "/cameras/criar-fluxo-video/v1"
        private const val END_SESSION_PATH = "/streaming/encerrar-sessao/v1"
        private const val LOCK_OPEN_STATE_PATH = "/fechaduras/status-abertura/v1"
        private const val LOCK_REMOTE_OPEN_PATH = "/fechaduras/status-abrir-remoto/v1"
        private const val LOCK_VOLUME_PATH = "/fechaduras/volume/v1"
        private const val LOCK_HISTORY_PATH = "/fechaduras/historico-abertura/v1"
        private const val LOCK_COMMAND_PATH = "/fechaduras/controle-fechadura/v1"
        private const val LOCK_CHANGE_VOLUME_PATH = "/fechaduras/mudar-volume/v1"
        private const val LOCK_ENABLE_REMOTE_OPEN_PATH = "/fechaduras/habilitar-abrir-remoto/v1"
    }
}
