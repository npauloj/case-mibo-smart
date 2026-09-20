package io.github.npauloj.mibosmart.arch

import io.github.baole.konture.architecture
import io.github.baole.konture.should
import io.github.baole.konture.that
import org.junit.jupiter.api.Test

/**
 * Rule 4 — `shared code stays portable` (ADR-005): no `android.*`, `java.*`, `javax.*` or
 * `platform.*` import in any `commonMain`, and no `androidx.*` import outside the JetBrains
 * multiplatform artifacts published under that namespace (Compose, lifecycle, navigation). What
 * needs a platform goes through `expect/actual` in a `platform` package (rule 8).
 *
 * Konture 0.8.4 has no `sourceSet("commonMain") { mustBePlatformIndependent() }` yet (that DSL is
 * documented on `main`, not released), so the rule is expressed on files: every file whose source set
 * is `commonMain` must have no banned import. Same guarantee, explicit list.
 */
class Rule4CommonMainPortableTest {

    private val banned = listOf("android.", "androidx.", "java.", "javax.", "platform.", "kotlinx.cinterop.")

    private val multiplatformAndroidx = listOf(
        "androidx.compose.",
        "androidx.lifecycle.",
        "androidx.navigation.",
        "androidx.savedstate.",
        "androidx.annotation.",
    )

    @Test
    fun `commonMain files import nothing platform specific`() {
        architecture {
            files {
                that { sourceSet?.name == "commonMain" }
                    .should {
                        val offenders = imports.filter { import ->
                            banned.any(import::startsWith) && multiplatformAndroidx.none(import::startsWith)
                        }
                        check(
                            offenders.isEmpty(),
                            "platform-specific import in commonMain (ADR-009 rule 4): $offenders",
                        )
                    }
            }
        }
    }
}
