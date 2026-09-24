package io.github.npauloj.mibosmart.data.catalog

import io.github.npauloj.mibosmart.data.platform.catalog.platformModelCatalog
import io.github.npauloj.mibosmart.domain.device.ModelCatalog
import org.koin.core.module.Module
import org.koin.dsl.module

/** The code catalogue's binding, beside the code it wires (ADR-014). */
internal val catalogDataModule: Module = module {
    single<ModelCatalog> { platformModelCatalog() }
}
