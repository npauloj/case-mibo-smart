package io.github.npauloj.mibosmart.app.lock

import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
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
    viewModelOf(::LockViewModel)
}
