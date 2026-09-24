package io.github.npauloj.mibosmart.data.device

import io.github.npauloj.mibosmart.data.local.DeviceCache
import io.github.npauloj.mibosmart.data.local.FakeDeviceCache
import io.github.npauloj.mibosmart.data.remote.EnvelopeReader
import io.github.npauloj.mibosmart.data.remote.HttpClientFactory
import io.github.npauloj.mibosmart.data.remote.RequestCounter
import io.github.npauloj.mibosmart.data.remote.SmartHomeApi
import io.github.npauloj.mibosmart.data.remote.smartHomeJson
import io.github.npauloj.mibosmart.data.session.InMemorySessionStore
import io.github.npauloj.mibosmart.data.session.SessionRefusals
import io.github.npauloj.mibosmart.data.session.SessionSamples
import io.github.npauloj.mibosmart.domain.device.DeviceKind
import io.github.npauloj.mibosmart.domain.device.DeviceOrigin
import io.github.npauloj.mibosmart.domain.device.DeviceRepository
import io.github.npauloj.mibosmart.domain.device.DeviceStatus
import io.github.npauloj.mibosmart.domain.device.OriginFilter
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

/**
 * SPEC D1, D3, D5, D6, D8 (no cache) and U3, asserted against the real transport with
 * `MockEngine`.
 */
class ListDevicesTest {

    /** SPEC D1: page 1, `tamanhoPagina: 20`, `origem: todos`, one call, bearer credential. */
    @Test
    fun firstPageUsesDefaults() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val repository = repositoryAnswering(requests) { respondWithDevices("[]") }

        repository.firstPage()

        assertEquals(1, requests.size, "the account pays per request (ADR-006)")
        val request = requests.single()
        assertEquals("/produtos/listar-dispositivos/v1", request.url.encodedPath)
        assertEquals("Bearer um-token", request.headers[HttpHeaders.Authorization])
        assertEquals(
            """{"tamanhoPagina":20,"pagina":1,"origem":"todos"}""",
            (request.body as TextContent).text,
        )
    }

    /** SPEC D3: an empty page 1 is an empty list, not a failure — the screen decides what to say. */
    @Test
    fun emptyFirstPageIsEmptyState() = runTest {
        val repository = repositoryAnswering(mutableListOf()) { respondWithDevices("[]") }

        assertEquals(emptyList(), repository.firstPage())
    }

    /** SPEC D8 without a cache: a call that never arrives is offline, not "resposta inesperada". */
    @Test
    fun networkFailureShowsError() = runTest {
        val repository = repositoryAnswering(mutableListOf()) { error("no route to host") }

        assertFailsWith<SmartHomeException.Offline> { repository.firstPage() }
    }

    /**
     * SPEC D5, D6, U3 and U8 in one pass over a page shaped like the account's: the payload is
     * classified, the sub-device keeps its parent, the compact `ultimaVezOnline` becomes an
     * instant, and the order is the app's rather than the partner's.
     */
    @Test
    fun aPageIsClassifiedDatedAndOrdered() = runTest {
        val repository = repositoryAnswering(mutableListOf()) { respondWithDevices(PAGE) }

        val devices = repository.firstPage()

        assertEquals(listOf("MFR 1001", "MCA 1002", "iM3-C"), devices.map { it.name })
        val lock = devices.first()
        assertEquals(DeviceKind.Lock, lock.kind)
        assertEquals(DeviceStatus.Online, lock.status)
        assertEquals(DeviceOrigin.Linked, lock.origin)
        assertEquals("PLACEHOLDER-HUB-NS", lock.parent?.value)
        val offlineCamera = devices.last()
        assertEquals(DeviceKind.Camera, offlineCamera.kind)
        assertEquals("2026-09-18T13:27:04Z", assertNotNull(offlineCamera.lastSeen).toString())
    }

    /**
     * SPEC L1: `idProduto` reaches the domain, because the lock edge needs the hub's **and**
     * the lock's to address a door (`docs/api-contract.md` §5) and the list is the only
     * response that carries either.
     */
    @Test
    fun carriesTheProductId() = runTest {
        val repository = repositoryAnswering(mutableListOf()) { respondWithDevices(PAGE) }

        val devices = repository.firstPage().associateBy { it.name }

        assertEquals("PLACEHOLDER-LOCK-ID", assertNotNull(devices["MFR 1001"]).productId)
        assertEquals("PLACEHOLDER-HUB-ID", assertNotNull(devices["MCA 1002"]).productId)
        assertEquals(
            "",
            assertNotNull(devices["iM3-C"]).productId,
            "`idProduto` is \"\" on some cameras and must survive as blank, not become a guess",
        )
    }

    /**
     * SPEC L1: the hub's `idProduto` reaches the domain **from the sub-device's own row**,
     * which is the field that lets a lock be addressed without its hub being loaded
     * (`idProdutoDispositivoPai`, `docs/api-contract.md` §3).
     */
    @Test
    fun carriesTheParentProductId() = runTest {
        val repository = repositoryAnswering(mutableListOf()) { respondWithDevices(PAGE) }

        val devices = repository.firstPage().associateBy { it.name }

        assertEquals("PLACEHOLDER-HUB-ID", assertNotNull(devices["MFR 1001"]).parentProductId)
        assertNull(assertNotNull(devices["MCA 1002"]).parentProductId, "a hub hangs off nothing")
        assertNull(assertNotNull(devices["iM3-C"]).parentProductId, "a camera hangs off nothing")
    }

    /** SPEC U3: a device the partner never saw online has no timestamp, and that must not throw. */
    @Test
    fun aDeviceWithoutLastSeenKeepsANullInstant() = runTest {
        val repository = repositoryAnswering(mutableListOf()) {
            respondWithDevices(
                """[{"ns":"PLACEHOLDER-NS-1","modelo":"iM7-FC","nome":"iM7-FC",
                   "status":"offline","origem":"vinculado"}]""",
            )
        }

        assertNull(repository.firstPage().single().lastSeen)
    }

    /** SPEC E3: a `data` that is not a list of devices is a named failure, never a crash. */
    @Test
    fun aPayloadThatIsNotADeviceListIsUnexpected() = runTest {
        val repository = repositoryAnswering(mutableListOf()) { respondWithDevices("""{"volume":1}""") }

        assertFailsWith<SmartHomeException.UnexpectedResponse> { repository.firstPage() }
    }

    /** SPEC D10: a page that arrives is written to the cache, dated, so the next start is free. */
    @Test
    fun aSuccessfulPageIsCachedWithItsTimestamp() = runTest {
        val cache = FakeDeviceCache()
        val repository = repositoryAnswering(mutableListOf(), cache = cache) { respondWithDevices(PAGE) }

        repository.firstPage()

        val cached = assertNotNull(repository.cachedPage(), "nothing was written to the cache")
        assertEquals(listOf("MFR 1001", "MCA 1002", "iM3-C"), cached.devices.map { it.name })
        assertEquals(FETCHED_AT, cached.fetchedAt)
    }

    /** ADR-006: with no session there is nothing to authenticate with, so no request is spent. */
    @Test
    fun withoutASessionNoRequestIsSpent() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val repository = repositoryAnswering(requests, token = null) { respondWithDevices("[]") }

        assertFailsWith<SmartHomeException.TokenRejected> { repository.firstPage() }
        assertTrue(requests.isEmpty(), "a call was made with no credential to send")
    }

    /**
     * SPEC D2 and D4 on the wire: the chip picks the `origem`, the page number is the partner's
     * own, and a page exactly as long as the one requested is what offers the next.
     */
    @Test
    fun aFilteredNextPageAsksForItByNumber() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val repository = repositoryAnswering(requests) { respondWithDevices(fullPage()) }

        val page = repository.page(OriginFilter.Linked, page = 2)

        assertEquals(
            """{"tamanhoPagina":20,"pagina":2,"origem":"vinculados"}""",
            (requests.single().body as TextContent).text,
        )
        assertTrue(page.hasMore, "a page of exactly `tamanhoPagina` devices must offer the next one")
    }

    /** SPEC D2: a page shorter than `tamanhoPagina` is the last one, whatever the filter. */
    @Test
    fun aShortPageIsTheLastPage() = runTest {
        val repository = repositoryAnswering(mutableListOf()) { respondWithDevices(PAGE) }

        assertFalse(repository.page(OriginFilter.Shared, page = 1).hasMore)
    }

    /** SPEC D10: only page 1 is cached — a later page would open the app halfway down the list. */
    @Test
    fun aLaterPageIsNotCached() = runTest {
        val cache = FakeDeviceCache()
        val repository = repositoryAnswering(mutableListOf(), cache = cache) { respondWithDevices(PAGE) }

        repository.page(OriginFilter.All, page = 2)

        assertNull(repository.cachedPage(), "page 2 was written over the cold-start page")
    }

    /** Twenty devices, which is what `tamanhoPagina` asks for — the "there may be more" boundary. */
    private fun fullPage(): String = (1..20).joinToString(prefix = "[", postfix = "]") { index ->
        """{"ns":"PLACEHOLDER-NS-$index","modelo":"iM7-FC","nome":"iM7-FC $index",
           "status":"online","origem":"vinculado"}"""
    }

    private fun MockRequestHandleScope.respondWithDevices(data: String) = respond(
        content = """{"status":"sucesso","data":$data}""",
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
    )

    /** Page 1 of everything: what every test that is not about paging is asking for (SPEC D1). */
    private suspend fun DeviceRepository.firstPage() = page(OriginFilter.All, page = 1).devices

    private suspend fun repositoryAnswering(
        requests: MutableList<HttpRequestData>,
        token: Token? = Token("um-token"),
        cache: DeviceCache = FakeDeviceCache(),
        answer: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): DeviceRepository = SmartHomeDeviceRepository(
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
        sessionStore = InMemorySessionStore().apply { token?.let { write(it, SessionSamples.IssuedAt) } },
        cache = cache,
        now = { FETCHED_AT },
    )

    private companion object {
        /** The clock the repository dates a cached page with (SPEC D10). */
        val FETCHED_AT = Instant.parse("2026-09-21T12:00:00Z")

        /**
         * A page shaped like `docs/api-contract.md` §3, returned out of order on purpose: a
         * lock under its hub, the hub itself, and an offline camera with a compact
         * `ultimaVezOnline`.
         */
        val PAGE = """
            [
              {"ns":"PLACEHOLDER-CAM-NS","modelo":"iM3-C","nome":"iM3-C","status":"offline",
               "origem":"vinculado","subdispositivo":false,"idProduto":"",
               "ultimaVezOnline":"20260918T132704Z"},
              {"ns":"PLACEHOLDER-HUB-NS","modelo":"IOT-ZG2-IB","nome":"MCA 1002","status":"online",
               "origem":"vinculado","subdispositivo":false,"idProduto":"PLACEHOLDER-HUB-ID"},
              {"ns":"PLACEHOLDER-LOCK-NS","modelo":"IOT-MFR1001-IB","nome":"MFR 1001","status":"online",
               "origem":"vinculado","subdispositivo":true,"idProduto":"PLACEHOLDER-LOCK-ID",
               "dispositivoPai":"PLACEHOLDER-HUB-NS","idProdutoDispositivoPai":"PLACEHOLDER-HUB-ID"}
            ]
        """.trimIndent()
    }
}
