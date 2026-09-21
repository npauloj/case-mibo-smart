package io.github.npauloj.mibosmart.data.remote

import io.github.npauloj.mibosmart.domain.error.SmartHomeException
import io.github.npauloj.mibosmart.domain.session.Token
import io.ktor.client.HttpClient
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
) {

    /** `POST /produtos/listar-dispositivos/v1`, returning the raw `data` payload of the envelope. */
    suspend fun listDevices(token: Token, pageSize: Int, page: Int): JsonElement {
        val rawBody = try {
            httpClient.post("${baseUrl.trimEnd('/')}$LIST_DEVICES_PATH") {
                contentType(ContentType.Application.Json)
                bearerAuth(token.value)
                setBody(ListDevicesRequestDto(pageSize = pageSize, page = page))
            }.bodyAsText()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (transport: Throwable) {
            // The API reports its own errors inside a 200 body, so anything thrown here is the call
            // never having completed: no connectivity, timeout, DNS or TLS.
            throw SmartHomeException.Offline(transport)
        }
        return envelopeReader.read(rawBody)
    }

    private companion object {
        const val LIST_DEVICES_PATH = "/produtos/listar-dispositivos/v1"
    }
}
