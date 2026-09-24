package io.github.npauloj.mibosmart.data.catalog

import io.github.npauloj.mibosmart.legacy.catalog.CatalogBridge
import io.github.npauloj.mibosmart.legacy.catalog.PartnerCatalog
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * ADR-007: the Kotlin side of the interop — what the app gets out of the partner's legacy Java
 * SDK.
 */
class ModelCatalogAdapterTest {

    /** SPEC L9: a `tipo` the app has no word of its own for is read out of the partner's catalogue. */
    @Test
    fun mapsKnownEventLabels() {
        val catalog = LegacyModelCatalog()

        assertEquals("Abertura por biometria", catalog.label("biometria"))
        assertEquals("Abertura por senha", catalog.label("senha"))
    }

    /** The SDK's checked exception is an answer, not a failure: the code itself is what is shown. */
    @Test
    fun checkedExceptionFallsBackToRawValue() {
        val empty = LegacyModelCatalog(CatalogBridge.of(PartnerCatalog.builder().build()))

        assertEquals("biometria", empty.label("biometria"))
        assertEquals("", empty.label(""), "a blank code is still not a crash")
    }

    /** The same catalogue names model codes; the history is only the first screen wired to it. */
    @Test
    fun namesModelCodesFromTheSameTable() {
        assertEquals("Central Zigbee", LegacyModelCatalog().label("IOT-ZG2-IB"))
    }
}
