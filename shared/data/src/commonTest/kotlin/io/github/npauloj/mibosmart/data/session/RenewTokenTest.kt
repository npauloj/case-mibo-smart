package io.github.npauloj.mibosmart.data.session

import io.github.npauloj.mibosmart.data.remote.EnvelopeReader
import io.github.npauloj.mibosmart.data.remote.HttpClientFactory
import io.github.npauloj.mibosmart.data.remote.RequestCounter
import io.github.npauloj.mibosmart.data.remote.SmartHomeApi
import io.github.npauloj.mibosmart.data.remote.smartHomeJson
import io.github.npauloj.mibosmart.domain.error.SmartHomeException
import io.github.npauloj.mibosmart.domain.session.SessionRepository
import io.github.npauloj.mibosmart.domain.session.Token
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Headers
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest

/**
 * SPEC S10 on the wire: the exact request `renovar-token` was measured to want, and the
 * deadline it answers with (`docs/api-contract.md` §2, probed 2026-09-21).
 */
class RenewTokenTest {

    /** The path, the body and the header, exactly as measured — nothing derived from the Swagger. */
    @Test
    fun exactRequest() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val repository = repositoryAnswering(requests) { renewalAnswer() }

        repository.renewToken(Token(CURRENT))

        val request = requests.single()
        assertEquals(
            "/autenticacao/renovar-token/v1",
            request.url.encodedPath,
            "the Swagger's /autenticacao/renovarToken is not the path that answers",
        )
        assertEquals(
            """{"token":"$CURRENT"}""",
            (request.body as TextContent).text,
            "the token travels in the body as well as the header",
        )
        assertEquals("Bearer $CURRENT", request.headers[HttpHeaders.Authorization])
    }

    /** `tempoExpiracao` is seconds, and it is the only source of the renewed session's deadline. */
    @Test
    fun readsTheServerSuppliedLifetime() = runTest {
        val repository = repositoryAnswering(mutableListOf()) { renewalAnswer(lifetimeSeconds = 7199) }

        val renewed = repository.renewToken(Token(CURRENT))

        assertEquals(Token(RENEWED), renewed.token)
        assertEquals(7199.seconds, renewed.lifetime)
    }

    /** ADR-006: a renewal is one request out of ~300, and it is counted before the wire. */
    @Test
    fun spendsExactlyOneRequest() = runTest {
        val counter = RequestCounter()
        val repository = repositoryAnswering(mutableListOf(), counter) { renewalAnswer() }

        repository.renewToken(Token(CURRENT))

        assertEquals(1, counter.requests.value)
    }

    /** A renewal of a token the partner has already refused is a 403 like any other (ADR-012). */
    @Test
    fun anExpiredTokenCannotRenewItself() = runTest {
        val repository = repositoryAnswering(mutableListOf()) {
            respond(
                content = """{"status":"erro","msg":"Token expirado, por favor gere um novo token"}""",
                status = HttpStatusCode.Forbidden,
                headers = jsonHeaders(),
            )
        }

        assertFailsWith<SmartHomeException.TokenExpired> { repository.renewToken(Token(CURRENT)) }
    }

    /** A `200` without `tempoExpiracao` is not this endpoint's answer, and is not guessed at (E3). */
    @Test
    fun anAnswerWithoutADeadlineIsNotAccepted() = runTest {
        val repository = repositoryAnswering(mutableListOf()) {
            respond(
                content = """{"status":"sucesso","data":{"token":"$RENEWED"}}""",
                headers = jsonHeaders(),
            )
        }

        assertFailsWith<SmartHomeException.UnexpectedResponse> { repository.renewToken(Token(CURRENT)) }
    }

    /** The flat envelope the endpoint was measured to answer with (`docs/api-contract.md` §2). */
    private fun MockRequestHandleScope.renewalAnswer(lifetimeSeconds: Int = 7199): HttpResponseData =
        respond(
            content = """{"status":"sucesso","data":{"token":"$RENEWED","tempoExpiracao":$lifetimeSeconds}}""",
            headers = jsonHeaders(),
        )

    private fun repositoryAnswering(
        requests: MutableList<HttpRequestData>,
        requestCounter: RequestCounter = RequestCounter(),
        answer: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): SessionRepository = SmartHomeSessionRepository(
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
            requestCounter = requestCounter,
            refusedRequests = SessionRefusals(),
        ),
        json = smartHomeJson,
    )

    private companion object {

        /** Token shapes, not credentials: `Ot_` plus a body, assembled so no secret scan can match. */
        const val CURRENT = "Ot_" + "0a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p"
        const val RENEWED = "Ot_" + "5p4o3n2m1l0k9j8i7h6g5f4e3d2c1b0a"
    }
}

private fun jsonHeaders(): Headers =
    headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
