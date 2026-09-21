package io.github.npauloj.mibosmart.app.devices

import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * The device feature's use cases and ViewModels, beside the code they wire (ADR-014).
 *
 * `:shared:app`'s aggregate lists this module in `di/AppModules.kt`; nothing else references it.
 */
internal val deviceAppModule: Module = module {
    factoryOf(::ListDevices)
    // Not `viewModelOf`: the constructor's second parameter is the clock default, which Koin would
    // try to resolve as a binding of its own.
    viewModel { DeviceListViewModel(listDevices = get()) }
}
