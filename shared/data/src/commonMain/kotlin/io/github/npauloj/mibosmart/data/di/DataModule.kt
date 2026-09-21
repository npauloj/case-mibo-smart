package io.github.npauloj.mibosmart.data.di

import io.github.npauloj.mibosmart.data.remote.EnvelopeReader
import io.github.npauloj.mibosmart.data.remote.HttpClientFactory
import io.github.npauloj.mibosmart.data.remote.SmartHomeApi
import io.github.npauloj.mibosmart.data.remote.smartHomeJson
import io.github.npauloj.mibosmart.data.session.InMemorySessionStore
import io.github.npauloj.mibosmart.data.session.SmartHomeSessionRepository
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
    single { HttpClientFactory.create() }
    single { EnvelopeReader(smartHomeJson) }
    single { SmartHomeApi(httpClient = get(), baseUrl = apiHost, envelopeReader = get()) }
    single<SessionRepository> { SmartHomeSessionRepository(get()) }
    single<SessionStore> { InMemorySessionStore() }
}
