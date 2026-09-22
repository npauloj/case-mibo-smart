package io.github.npauloj.mibosmart.legacy.catalog

/**
 * The Kotlin face of [PartnerCatalog] — and the only type the app calls (ADR-007, ADR-023).
 *
 * It sits in the legacy module rather than in `:shared:data` for one reason: a Java test can only
 * call Kotlin that is on its own classpath, and the reverse direction of the interop — Java calling
 * Kotlin — is exactly what has to be proven. So this class is written as API for both callers:
 * `@JvmStatic` and `@JvmField` give Java the shapes it expects, `@JvmOverloads` spares it the
 * default argument Kotlin has, and `@Throws` makes the SDK's checked exception visible again on the
 * way back out.
 *
 * Nullability is handled here rather than passed on: [PartnerCatalog.describe] is Java, so Kotlin
 * sees `String!` and the app would be one unchecked platform type away from an NPE it could not see
 * in the source.
 */
class CatalogBridge(private val catalog: PartnerCatalog) {

    /**
     * The catalogue's words for [code].
     *
     * @throws UnknownCodeException when the catalogue has none — the app turns that into the raw
     *   code, which is the honest thing to show for a code nobody has a name for.
     */
    @Throws(UnknownCodeException::class)
    fun strictLabel(code: String): String =
        // The platform type made explicit: the SDK documents a non-null return, but only this line
        // is in a position to insist on it.
        catalog.describe(code) ?: throw UnknownCodeException(code)

    /** The same lookup with the fallback already applied — the shape a Java caller reaches for. */
    @JvmOverloads
    fun label(code: String, fallback: String = code): String = catalog.describe(code, fallback) ?: fallback

    companion object {

        /** The bundled catalogue, wrapped once: it is immutable and holds no state worth repeating. */
        @JvmField
        val BUNDLED: CatalogBridge = CatalogBridge(PartnerCatalog.bundled())

        /** For a caller with a catalogue of its own — a partner's newer table, or a test's. */
        @JvmStatic
        fun of(catalog: PartnerCatalog): CatalogBridge = CatalogBridge(catalog)
    }
}
