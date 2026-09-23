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
 * Wires the partner implementation. Modules above `:shared:data` only ever see domain interfaces —
 * Ktor, the DTOs and the envelope reader stay internal to this one.
 *
 * **This file holds the transport, which every feature shares, and nothing else.** A feature's
 * repositories live in its own `<feature>DataModule.kt` beside the code they wire, and are listed in
 * [featureModules] below (ADR-014). Two slices adding bindings no longer edit the same lines — the
 * conflict that cost three manual resolutions in wave 2.
 *
 * @param apiHost the partner host, configured locally and never versioned (ADR-008).
 * @param portalHost the partner's **streaming** host — a different address for the same platform,
 *   configured and withheld the same way. The `streaming/…` calls and `criar-fluxo-video` only
 *   work there; see [SmartHomeApi.streamingBaseUrl] for what the api host answers instead.
 */
fun dataModule(apiHost: String, portalHost: String): Module = module {
    // The platform logger, not Ktor's default: on Android the default writes where logcat does
    // not read, so the app logged nothing at all while talking to the API (ADR-012).
    single { HttpClientFactory.create(platformLogger()) }
    single { EnvelopeReader(smartHomeJson) }
    // The budget counter (ADR-006): one instance for the whole run, because "requests spent so far"
    // is a property of the app, not of a screen. The transport is what increments it.
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

/**
 * One entry per feature, alphabetical.
 *
 * A slice that adds a feature appends its module here and creates the file it names. That single line
 * is the only shared edit left: keep the list one-per-line and sorted, so a merge is always "keep
 * both", never a judgement call.
 */
private val featureModules: List<Module> = listOf(
    cameraDataModule,
    catalogDataModule,
    deviceDataModule,
    lockDataModule,
    sessionDataModule,
)
