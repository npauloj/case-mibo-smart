package io.github.npauloj.mibosmart.data.platform.db

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import io.github.npauloj.mibosmart.data.local.db.MiboSmartDatabase
import org.koin.core.scope.Scope

/**
 * The `Context` is read from the graph when the file is opened, not while the module is
 * declared: nothing should touch the disk because a binding exists (same rule as the vault,
 * ADR-008).
 */
internal actual fun Scope.databaseDriverFactory(): DatabaseDriverFactory =
    DatabaseDriverFactory { AndroidSqliteDriver(MiboSmartDatabase.Schema, get<Context>(), DATABASE_FILE) }
