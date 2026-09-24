package io.github.npauloj.mibosmart.data.remote

import io.github.npauloj.mibosmart.data.lock.SmartHomeLockRepository
import io.github.npauloj.mibosmart.data.session.InMemorySessionStore
import io.github.npauloj.mibosmart.data.session.SessionRefusals
import io.github.npauloj.mibosmart.data.session.SessionSamples
import io.github.npauloj.mibosmart.domain.device.DeviceId
import io.github.npauloj.mibosmart.domain.error.SmartHomeException
import io.github.npauloj.mibosmart.domain.lock.LockAddress
import io.github.npauloj.mibosmart.domain.lock.LockCommand
import io.github.npauloj.mibosmart.domain.lock.OpeningEvent
import io.github.npauloj.mibosmart.domain.lock.OpeningKind
import io.github.npauloj.mibosmart.domain.lock.VolumeLevel
import io.github.npauloj.mibosmart.domain.session.SessionStore
import io.github.npauloj.mibosmart.domain.session.Token
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime

/** SPEC L1 and L8: the exact bytes the three lock reads put on the wire. */
class LockRequestsTest {

    /**
     * SPEC L8: the Swagger requires `productId`, the property the API reads is `idProduto`, and
     * only sending both worked. Dropping either one silently stops returning the volume.
     */
    @Test
    fun volumeRequestCarriesBothIds() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val repository = repositoryAnswering(requests, signedIn())

        val volume = repository.readVolume(ADDRESS)

        assertEquals(VolumeLevel.Low, volume)
        val body = requests.single().bodyText()
        assertTrue(body.contains(""""idProduto":"$LOCK_PRODUCT_ID""""), "unexpected request body: $body")
        assertTrue(body.contains(""""productId":"$LOCK_PRODUCT_ID""""), "unexpected request body: $body")
    }

    /** SPEC L1: `<lockNs>_<hubNs>_<hubIdProduto>`, on all three endpoints. */
    @Test
    fun everyReadAddressesTheLockThroughItsHub() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val repository = repositoryAnswering(requests, signedIn())

        repository.readOpenState(ADDRESS)
        repository.readRemoteOpenEnabled(ADDRESS)
        repository.readVolume(ADDRESS)

        assertEquals(
            listOf(
                "/fechaduras/status-abertura/v1",
                "/fechaduras/status-abrir-remoto/v1",
                "/fechaduras/volume/v1",
            ),
            requests.map { it.url.encodedPath },
        )
        requests.forEach { request ->
            val body = request.bodyText()
            assertTrue(
                body.contains(""""ns":"${LOCK_NAMESPACE}_${HUB_NAMESPACE}_$HUB_PRODUCT_ID""""),
                "unexpected request body on ${request.url.encodedPath}: $body",
            )
        }
    }

    /** SPEC L7: `mudar-volume` carries the composite address and the integer, and nothing else. */
    @Test
    fun changeVolumeSendsTheLevelAsTheDocumentedInteger() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val repository = repositoryAnswering(requests, signedIn())

        repository.changeVolume(ADDRESS, VolumeLevel.High)

        assertEquals("/fechaduras/mudar-volume/v1", requests.single().url.encodedPath)
        val body = requests.single().bodyText()
        assertTrue(body.contains(""""ns":"${LOCK_NAMESPACE}_${HUB_NAMESPACE}_$HUB_PRODUCT_ID""""), body)
        assertTrue(body.contains(""""idProduto":"$LOCK_PRODUCT_ID""""), body)
        assertTrue(body.contains(""""volume":3"""), "unexpected request body: $body")
    }

    /** SPEC L3: `aberto` is the state the door is **asked for**, and both values are real. */
    @Test
    fun commandSendsTheRequestedOpenState() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val repository = repositoryAnswering(requests, signedIn())

        repository.command(ADDRESS, LockCommand.Open)
        repository.command(ADDRESS, LockCommand.Close)

        assertEquals(
            listOf("/fechaduras/controle-fechadura/v1", "/fechaduras/controle-fechadura/v1"),
            requests.map { it.url.encodedPath },
        )
        val opening = requests.first().bodyText()
        assertTrue(opening.contains(""""ns":"${LOCK_NAMESPACE}_${HUB_NAMESPACE}_$HUB_PRODUCT_ID""""), opening)
        assertTrue(opening.contains(""""idProduto":"$LOCK_PRODUCT_ID""""), opening)
        assertTrue(opening.contains(""""aberto":true"""), "unexpected request body: $opening")
        assertTrue(requests.last().bodyText().contains(""""aberto":false"""), requests.last().bodyText())
    }

    /** SPEC L2: the app enables remote opening and has no way to disable it. */
    @Test
    fun enableRemoteOpenAlwaysAsksToEnable() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val repository = repositoryAnswering(requests, signedIn())

        repository.enableRemoteOpen(ADDRESS)

        assertEquals("/fechaduras/habilitar-abrir-remoto/v1", requests.single().url.encodedPath)
        val body = requests.single().bodyText()
        assertTrue(body.contains(""""ns":"${LOCK_NAMESPACE}_${HUB_NAMESPACE}_$HUB_PRODUCT_ID""""), body)
        assertTrue(body.contains(""""idProduto":"$LOCK_PRODUCT_ID""""), body)
        assertTrue(body.contains(""""habilitar":true"""), "unexpected request body: $body")
        assertFalse(body.contains("false"), "nothing in this request may ever say false: $body")
    }

    /** SPEC L9: `historico-abertura` is the one lock call **without** `idProduto`. */
    @Test
    fun historyAsksForAQuantityAndNoProductId() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val repository = repositoryAnswering(requests, signedIn())

        repository.readOpeningHistory(ADDRESS, entries = 50)

        assertEquals("/fechaduras/historico-abertura/v1", requests.single().url.encodedPath)
        val body = requests.single().bodyText()
        assertTrue(body.contains(""""ns":"${LOCK_NAMESPACE}_${HUB_NAMESPACE}_$HUB_PRODUCT_ID""""), body)
        assertTrue(body.contains(""""quantidade":50"""), "unexpected request body: $body")
        assertFalse(body.contains(""""idProduto":"""), "historico-abertura takes no product id: $body")
    }

    /**
     * SPEC L9: `tempoLocal` is wall-clock time, and a `tipo` nobody has seen survives the
     * mapping.
     */
    @Test
    fun historyEntriesKeepTheirWallClockTimeAndUnknownTypes() = runTest {
        val repository = repositoryAnswering(mutableListOf(), signedIn())

        val history = repository.readOpeningHistory(ADDRESS, entries = 50)

        assertEquals(
            listOf(
                OpeningEvent(LocalDateTime(2026, 9, 18, 10, 27, 35), OpeningKind.Remote, "APP"),
                OpeningEvent(LocalDateTime(2026, 9, 18, 10, 24, 29), OpeningKind.Local, null),
                OpeningEvent(LocalDateTime(2026, 9, 18, 9, 1, 2), OpeningKind.Unknown("biometria"), null),
            ),
            history,
        )
    }

    /** SPEC E3: a `tempoLocal` that is not the documented shape is reported, never guessed at. */
    @Test
    fun anUnreadableHistoryTimestampIsUnexpected() = runTest {
        val repository = repositoryAnswering(mutableListOf(), signedIn(), historyTime = "ontem à tarde")

        assertFailsWith<SmartHomeException.UnexpectedResponse> {
            repository.readOpeningHistory(ADDRESS, entries = 50)
        }
    }

    /** SPEC E3: a level the contract does not document is reported, never rendered as a number. */
    @Test
    fun aVolumeOutsideTheDocumentedRangeIsUnexpected() = runTest {
        val repository = repositoryAnswering(mutableListOf(), signedIn(), volumeLevel = 7)

        assertFailsWith<SmartHomeException.UnexpectedResponse> { repository.readVolume(ADDRESS) }
    }

    /**
     * No session, no lock: there is nothing to retry and the only way forward is a new token,
     * which is what a refused one means to every screen above (SPEC S6).
     */
    @Test
    fun aMissingSessionIsTokenRejected() = runTest {
        val repository = repositoryAnswering(mutableListOf(), InMemorySessionStore())

        assertFailsWith<SmartHomeException.TokenRejected> { repository.readOpenState(ADDRESS) }
    }

    private suspend fun signedIn(): SessionStore =
        InMemorySessionStore().apply { write(Token("um-token"), SessionSamples.IssuedAt) }

    private fun HttpRequestData.bodyText(): String = (body as TextContent).text

    private fun repositoryAnswering(
        requests: MutableList<HttpRequestData>,
        sessionStore: SessionStore,
        volumeLevel: Int = 1,
        historyTime: String = "20260918T102735",
    ) = SmartHomeLockRepository(
        api = SmartHomeApi(
            httpClient = HttpClientFactory.create(
                MockEngine { request ->
                    requests += request
                    respond(
                        content = request.url.encodedPath.answer(volumeLevel, historyTime),
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                },
            ),
            baseUrl = "https://api.example.invalid",
            streamingBaseUrl = "https://portal.example.invalid",
            envelopeReader = EnvelopeReader(smartHomeJson),
            requestCounter = RequestCounter(),
            refusedRequests = SessionRefusals(),
        ),
        sessionStore = sessionStore,
        json = smartHomeJson,
    )

    /** The payloads observed on 2026-09-20, in the wrapped shape (`docs/api-contract.md` §1.1). */
    private fun String.answer(volumeLevel: Int, historyTime: String): String = when {
        endsWith("status-abertura/v1") -> """{"status":"sucesso","data":{"aberto":false}}"""
        endsWith("status-abrir-remoto/v1") -> """{"status":"sucesso","data":{"habilitado":true}}"""
        endsWith("historico-abertura/v1") -> """
            {"status":"sucesso","data":[
              {"tempoLocal":"$historyTime","nome":"APP","tipo":"usuarioRemoto"},
              {"tempoLocal":"20260918T102429","nome":"","tipo":"interno"},
              {"tempoLocal":"20260918T090102","tipo":"biometria"}
            ]}
        """.trimIndent()

        endsWith("mudar-volume/v1") || endsWith("habilitar-abrir-remoto/v1") ||
            endsWith("controle-fechadura/v1") ->
            """{"status":"sucesso","data":{}}"""

        else -> """{"status":"sucesso","data":{"volume":$volumeLevel}}"""
    }

    private companion object {
        /** Placeholders in the shape of the contract: a real lock's identifiers open a real door. */
        const val LOCK_NAMESPACE = "<lock-ns>"
        const val HUB_NAMESPACE = "<hub-ns>"
        const val HUB_PRODUCT_ID = "<hub-idProduto>"
        const val LOCK_PRODUCT_ID = "<lock-idProduto>"

        val ADDRESS = LockAddress(
            lock = DeviceId(LOCK_NAMESPACE),
            hub = DeviceId(HUB_NAMESPACE),
            hubProductId = HUB_PRODUCT_ID,
            lockProductId = LOCK_PRODUCT_ID,
        )
    }
}
