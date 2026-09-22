package io.github.npauloj.mibosmart.app.lock

import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * The lock feature's use cases and ViewModels, beside the code they wire (ADR-014).
 *
 * `:shared:app`'s aggregate lists this module in `di/AppModules.kt`; nothing else references it.
 */
internal val lockAppModule: Module = module {
    factoryOf(::LoadLock)
    factoryOf(::ChangeVolume)
    factoryOf(::EnableRemoteOpen)
    factoryOf(::ToggleLock)
    factoryOf(::OpeningHistory)
    viewModelOf(::LockViewModel)
    // Not `viewModelOf`: the constructor's last parameter is the device's time zone, a default
    // rather than a binding, which Koin would otherwise try to resolve.
    viewModel { OpeningHistoryViewModel(openingHistory = get(), clock = get()) }
}
