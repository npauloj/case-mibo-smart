package io.github.npauloj.mibosmart.data.platform.db

import app.cash.sqldelight.db.SqlDriver
import org.koin.core.scope.Scope

/** The name of the one SQLite file this app owns; the same on both platforms. */
internal const val DATABASE_FILE = "mibosmart.db"

/**
 * Opens the app's SQLite file.
 *
 * A factory rather than a plain `SqlDriver` because the file must be opened on **first use**, not
 * while the Koin graph is being declared: the Android driver needs a `Context`, and a JVM test that
 * resolves the device repository from the real graph has none. It is the rule the token vault
 * already follows (ADR-008).
 */
internal fun interface DatabaseDriverFactory {
    fun create(): SqlDriver
}

/**
 * The platform's driver factory, built from the Koin graph — Android reads its `Context` from there
 * (ADR-008), iOS needs nothing.
 *
 * `expect/actual` in a package named `platform` — rule 8 (ADR-001, ADR-009).
 */
internal expect fun Scope.databaseDriverFactory(): DatabaseDriverFactory
