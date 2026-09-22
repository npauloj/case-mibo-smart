package io.github.npauloj.mibosmart.arch

import io.github.baole.konture.architecture
import org.junit.jupiter.api.Test

/**
 * ADR-007's guardrail — `the legacy Java SDK is reached through :shared:data and nowhere else`.
 *
 * `:legacy-catalog` is a JVM library: anything that depends on it stops being able to build for iOS.
 * `:shared:data` can afford that because only its Android source set sees the module, and the iOS
 * one answers with the raw codes instead. `:shared:domain` and `:shared:app` cannot — the first is
 * pure Kotlin by rule 1, the second builds the iOS framework — so the edge that would break the iOS
 * build is the one this rule refuses, in the Gradle graph, before a compiler ever reports it.
 *
 * The module itself depends on nothing of ours: it stands in for a library the partner ships, and a
 * partner's SDK does not know this app's domain.
 */
class LegacyCatalogBoundaryTest {

    @Test
    fun `domain and shared app never depend on the legacy java catalogue`() {
        architecture {
            modules {
                that().haveNameMatching(Modules.DOMAIN)
                    .should().notDependOnModule(Modules.LEGACY_CATALOG)
            }
            modules {
                that().haveNameMatching(Modules.APP)
                    .should().notDependOnModule(Modules.LEGACY_CATALOG)
            }
        }
    }

    @Test
    fun `the legacy java catalogue depends on no module of this app`() {
        architecture {
            modules {
                that().haveNameMatching(Modules.LEGACY_CATALOG)
                    .should().notDependOnModules(
                        Modules.DOMAIN,
                        Modules.DATA,
                        Modules.APP,
                        Modules.ANDROID_APP,
                    )
            }
        }
    }
}
