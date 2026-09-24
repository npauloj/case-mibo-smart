package io.github.npauloj.mibosmart.domain.device

/**
 * The partner's own words for one of its codes — a device model code, a lock opening type
 * (ADR-007).
 */
interface ModelCatalog {

    /** What [code] is called, or [code] itself when the catalogue has no entry for it. */
    fun label(code: String): String
}

/** The catalogue that knows nothing: every code is its own label. */
object RawCodes : ModelCatalog {

    override fun label(code: String): String = code
}
