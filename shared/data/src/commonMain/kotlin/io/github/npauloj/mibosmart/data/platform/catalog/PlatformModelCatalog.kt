package io.github.npauloj.mibosmart.data.platform.catalog

import io.github.npauloj.mibosmart.domain.device.ModelCatalog

/**
 * The catalogue this platform can actually read (ADR-007).
 *
 * Android has the partner's legacy Java SDK; iOS has no build of it and answers with the raw codes.
 * That difference is a platform bridge like any other, so it lives in a package named `platform` —
 * architecture rule 8 (ADR-001, ADR-009) — and the adapter it returns stays an ordinary class.
 */
internal expect fun platformModelCatalog(): ModelCatalog
