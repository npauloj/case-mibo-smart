package io.github.npauloj.mibosmart.data.session

import app.cash.turbine.test
import io.github.npauloj.mibosmart.data.remote.EnvelopeReader
import io.github.npauloj.mibosmart.data.remote.HttpClientFactory
import io.github.npauloj.mibosmart.data.remote.RequestCounter
import io.github.npauloj.mibosmart.data.remote.SmartHomeApi
import io.github.npauloj.mibosmart.data.remote.smartHomeJson
import io.github.npauloj.mibosmart.domain.error.SmartHomeException
import io.github.npauloj.mibosmart.domain.session.SessionEndReason
import io.github.npauloj.mibosmart.domain.session.Token
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest

/**
 * SPEC S6's data half: every refusal reaches the guard **carrying the token its request was sent
 * with**, and nothing else does.
 *
 * Driven through the real [SmartHomeApi] rather than by calling `report` directly, because the claim
 * being tested is that the transport reports at all — a guard nobody feeds is the failure mode.
 *
 * Turbine is used here and only here in this file: [SessionRefusals] is a one-shot event stream, not
 * a screen state (`CLAUDE.md`).
 */
class SessionRefusalsTest {

    /** A 401 is announced, with the credential the call carried (ADR-012). */
    @Test
    fun anUnauthorizedRequestReportsTheTokenItWasSentWith() = runTest {
        val refusals = SessionRefusals()
        val api = apiAnswering(refusals, HttpStatusCode.Unauthorized, """"Não autorizado"""")

        refusals.refusals.test {
            assertFailsWith<SmartHomeException.TokenRejected> { api.listDevices(TOKEN, pageSize = 1, page = 1) }

            val refusal = awaitItem()
            assertEquals(TOKEN, refusal.sentWith)
            assertEquals(SessionEndReason.Expired, refusal.reason)
        }
    }

    /** A 403 with the partner's envelope is announced with the sentence it carried (SPEC S3.1). */
    @Test
    fun anExpiredTokenReportsThePartnersSentence() = runTest {
        val refusals = SessionRefusals()
        val api = apiAnswering(
            refusals,
            HttpStatusCode.Forbidden,
            """{"status":"erro","msg":"$PARTNER_SENTENCE"}""",
        )

        refusals.refusals.test {
            assertFailsWith<SmartHomeException.TokenExpired> { api.listDevices(TOKEN, pageSize = 1, page = 1) }

            assertEquals(SessionEndReason.StatedByPartner(PARTNER_SENTENCE), awaitItem().reason)
        }
    }

    /**
     * The 403 that must **not** end the session: the gateway's shape, for an endpoint this account
     * may not call with a perfectly valid token (ADR-012 amended, SPEC E1).
     */
    @Test
    fun aForbiddenEndpointIsNotAnnounced() = runTest {
        val refusals = SessionRefusals()
        val api = apiAnswering(refusals, HttpStatusCode.Forbidden, """{"message":"Forbidden"}""")

        refusals.refusals.test {
            assertFailsWith<SmartHomeException.Forbidden> { api.listDevices(TOKEN, pageSize = 1, page = 1) }

            expectNoEvents()
        }
    }

    /** ADR-006: every call spends one request, counted before the wire and whatever comes back. */
    @Test
    fun everyCallIsCounted() = runTest {
        val counter = RequestCounter()
        val api = apiAnswering(
            SessionRefusals(),
            HttpStatusCode.OK,
            """{"status":"sucesso","data":[]}""",
            counter,
        )

        api.listDevices(TOKEN, pageSize = 1, page = 1)
        api.listDevices(TOKEN, pageSize = 1, page = 1)

        assertEquals(2, counter.requests.value)
    }

    /** A call that never arrives still spent a request from the budget (ADR-006). */
    @Test
    fun aRequestThatNeverArrivesIsStillCounted() = runTest {
        val counter = RequestCounter()
        val api = SmartHomeApi(
            httpClient = HttpClientFactory.create(MockEngine { error("no route to host") }),
            baseUrl = BASE_URL,
            envelopeReader = EnvelopeReader(smartHomeJson),
            requestCounter = counter,
            refusedRequests = SessionRefusals(),
        )

        assertFailsWith<SmartHomeException.Offline> { api.listDevices(TOKEN, pageSize = 1, page = 1) }

        assertEquals(1, counter.requests.value)
    }

    private fun apiAnswering(
        refusals: SessionRefusals,
        status: HttpStatusCode,
        body: String,
        counter: RequestCounter = RequestCounter(),
    ) = SmartHomeApi(
        httpClient = HttpClientFactory.create(
            MockEngine {
                respond(
                    content = body,
                    status = status,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            },
        ),
        baseUrl = BASE_URL,
        envelopeReader = EnvelopeReader(smartHomeJson),
        requestCounter = counter,
        refusedRequests = refusals,
    )

    private companion object {
        const val BASE_URL = "https://api.example.invalid"
        val TOKEN = Token("token-em-uso")

        /** The partner's real 403 body, probed 2026-09-21 (ADR-012). */
        const val PARTNER_SENTENCE = "Token expirado, por favor gere um novo token"
    }
}
