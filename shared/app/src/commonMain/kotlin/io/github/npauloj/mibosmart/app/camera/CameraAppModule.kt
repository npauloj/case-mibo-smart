package io.github.npauloj.mibosmart.app.camera

import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * The camera feature's use cases and ViewModels, beside the code they wire (ADR-014).
 *
 * `:shared:app`'s aggregate lists this module in `di/AppModules.kt` and binds the [LiveVideoSwitch]
 * the entry point configured; nothing else references either.
 */
internal val cameraAppModule: Module = module {
    factoryOf(::WatchLiveVideo)
    factoryOf(::EndStreamSession)
    viewModelOf(::LiveVideoViewModel)
}
