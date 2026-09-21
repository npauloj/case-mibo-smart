package io.github.npauloj.mibosmart.data.device

import io.github.npauloj.mibosmart.data.local.DeviceCache
import io.github.npauloj.mibosmart.data.local.SqlDeviceCache
import io.github.npauloj.mibosmart.data.local.SqlDeviceListPreferences
import io.github.npauloj.mibosmart.data.platform.db.DatabaseDriverFactory
import io.github.npauloj.mibosmart.data.platform.db.databaseDriverFactory
import io.github.npauloj.mibosmart.domain.device.DeviceListPreferences
import io.github.npauloj.mibosmart.domain.device.DeviceRepository
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The device feature's partner bindings, beside the code they wire (ADR-014).
 *
 * `:shared:data`'s aggregate lists this module in `di/DataModule.kt`; nothing else references it.
 */
internal val deviceDataModule: Module = module {
    // One SQLite file for everything that reads it: the platform factory opens a *new* connection on
    // every `create()`, so handing it to both the cache and the preferences would put two
    // connections — and two migration runs — on one file. The `lazy` keeps the promise the factory
    // exists for: the file is opened on first use, not because a binding was declared (ADR-006).
    single<DatabaseDriverFactory> {
        val driver = lazy { databaseDriverFactory().create() }
        DatabaseDriverFactory { driver.value }
    }
    single<DeviceCache> { SqlDeviceCache(get()) }
    single<DeviceListPreferences> { SqlDeviceListPreferences(get()) }
    single<DeviceRepository> { SmartHomeDeviceRepository(api = get(), sessionStore = get(), cache = get()) }
}
