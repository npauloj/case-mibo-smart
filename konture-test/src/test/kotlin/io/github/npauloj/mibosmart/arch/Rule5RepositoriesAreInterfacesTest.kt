package io.github.npauloj.mibosmart.arch

import io.github.baole.konture.architecture
import org.junit.jupiter.api.Test

/**
 * Rule 5 — `repositories declared in domain must be interfaces` (ADR-002, ADR-004): the domain owns
 * the contract, `:shared:data` owns the partner implementation. A concrete `*Repository` in the
 * domain would drag the partner's wire format inward.
 *
 * `allowEmpty()`: the walking skeleton has no repository yet; the rule must not fail for lack of
 * subjects, it must fail on the first concrete one.
 */
class Rule5RepositoriesAreInterfacesTest {

    @Test
    fun `every Repository in the domain package is an interface`() {
        architecture {
            classes {
                allowEmpty()
                    .that().resideInAPackage("${Modules.DOMAIN_PACKAGE}..")
                    .and().haveNameEndingWith("Repository")
                    .should().beInterfaces()
            }
        }
    }
}
