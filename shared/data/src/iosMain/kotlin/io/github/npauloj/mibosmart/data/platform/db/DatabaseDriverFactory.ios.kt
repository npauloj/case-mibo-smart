package io.github.npauloj.mibosmart.data.platform.db

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import io.github.npauloj.mibosmart.data.local.db.MiboSmartDatabase
import org.koin.core.scope.Scope

/** The file lands in the app's Application Support directory, which iOS backs up — no credential is in it (ADR-008). */
internal actual fun Scope.databaseDriverFactory(): DatabaseDriverFactory =
    DatabaseDriverFactory { NativeSqliteDriver(MiboSmartDatabase.Schema, DATABASE_FILE) }
