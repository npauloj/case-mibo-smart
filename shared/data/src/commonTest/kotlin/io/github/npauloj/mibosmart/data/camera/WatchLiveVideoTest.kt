package io.github.npauloj.mibosmart.data.camera

import io.github.npauloj.mibosmart.data.local.InMemoryCapabilityCache
import io.github.npauloj.mibosmart.data.remote.EnvelopeReader
import io.github.npauloj.mibosmart.data.remote.HttpClientFactory
import io.github.npauloj.mibosmart.data.remote.SmartHomeApi
import io.github.npauloj.mibosmart.data.remote.smartHomeJson
import io.github.npauloj.mibosmart.data.session.InMemorySessionStore
import io.github.npauloj.mibosmart.data.session.SessionSamples
import io.github.npauloj.mibosmart.domain.camera.StreamingRepository
import io.github.npauloj.mibosmart.domain.device.DeviceId
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * SPEC V1, V2, V6 and V8 asserted against the real transport with `MockEngine`.
 *
 * It lives in `:shared:data` because every claim here is a claim about bytes on the wire and about
 * the cache that decides whether they are sent at all — the same split as `ListDevicesTest`. The
 * states a user sees from these outcomes are the app module's `WatchLiveVideoTest`.
 *
 * **No test here opens a real session.** The account's streaming quota is shared and finite; every
 * `criar-fluxo-video`, `funcoes` and `encerrar-sessao` below is answered by `MockEngine`.
 *
 * The serial is a **placeholder** — no identifier of the test account enters a versioned file (ADR-008).
 */
class WatchLiveVideoTest {

    /** SPEC V2: the documented body, exactly — `stream_gb`, `streamId` and `canalVideo` included. */
    @Test
    fun exactCreateRequest() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val repository = repositoryAnswering(requests) { respondWith(SESSION_PAYLOAD) }

        val session = repository.openSession(CAMERA)

        assertEquals(1, requests.size, "the account pays per request (ADR-006)")
        val request = requests.single()
        assertEquals("/cameras/criar-fluxo-video/v1", request.url.encodedPath)
        assertEquals("Bearer um-token", request.headers[HttpHeaders.Authorization])
        assertEquals(
            """{"ns":"PLACEHOLDER-CAM-NS","stream_gb":0.5,"canalVideo":0,"streamId":1}""",
            (request.body as TextContent).text,
        )
        assertEquals("sessao-1", session.id, "the id `encerrar-sessao` needs was dropped on the way in")
        assertEquals("https://portal.example.invalid/stream/x", session.url)
    }

    /** SPEC V1: `funcoes` is paid for once per camera, however often the camera is opened. */
    @Test
    fun capabilityCheckedOnce() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val repository = repositoryAnswering(requests) { respondWith("""{"funcoes":"AudioTalk,RTSV2,RTSV1"}""") }

        assertTrue(repository.announcesLiveVideo(CAMERA))
        assertTrue(repository.announcesLiveVideo(CAMERA))

        assertEquals(1, requests.size, "the second visit to the same camera paid for `funcoes` again")
        assertEquals("/produtos/funcoes/v1", requests.single().url.encodedPath)
        assertEquals("""{"ns":"PLACEHOLDER-CAM-NS"}""", (requests.single().body as TextContent).text)
    }

    /** SPEC V1: a camera without `RTSV` says so — and the "no" is cached like any other answer. */
    @Test
    fun missingRtsvIsReported() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val repository = repositoryAnswering(requests) { respondWith("""{"funcoes":"BreathingLight,CloudStorage"}""") }

        assertFalse(repository.announcesLiveVideo(CAMERA))
        assertFalse(repository.announcesLiveVideo(CAMERA))

        assertEquals(1, requests.size, "a cached \"no\" costs the same request as a cached \"yes\"")
    }

    /**
     * SPEC V6: quota exhaustion has to be recognisable, because it is the one outcome with no retry.
     *
     * The contract documents it as HTTP 402 while §1 says the platform answers 200 with the outcome in
     * the body (§8, open question 5). Both readings are covered, so whichever one the real account
     * produces on the day, the screen says "Cota de streaming esgotada" and not "resposta inesperada".
     */
    @Test
    fun quotaIsClassifiedFromStatusAndFromBody() = runTest {
        val byStatus = repositoryAnswering(mutableListOf()) {
            respond("Quota de streaming insuficiente", HttpStatusCode.PaymentRequired)
        }
        assertFailsWith<SmartHomeException.QuotaExceeded> { byStatus.openSession(CAMERA) }

        val byBody = repositoryAnswering(mutableListOf()) {
            respond(
                content = """{"status":"erro","msg":"Quota de streaming insuficiente"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        assertFailsWith<SmartHomeException.QuotaExceeded> { byBody.openSession(CAMERA) }
    }

    /** SPEC V8: closing names the session, or the account keeps paying for it. */
    @Test
    fun endSessionNamesTheSession() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val repository = repositoryAnswering(requests) { respondWith("{}") }

        repository.closeSession("sessao-1")

        assertEquals("/streaming/encerrar-sessao/v1", requests.single().url.encodedPath)
        assertEquals("""{"session_id":"sessao-1"}""", (requests.single().body as TextContent).text)
    }

    /** ADR-006: with no session there is nothing to authenticate with, so no request is spent. */
    @Test
    fun withoutASessionNoRequestIsSpent() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val repository = repositoryAnswering(requests, token = null) { respondWith(SESSION_PAYLOAD) }

        assertFailsWith<SmartHomeException.TokenRejected> { repository.openSession(CAMERA) }
        assertTrue(requests.isEmpty(), "a call was made with no credential to send")
    }

    private fun MockRequestHandleScope.respondWith(data: String) = respond(
        content = """{"status":"sucesso","data":$data}""",
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
    )

    private suspend fun repositoryAnswering(
        requests: MutableList<HttpRequestData>,
        token: Token? = Token("um-token"),
        answer: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): StreamingRepository = SmartHomeStreamingRepository(
        api = SmartHomeApi(
            httpClient = HttpClientFactory.create(
                MockEngine { request ->
                    requests += request
                    answer(request)
                },
            ),
            baseUrl = "https://api.example.invalid",
            envelopeReader = EnvelopeReader(smartHomeJson),
        ),
        sessionStore = InMemorySessionStore().apply { token?.let { write(it, SessionSamples.IssuedAt) } },
        json = smartHomeJson,
        capabilities = InMemoryCapabilityCache(),
    )

    private companion object {
        val CAMERA = DeviceId("PLACEHOLDER-CAM-NS")

        val SESSION_PAYLOAD = """
            {"url":"https://portal.example.invalid/stream/x",
             "monitor_url":"https://portal.example.invalid/monitor_stream.html?session_id=sessao-1",
             "session_id":"sessao-1","quota_gb":0.5}
        """.trimIndent()
    }
}
