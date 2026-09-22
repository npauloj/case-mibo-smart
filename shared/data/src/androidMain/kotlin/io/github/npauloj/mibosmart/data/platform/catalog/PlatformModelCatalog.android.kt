package io.github.npauloj.mibosmart.data.platform.catalog

import io.github.npauloj.mibosmart.data.catalog.LegacyModelCatalog
import io.github.npauloj.mibosmart.domain.device.ModelCatalog

/** Android has the JVM, so it has the partner's legacy SDK (ADR-007). */
internal actual fun platformModelCatalog(): ModelCatalog = LegacyModelCatalog()
