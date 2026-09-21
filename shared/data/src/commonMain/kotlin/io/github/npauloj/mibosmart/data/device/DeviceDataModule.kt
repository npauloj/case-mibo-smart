package io.github.npauloj.mibosmart.data.device

import io.github.npauloj.mibosmart.domain.device.DeviceRepository
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The device feature's partner bindings, beside the code they wire (ADR-014).
 *
 * `:shared:data`'s aggregate lists this module in `di/DataModule.kt`; nothing else references it.
 */
internal val deviceDataModule: Module = module {
    single<DeviceRepository> { SmartHomeDeviceRepository(api = get(), sessionStore = get()) }
}
