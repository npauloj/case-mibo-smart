package io.github.npauloj.mibosmart.arch

import io.github.baole.konture.architecture
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/**
 * The permanent negative test (ADR-009): a rule that is *known* to be violated must fail, or the
 * whole suite is decoration. `:shared:app` does depend on `:shared:domain` — asserting the opposite
 * has to throw. If this test ever passes green, the layout the plugin produced is empty or stale.
 */
class NegativeRuleTest {

    @Test
    fun `a rule contradicted by the real module graph fails loudly`() {
        assertThrows(AssertionError::class.java) {
            architecture {
                modules {
                    that().haveNameMatching(Modules.APP)
                        .should().notDependOnModule(Modules.DOMAIN)
                }
            }
        }
    }
}
