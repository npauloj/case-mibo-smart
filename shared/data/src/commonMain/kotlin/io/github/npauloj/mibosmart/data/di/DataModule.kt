package io.github.npauloj.mibosmart.data.di

import io.github.npauloj.mibosmart.data.camera.cameraDataModule
import io.github.npauloj.mibosmart.data.catalog.catalogDataModule
import io.github.npauloj.mibosmart.data.device.deviceDataModule
import io.github.npauloj.mibosmart.data.lock.lockDataModule
import io.github.npauloj.mibosmart.data.platform.log.platformLogger
import io.github.npauloj.mibosmart.data.remote.EnvelopeReader
import io.github.npauloj.mibosmart.data.remote.HttpClientFactory
import io.github.npauloj.mibosmart.data.remote.RequestCounter
import io.github.npauloj.mibosmart.data.remote.SmartHomeApi
import io.github.npauloj.mibosmart.data.remote.smartHomeJson
import io.github.npauloj.mibosmart.data.session.sessionDataModule
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Wires the partner implementation. Modules above `:shared:data` only ever see domain
 * interfaces — Ktor, the DTOs and the envelope reader stay internal to this one.
 * @param apiHost the partner host, configured locally and never versioned (ADR-008).
 * @param portalHost the partner's **streaming** host — a different address for the same
 * platform, configured and withheld the same way.
 */
fun dataModule(apiHost: String, portalHost: String): Module = module {
    single { HttpClientFactory.create(platformLogger()) }
    single { EnvelopeReader(smartHomeJson) }
    single { RequestCounter() }
    single {
        SmartHomeApi(
            httpClient = get(),
            baseUrl = apiHost,
            streamingBaseUrl = portalHost,
            envelopeReader = get(),
            requestCounter = get(),
            refusedRequests = get(),
        )
    }

    includes(featureModules)
}

/** One entry per feature, alphabetical. */
private val featureModules: List<Module> = listOf(
    cameraDataModule,
    catalogDataModule,
    deviceDataModule,
    lockDataModule,
    sessionDataModule,
)
