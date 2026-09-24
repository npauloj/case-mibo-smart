package io.github.npauloj.mibosmart.data.platform.catalog

import io.github.npauloj.mibosmart.domain.device.ModelCatalog

/** The catalogue this platform can actually read (ADR-007). */
internal expect fun platformModelCatalog(): ModelCatalog
