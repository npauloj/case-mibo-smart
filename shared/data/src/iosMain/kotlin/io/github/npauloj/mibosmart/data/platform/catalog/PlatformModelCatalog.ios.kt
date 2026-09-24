package io.github.npauloj.mibosmart.data.platform.catalog

import io.github.npauloj.mibosmart.domain.device.ModelCatalog
import io.github.npauloj.mibosmart.domain.device.RawCodes

/**
 * iOS has no JVM and therefore no `:legacy-catalog`: the codes are shown as the partner sent
 * them.
 */
internal actual fun platformModelCatalog(): ModelCatalog = RawCodes
