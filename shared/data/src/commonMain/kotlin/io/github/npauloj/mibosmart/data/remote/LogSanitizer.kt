package io.github.npauloj.mibosmart.data.remote

import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.HttpHeaders

/**
 * Logging that cannot leak the credential (ADR-008, SPEC S9).
 *
 * `LogLevel.HEADERS`, never `ALL`: body logging would print the token on the renewal call. And the
 * `Authorization` header itself is replaced by Ktor's placeholder before anything is written, so the
 * redaction does not depend on anyone remembering it at the call site.
 */
internal fun HttpClientConfig<*>.installSanitizedLogging(logger: Logger) {
    install(Logging) {
        this.logger = logger
        level = LogLevel.HEADERS
        sanitizeHeader { header -> header.equals(HttpHeaders.Authorization, ignoreCase = true) }
    }
}
