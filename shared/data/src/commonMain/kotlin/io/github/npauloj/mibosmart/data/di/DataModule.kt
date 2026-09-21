package io.github.npauloj.mibosmart.data.di

import io.github.npauloj.mibosmart.data.remote.EnvelopeReader
import io.github.npauloj.mibosmart.data.remote.HttpClientFactory
import io.github.npauloj.mibosmart.data.remote.SmartHomeApi
import io.github.npauloj.mibosmart.data.platform.log.platformLogger
import io.github.npauloj.mibosmart.data.platform.vault.secureTokenStore
import io.github.npauloj.mibosmart.data.remote.smartHomeJson
import io.github.npauloj.mibosmart.data.session.SmartHomeSessionRepository
import io.github.npauloj.mibosmart.data.session.VaultSessionStore
import io.github.npauloj.mibosmart.domain.session.SessionRepository
import io.github.npauloj.mibosmart.domain.session.SessionStore
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Wires the partner implementation. Modules above `:shared:data` only ever see domain interfaces —
 * Ktor, the DTOs and the envelope reader stay internal to this one.
 *
 * @param apiHost the partner host, configured locally and never versioned (ADR-008).
 */
fun dataModule(apiHost: String): Module = module {
    // The platform logger, not Ktor's default: on Android the default writes where logcat does
    // not read, so the app logged nothing at all while talking to the API (ADR-012).
    single { HttpClientFactory.create(platformLogger()) }
    single { EnvelopeReader(smartHomeJson) }
    single { SmartHomeApi(httpClient = get(), baseUrl = apiHost, envelopeReader = get()) }
    single<SessionRepository> { SmartHomeSessionRepository(get()) }
    single<SessionStore> { VaultSessionStore(secureTokenStore()) }
}
