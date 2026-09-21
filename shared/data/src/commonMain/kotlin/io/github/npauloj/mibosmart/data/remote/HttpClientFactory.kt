package io.github.npauloj.mibosmart.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.DEFAULT
import io.ktor.client.plugins.logging.Logger
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/** Lenient about unknown keys on purpose: the partner returns fields this case does not model. */
internal val smartHomeJson: Json = Json { ignoreUnknownKeys = true }

/**
 * The one HTTP client of the app. The engine is the platform default (OkHttp / Darwin) in production
 * and `MockEngine` in tests; nothing else about the client differs between the two, so the contract
 * tests exercise the same configuration the app ships.
 */
internal object HttpClientFactory {

    fun create(logger: Logger = Logger.DEFAULT): HttpClient = HttpClient { configure(logger) }

    fun create(engine: HttpClientEngine, logger: Logger = Logger.DEFAULT): HttpClient =
        HttpClient(engine) { configure(logger) }

    private fun HttpClientConfig<*>.configure(logger: Logger) {
        // The API answers 200 even for its own errors (docs/api-contract.md §1): a non-2xx status is
        // a transport accident, and the envelope reader — not Ktor — decides what an answer means.
        expectSuccess = false
        install(ContentNegotiation) { json(smartHomeJson) }
        installSanitizedLogging(logger)
    }
}
