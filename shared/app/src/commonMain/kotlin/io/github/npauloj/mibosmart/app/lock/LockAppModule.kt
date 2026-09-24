package io.github.npauloj.mibosmart.app.lock

import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** The lock feature's use cases and ViewModels, beside the code they wire (ADR-014). */
internal val lockAppModule: Module = module {
    factoryOf(::LoadLock)
    factoryOf(::ChangeVolume)
    factoryOf(::EnableRemoteOpen)
    factoryOf(::ToggleLock)
    factoryOf(::OpeningHistory)
    viewModelOf(::LockViewModel)
    viewModel { OpeningHistoryViewModel(openingHistory = get(), clock = get(), catalog = get()) }
}
