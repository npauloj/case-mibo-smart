package io.github.npauloj.mibosmart.app.di

import io.github.npauloj.mibosmart.app.AppViewModel
import io.github.npauloj.mibosmart.app.devices.deviceAppModule
import io.github.npauloj.mibosmart.app.lock.lockAppModule
import io.github.npauloj.mibosmart.app.session.sessionAppModule
import io.github.npauloj.mibosmart.data.di.dataModule
import kotlin.time.Clock
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

/**
 * Use cases and ViewModels; the partner implementation comes from `:shared:data`.
 *
 * **This file holds what belongs to no single feature and nothing else** — a feature's use cases and
 * ViewModels live in its own `<feature>AppModule.kt`, listed in [featureModules] (ADR-014).
 *
 * @param apiHost the partner host. It is configured per machine (`local.properties` → `BuildConfig`)
 *   and never versioned (ADR-008), so it can only arrive from the platform entry point.
 */
fun appModule(apiHost: String): Module = module {
    includes(dataModule(apiHost))

    // The one clock of the app: "última atualização há X" is read against it (SPEC U3), and a test
    // that has to assert those words needs to choose what "now" is. It belongs to no single feature.
    single<Clock> { Clock.System }

    // Routing between features, so it belongs to none of them.
    viewModelOf(::AppViewModel)

    includes(featureModules)
}

/**
 * One entry per feature, alphabetical.
 *
 * A slice that adds a feature appends its module here and creates the file it names. That single line
 * is the only shared edit left: keep the list one-per-line and sorted, so a merge is always "keep
 * both", never a judgement call.
 */
private val featureModules: List<Module> = listOf(
    deviceAppModule,
    lockAppModule,
    sessionAppModule,
)

fun initKoin(apiHost: String, appDeclaration: KoinAppDeclaration = {}) {
    startKoin {
        appDeclaration()
        modules(appModule(apiHost))
    }
}
