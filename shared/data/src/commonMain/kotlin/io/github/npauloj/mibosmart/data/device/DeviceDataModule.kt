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

/** The device feature's partner bindings, beside the code they wire (ADR-014). */
internal val deviceDataModule: Module = module {
    single<DatabaseDriverFactory> {
        val driver = lazy { databaseDriverFactory().create() }
        DatabaseDriverFactory { driver.value }
    }
    single<DeviceCache> { SqlDeviceCache(get()) }
    single<DeviceListPreferences> { SqlDeviceListPreferences(get()) }
    single<DeviceRepository> { SmartHomeDeviceRepository(api = get(), sessionStore = get(), cache = get()) }
}
