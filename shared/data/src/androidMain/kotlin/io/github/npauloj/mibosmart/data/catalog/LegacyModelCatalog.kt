package io.github.npauloj.mibosmart.data.catalog

import io.github.npauloj.mibosmart.domain.device.ModelCatalog
import io.github.npauloj.mibosmart.legacy.catalog.CatalogBridge
import io.github.npauloj.mibosmart.legacy.catalog.UnknownCodeException

/** The domain's [ModelCatalog] answered by the partner's legacy Java SDK (ADR-007). */
internal class LegacyModelCatalog(private val bridge: CatalogBridge = CatalogBridge.BUNDLED) : ModelCatalog {

    override fun label(code: String): String =
        try {
            bridge.strictLabel(code)
        } catch (unknown: UnknownCodeException) {
            code
        }
}
