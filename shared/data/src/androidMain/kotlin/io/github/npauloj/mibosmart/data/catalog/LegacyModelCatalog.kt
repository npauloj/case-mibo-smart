package io.github.npauloj.mibosmart.data.catalog

import io.github.npauloj.mibosmart.domain.device.ModelCatalog
import io.github.npauloj.mibosmart.legacy.catalog.CatalogBridge
import io.github.npauloj.mibosmart.legacy.catalog.UnknownCodeException

/**
 * The domain's [ModelCatalog] answered by the partner's legacy Java SDK (ADR-007).
 *
 * It lives in `androidMain` because `:legacy-catalog` is a JVM library and there is no iOS build of
 * it; iOS keeps [io.github.npauloj.mibosmart.domain.device.RawCodes] and shows the codes as they
 * came, which is exactly what this app did before the catalogue existed.
 *
 * The one rule here is the SDK's checked exception: an unknown code is not a failure worth
 * surfacing — it is a code with no name yet — so it becomes the raw value, which is the same answer
 * iOS gives. Removing this class removes the feature and nothing else (ADR-007's kill switch).
 */
internal class LegacyModelCatalog(private val bridge: CatalogBridge = CatalogBridge.BUNDLED) : ModelCatalog {

    override fun label(code: String): String =
        try {
            bridge.strictLabel(code)
        } catch (unknown: UnknownCodeException) {
            code
        }
}
