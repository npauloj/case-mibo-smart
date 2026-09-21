package io.github.npauloj.mibosmart.data.camera

import io.github.npauloj.mibosmart.data.local.CapabilityCache
import io.github.npauloj.mibosmart.data.local.InMemoryCapabilityCache
import io.github.npauloj.mibosmart.data.remote.smartHomeJson
import io.github.npauloj.mibosmart.domain.camera.StreamingRepository
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The camera feature's partner bindings, beside the code they wire (ADR-014).
 *
 * The cache is a `single` because "once per camera per install" is a claim about the whole app, not
 * about one screen: a per-screen instance would pay for `funcoes` again on every visit (SPEC V1).
 */
internal val cameraDataModule: Module = module {
    single<CapabilityCache> { InMemoryCapabilityCache() }
    single<StreamingRepository> {
        SmartHomeStreamingRepository(
            api = get(),
            sessionStore = get(),
            json = smartHomeJson,
            capabilities = get(),
        )
    }
}
