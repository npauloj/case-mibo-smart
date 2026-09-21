package io.github.npauloj.mibosmart.app.di

import io.github.npauloj.mibosmart.app.AppViewModel
import io.github.npauloj.mibosmart.app.devices.DeviceListViewModel
import io.github.npauloj.mibosmart.app.devices.ListDevices
import io.github.npauloj.mibosmart.app.lock.LoadLock
import io.github.npauloj.mibosmart.app.lock.LockViewModel
import io.github.npauloj.mibosmart.app.session.AuthenticateToken
import io.github.npauloj.mibosmart.app.session.TokenEntryViewModel
import io.github.npauloj.mibosmart.data.di.dataModule
import kotlin.time.Clock
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

/**
 * Use cases and ViewModels; the partner implementation comes from `:shared:data`.
 *
 * @param apiHost the partner host. It is configured per machine (`local.properties` → `BuildConfig`)
 *   and never versioned (ADR-008), so it can only arrive from the platform entry point.
 */
fun appModule(apiHost: String): Module = module {
    includes(dataModule(apiHost))

    // The one clock of the app: "última atualização há X" is read against it (SPEC U3), and a test
    // that has to assert those words needs to choose what "now" is.
    single<Clock> { Clock.System }

    // Routing between features, so it belongs to none of them.
    viewModelOf(::AppViewModel)

    // session
    factoryOf(::AuthenticateToken)
    viewModelOf(::TokenEntryViewModel)

    // devices
    factoryOf(::ListDevices)
    // Not `viewModelOf`: the constructor's second parameter is the clock default, which Koin would
    // try to resolve as a binding of its own.
    viewModel { DeviceListViewModel(listDevices = get()) }

    // lock
    factoryOf(::LoadLock)
    viewModelOf(::LockViewModel)
}

fun initKoin(apiHost: String, appDeclaration: KoinAppDeclaration = {}) {
    startKoin {
        appDeclaration()
        modules(appModule(apiHost))
    }
}
