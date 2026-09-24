package io.github.npauloj.mibosmart.data.platform.db

import app.cash.sqldelight.db.SqlDriver
import org.koin.core.scope.Scope

/** The name of the one SQLite file this app owns; the same on both platforms. */
internal const val DATABASE_FILE = "mibosmart.db"

/** Opens the app's SQLite file. */
internal fun interface DatabaseDriverFactory {
    fun create(): SqlDriver
}

/**
 * The platform's driver factory, built from the Koin graph — Android reads its `Context` from
 * there (ADR-008), iOS needs nothing.
 */
internal expect fun Scope.databaseDriverFactory(): DatabaseDriverFactory
