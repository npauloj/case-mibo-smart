package io.github.npauloj.mibosmart.arch

import io.github.baole.konture.architecture
import org.junit.jupiter.api.Test

/**
 * Rule 1 — `domain must not depend on frameworks, persistence or the outer modules` (ADR-001, ADR-004).
 *
 * Two views of the same decision: the Gradle graph (`:shared:domain` has no edge to `:shared:data`
 * or `:shared:app`) and the source (nothing under `..domain..` reaches Ktor, Koin, SQLDelight,
 * Compose, Android or Java).
 */
class Rule1DomainPurityTest {

    @Test
    fun `domain module depends on no other project module`() {
        architecture {
            modules {
                that().haveNameMatching(Modules.DOMAIN)
                    .should().notDependOnModules(Modules.DATA, Modules.APP, Modules.ANDROID_APP)
            }
        }
    }

    @Test
    fun `domain classes only reach the domain itself, kotlin and the two allowed kotlinx libraries`() {
        architecture {
            classes {
                that().resideInAPackage("${Modules.DOMAIN_PACKAGE}..")
                    .should().onlyDependOnClassesInAnyPackage(
                        "${Modules.DOMAIN_PACKAGE}..",
                        "kotlin..",
                        "kotlinx.coroutines..",
                        "kotlinx.datetime..",
                    )
            }
        }
    }
}
