package io.github.npauloj.mibosmart.arch

import io.github.baole.konture.architecture
import org.junit.jupiter.api.Test

/**
 * Rule 2 — `dependencies only point inward` + `module graph must not contain cycles` (ADR-001).
 *
 * domain ← data ← app ← androidApp. The compiler already rejects a UI→Ktor import; this rule rejects
 * the Gradle edge an agent would add "so the file compiles".
 */
class Rule2InwardDependenciesTest {

    @Test
    fun `data never depends on app or the android host`() {
        architecture {
            modules {
                that().haveNameMatching(Modules.DATA)
                    .should().notDependOnModules(Modules.APP, Modules.ANDROID_APP)
            }
        }
    }

    @Test
    fun `shared app never depends on the android host`() {
        architecture {
            modules {
                that().haveNameMatching(Modules.APP)
                    .should().notDependOnModule(Modules.ANDROID_APP)
            }
        }
    }

    @Test
    fun `module graph is free of cycles`() {
        architecture {
            modules {
                that().haveNameStartingWith(":")
                    .should().beFreeOfCycles()
            }
        }
    }
}
