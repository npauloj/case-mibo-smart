package io.github.npauloj.mibosmart.arch

import java.io.File
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Rule 8 — `platform bridges live in one place` (ADR-001, ADR-005, ADR-008): every `expect` and
 * `actual` declaration sits in a package whose path contains a `platform` segment —
 * `data.platform.*` (token vault) or `app.<feature>.platform` (video surface) — and never in the
 * platform apps, which cannot host actuals for modules they depend on.
 *
 * Implemented as a plain source scan on purpose: it must keep working if the architecture-test
 * library is swapped (ADR-009 names Konsist as plan B), and the rule is about a keyword, not a
 * dependency edge. The scan covers every `src/<sourceSet>/kotlin` folder of the shared modules and
 * of `:androidApp`.
 */
class Rule8PlatformBridgesTest {

    private val declaration = Regex("""^\s*(?:(?:public|internal|private)\s+)?(?:expect|actual)\s+""")

    @Test
    fun `expect and actual declarations only live in packages named platform`() {
        val offenders = kotlinSourceFiles()
            .filter { file -> file.useLines { lines -> lines.any(declaration::containsMatchIn) } }
            .filterNot { file -> file.invariantSeparatorsPath.contains("/platform/") }
            .map { it.relativeTo(repoRoot).invariantSeparatorsPath }
            .sorted()
            .toList()

        assertTrue(offenders.isEmpty()) {
            "expect/actual outside a `platform` package (ADR-009 rule 8):\n" +
                offenders.joinToString("\n") { "  $it" }
        }
    }

    private val repoRoot: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").exists() }

    private fun kotlinSourceFiles(): Sequence<File> =
        listOf("shared/domain", "shared/data", "shared/app", "androidApp")
            .map { File(repoRoot, "$it/src") }
            .filter { it.isDirectory }
            .asSequence()
            .flatMap { src -> src.walkTopDown().filter { it.isFile && it.extension == "kt" } }
}
