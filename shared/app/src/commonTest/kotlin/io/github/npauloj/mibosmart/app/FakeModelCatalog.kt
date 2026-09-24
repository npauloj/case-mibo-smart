package io.github.npauloj.mibosmart.app

import io.github.npauloj.mibosmart.domain.device.ModelCatalog

/** A catalogue with exactly the entries a test names (ADR-007). */
internal class FakeModelCatalog(vararg entries: Pair<String, String>) : ModelCatalog {

    private val labels = entries.toMap()

    override fun label(code: String): String = labels[code] ?: code
}

/** A catalogue that answers nothing but a failure — the partner's SDK on a bad day. */
internal object RaisingModelCatalog : ModelCatalog {

    override fun label(code: String): String = throw IllegalStateException("catalogue unavailable")
}
