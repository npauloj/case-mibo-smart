package io.github.npauloj.mibosmart.app

import io.github.npauloj.mibosmart.domain.device.ModelCatalog

/**
 * A catalogue with exactly the entries a test names (ADR-007).
 *
 * The real one is the partner's Java library and exists on Android only; what a screen has to get
 * right is the same either way — the catalogue's words when there are any, the partner's raw code
 * when there are not. It lives beside [FixedClock] rather than in a feature's fixtures because two
 * features ask the catalogue the same question: the lock history names a `tipo` (SPEC L9) and the
 * device list names a model code (SPEC D5).
 */
internal class FakeModelCatalog(vararg entries: Pair<String, String>) : ModelCatalog {

    private val labels = entries.toMap()

    override fun label(code: String): String = labels[code] ?: code
}

/**
 * A catalogue that answers nothing but a failure — the partner's SDK on a bad day.
 *
 * Android's adapter already turns the SDK's checked exception into the raw code, so nothing the app
 * ships throws from here today. This is what proves the callers do not *depend* on that: a table
 * that starts raising must cost a name and never a screen.
 */
internal object RaisingModelCatalog : ModelCatalog {

    override fun label(code: String): String = throw IllegalStateException("catalogue unavailable")
}
