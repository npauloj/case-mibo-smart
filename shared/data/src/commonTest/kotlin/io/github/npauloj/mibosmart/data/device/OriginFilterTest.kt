package io.github.npauloj.mibosmart.data.device

import io.github.npauloj.mibosmart.data.remote.wireValue
import io.github.npauloj.mibosmart.domain.device.OriginFilter
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * SPEC **D4**: the three chips, and the three Portuguese words the partner expects for them
 * (`docs/api-contract.md` §3).
 *
 * A table over `entries` rather than three assertions: a filter added to the domain without a wire
 * word is a request the partner would reject at runtime, and this is where it is caught instead.
 */
class OriginFilterTest {

    @Test
    fun mapsToWireValues() {
        val expected = mapOf(
            OriginFilter.All to "todos",
            OriginFilter.Linked to "vinculados",
            OriginFilter.Shared to "compartilhados",
        )

        assertEquals(expected.keys, OriginFilter.entries.toSet(), "a filter has no `origem` on the wire")
        expected.forEach { (filter, wire) -> assertEquals(wire, filter.wireValue, "$filter") }
    }
}
