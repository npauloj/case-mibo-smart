package io.github.npauloj.mibosmart.data.session

import io.github.npauloj.mibosmart.data.remote.EnvelopeReader
import io.github.npauloj.mibosmart.data.remote.HttpClientFactory
import io.github.npauloj.mibosmart.data.remote.RequestCounter
import io.github.npauloj.mibosmart.data.remote.SmartHomeApi
import io.github.npauloj.mibosmart.data.remote.smartHomeJson
import io.github.npauloj.mibosmart.domain.error.SmartHomeException
import io.github.npauloj.mibosmart.domain.session.Token
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/** SPEC S2: validation is one call for the smallest possible page, and nothing else. */
class SmartHomeSessionRepositoryTest {

    @Test
    fun validationSendsOneSmallestPageRequest() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val repository = repositoryAnswering(requests) {
            respond(
                content = """{"status":"sucesso","data":[]}""",
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        repository.validateToken(Token("um-token"))

        assertEquals(1, requests.size)
        val request = requests.single()
        assertEquals("/produtos/listar-dispositivos/v1", request.url.encodedPath)
        val body = (request.body as TextContent).text
        assertTrue(body.contains("\"tamanhoPagina\":1"), "unexpected request body: $body")
        assertTrue(body.contains("\"pagina\":1"), "unexpected request body: $body")
    }

    /** A 401 with a bare JSON string — what the API really answers for an unknown token (ADR-012). */
    @Test
    fun aRefusedTokenSurfacesAsTokenRejected() = runTest {
        val repository = repositoryAnswering(mutableListOf()) {
            respond(
                content = "\"Não autorizado\"",
                status = HttpStatusCode.Unauthorized,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Plain.toString()),
            )
        }

        assertFailsWith<SmartHomeException.TokenRejected> { repository.validateToken(Token("um-token")) }
    }

    /** A 403 is an expired session, and it carries a message worth showing (SPEC S3.1). */
    @Test
    fun anExpiredTokenSurfacesAsTokenExpiredWithItsMessage() = runTest {
        val repository = repositoryAnswering(mutableListOf()) {
            respond(
                content = """{"status":"erro","msg":"Token expirado, por favor gere um novo token"}""",
                status = HttpStatusCode.Forbidden,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Plain.toString()),
            )
        }

        val failure = assertFailsWith<SmartHomeException.TokenExpired> {
            repository.validateToken(Token("um-token"))
        }
        assertEquals("Token expirado, por favor gere um novo token", failure.serverMessage)
    }

    @Test
    fun aTransportFailureSurfacesAsOffline() = runTest {
        val repository = repositoryAnswering(mutableListOf()) { error("no route to host") }

        assertFailsWith<SmartHomeException.Offline> { repository.validateToken(Token("um-token")) }
    }

    private fun repositoryAnswering(
        requests: MutableList<HttpRequestData>,
        answer: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ) = SmartHomeSessionRepository(
        api = SmartHomeApi(
            httpClient = HttpClientFactory.create(
                MockEngine { request ->
                    requests += request
                    answer(request)
                },
            ),
            baseUrl = "https://api.example.invalid",
            streamingBaseUrl = "https://portal.example.invalid",
            envelopeReader = EnvelopeReader(smartHomeJson),
            requestCounter = RequestCounter(),
            refusedRequests = SessionRefusals(),
        ),
        json = smartHomeJson,
    )
}
