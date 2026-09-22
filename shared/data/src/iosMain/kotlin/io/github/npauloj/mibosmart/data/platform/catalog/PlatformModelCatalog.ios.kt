package io.github.npauloj.mibosmart.data.platform.catalog

import io.github.npauloj.mibosmart.domain.device.ModelCatalog
import io.github.npauloj.mibosmart.domain.device.RawCodes

/**
 * iOS has no JVM and therefore no `:legacy-catalog`: the codes are shown as the partner sent them.
 *
 * That is a deliberate degradation, not a gap to fill later with a second copy of the table — a
 * translated list maintained twice would be the first thing to drift (ADR-007).
 */
internal actual fun platformModelCatalog(): ModelCatalog = RawCodes
