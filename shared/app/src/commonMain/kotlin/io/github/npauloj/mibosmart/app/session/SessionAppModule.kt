package io.github.npauloj.mibosmart.app.session

import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * The session feature's use cases and ViewModels, beside the code they wire (ADR-014).
 *
 * `:shared:app`'s aggregate lists this module in `di/AppModules.kt`; nothing else references it.
 */
internal val sessionAppModule: Module = module {
    factoryOf(::AuthenticateToken)
    viewModelOf(::TokenEntryViewModel)
}
