package io.github.npauloj.mibosmart.app.session

import io.github.npauloj.mibosmart.domain.session.SessionGuard
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** The session feature's use cases and ViewModels, beside the code they wire (ADR-014). */
internal val sessionAppModule: Module = module {
    factoryOf(::AuthenticateToken)
    factoryOf(::Logout)
    factoryOf(::RenewToken)
    factoryOf(::SessionStartup)
    factoryOf(::SessionGuard)
    viewModelOf(::AccountViewModel)
    viewModelOf(::TokenEntryViewModel)
}
