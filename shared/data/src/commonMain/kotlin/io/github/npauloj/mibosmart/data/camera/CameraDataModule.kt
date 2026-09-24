package io.github.npauloj.mibosmart.data.camera

import io.github.npauloj.mibosmart.data.local.CapabilityCache
import io.github.npauloj.mibosmart.data.local.InMemoryCapabilityCache
import io.github.npauloj.mibosmart.data.remote.smartHomeJson
import io.github.npauloj.mibosmart.domain.camera.StreamingRepository
import org.koin.core.module.Module
import org.koin.dsl.module

/** The camera feature's partner bindings, beside the code they wire (ADR-014). */
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
