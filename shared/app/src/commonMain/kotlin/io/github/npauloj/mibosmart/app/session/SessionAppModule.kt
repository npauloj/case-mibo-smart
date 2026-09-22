package io.github.npauloj.mibosmart.app.session

import io.github.npauloj.mibosmart.domain.session.SessionGuard
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
    factoryOf(::Logout)
    factoryOf(::RenewToken)
    factoryOf(::SessionStartup)
    // A domain policy, wired here because this is the feature that owns it: the guard has no state of
    // its own, so a factory is one object per use rather than one to keep alive (ADR-014).
    factoryOf(::SessionGuard)
    viewModelOf(::AccountViewModel)
    viewModelOf(::TokenEntryViewModel)
}
