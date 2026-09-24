package io.github.npauloj.mibosmart.app.camera

import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** The camera feature's use cases and ViewModels, beside the code they wire (ADR-014). */
internal val cameraAppModule: Module = module {
    factoryOf(::WatchLiveVideo)
    factoryOf(::EndStreamSession)
    viewModelOf(::LiveVideoViewModel)
}
