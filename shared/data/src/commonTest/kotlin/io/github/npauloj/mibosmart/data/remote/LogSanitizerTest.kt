package io.github.npauloj.mibosmart.data.remote

import io.github.npauloj.mibosmart.data.session.SessionRefusals
import io.github.npauloj.mibosmart.domain.session.Token
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.logging.Logger
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/** SPEC S9 / ADR-008: the credential must not survive a round trip through the client's log. */
class LogSanitizerTest {

    private class RecordingLogger : Logger {
        val lines = mutableListOf<String>()
        override fun log(message: String) {
            lines += message
        }
    }

    @Test
    fun authorizationHeaderRedacted() = runTest {
        val logger = RecordingLogger()
        val engine = MockEngine {
            respond(
                content = """{"status":"sucesso","data":[]}""",
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val api = SmartHomeApi(
            httpClient = HttpClientFactory.create(engine, logger),
            baseUrl = "https://api.example.invalid",
            streamingBaseUrl = "https://portal.example.invalid",
            envelopeReader = EnvelopeReader(smartHomeJson),
            requestCounter = RequestCounter(),
            refusedRequests = SessionRefusals(),
        )

        api.listDevices(Token(SECRET), pageSize = 1, page = 1)

        val log = logger.lines.joinToString(separator = "\n")
        assertTrue(log.contains(HttpHeaders.Authorization), "the log never mentioned the header: $log")
        assertFalse(log.contains(SECRET), "the token reached the log: $log")
    }

    private companion object {
        const val SECRET = "token-que-nao-pode-vazar"
    }
}
