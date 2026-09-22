package io.github.npauloj.mibosmart.data.remote

import io.github.npauloj.mibosmart.data.lock.SmartHomeLockRepository
import io.github.npauloj.mibosmart.data.session.InMemorySessionStore
import io.github.npauloj.mibosmart.data.session.SessionRefusals
import io.github.npauloj.mibosmart.data.session.SessionSamples
import io.github.npauloj.mibosmart.domain.device.DeviceId
import io.github.npauloj.mibosmart.domain.error.SmartHomeException
import io.github.npauloj.mibosmart.domain.lock.LockAddress
import io.github.npauloj.mibosmart.domain.lock.LockCommand
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

/**
 * SPEC L1 and L8: the exact bytes the three lock reads put on the wire.
 *
 * The lock is the one device the partner does not address by its own namespace, and its volume read
 * is a documented contract bug (`docs/api-contract.md` §5, §7.4). Both are quarantined here, with a
 * test each, because nothing above `:shared:data` is allowed to know about them (ADR-004).
 */
class LockRequestsTest {

    /**
     * SPEC L8: the Swagger requires `productId`, the property the API reads is `idProduto`, and only
     * sending both worked. Dropping either one silently stops returning the volume.
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

    /**
     * SPEC L1: `<lockNs>_<hubNs>_<hubIdProduto>`, on all three endpoints.
     *
     * The order of the three parts is the contract's. Addressed by its own namespace the same lock
     * answers "Dispositivo não encontrado" (`docs/api-contract.md` §3), so a wrong join does not
     * degrade — it stops working.
     */
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

    /**
     * SPEC L7: `mudar-volume` carries the composite address and the integer, and nothing else.
     *
     * The level on the wire is the partner's 0..3, not the app's name for it: sending `"High"` would
     * be accepted by nothing and reported by no one.
     */
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

    /**
     * SPEC L3: `aberto` is the state the door is **asked for**, and both values are real.
     *
     * The direction is the whole contract (`docs/api-contract.md` §5) and it is the one field in the
     * app whose inversion would open a door instead of locking it, so both commands are put on the
     * wire and read back rather than one being assumed from the other.
     */
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

    /**
     * SPEC L2: the app enables remote opening and has no way to disable it.
     *
     * `habilitar` is fixed at `true` in the request type, so this asserts a property of the code
     * rather than of one call site: there is no argument anywhere that could make these bytes say
     * `false`. It is the guarantee that matters most here — the opposite value would quietly take a
     * door's safety net away.
     */
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

    /** SPEC E3: a level the contract does not document is reported, never rendered as a number. */
    @Test
    fun aVolumeOutsideTheDocumentedRangeIsUnexpected() = runTest {
        val repository = repositoryAnswering(mutableListOf(), signedIn(), volumeLevel = 7)

        assertFailsWith<SmartHomeException.UnexpectedResponse> { repository.readVolume(ADDRESS) }
    }

    /**
     * No session, no lock: there is nothing to retry and the only way forward is a new token, which
     * is what a refused one means to every screen above (SPEC S6).
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
    ) = SmartHomeLockRepository(
        api = SmartHomeApi(
            httpClient = HttpClientFactory.create(
                MockEngine { request ->
                    requests += request
                    respond(
                        content = request.url.encodedPath.answer(volumeLevel),
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                },
            ),
            baseUrl = "https://api.example.invalid",
            envelopeReader = EnvelopeReader(smartHomeJson),
            requestCounter = RequestCounter(),
            refusedRequests = SessionRefusals(),
        ),
        sessionStore = sessionStore,
        json = smartHomeJson,
    )

    /** The payloads observed on 2026-09-20, in the wrapped shape (`docs/api-contract.md` §1.1). */
    private fun String.answer(volumeLevel: Int): String = when {
        endsWith("status-abertura/v1") -> """{"status":"sucesso","data":{"aberto":false}}"""
        endsWith("status-abrir-remoto/v1") -> """{"status":"sucesso","data":{"habilitado":true}}"""
        // The writes' success payload was never probed — it changes a real device
        // (`docs/api-contract.md` §8, open question 4). An envelope with an empty `data` is the
        // least the reader accepts, and the repository reads nothing out of it anyway.
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
