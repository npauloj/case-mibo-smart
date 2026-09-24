package io.github.npauloj.mibosmart.data.local

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.npauloj.mibosmart.data.local.db.MiboSmartDatabase
import io.github.npauloj.mibosmart.data.platform.db.DatabaseDriverFactory
import io.github.npauloj.mibosmart.domain.device.OriginFilter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

/**
 * SPEC **D4** against a real SQLite file, on the JVM host: "remembered across launches" is a
 * claim about what survives the process, so it is worth nothing asserted against a variable.
 */
class PreferencesTest {

    /** The chip written by one instance is the chip the next one opens on — the whole of D4. */
    @Test
    fun filterPersists() = runTest {
        val driver = inMemoryDriver()

        SqlDeviceListPreferences(driver).writeOriginFilter(OriginFilter.Shared)

        assertEquals(OriginFilter.Shared, SqlDeviceListPreferences(driver).readOriginFilter())
    }

    /** The last choice wins; a preference is a value, not a log. */
    @Test
    fun theLastChoiceReplacesTheOneBeforeIt() = runTest {
        val preferences = SqlDeviceListPreferences(inMemoryDriver())

        preferences.writeOriginFilter(OriginFilter.Shared)
        preferences.writeOriginFilter(OriginFilter.Linked)

        assertEquals(OriginFilter.Linked, preferences.readOriginFilter())
    }

    /** SPEC D4: nothing chosen yet is `todos`, the filter the list opens on by default. */
    @Test
    fun withoutAChoiceTheListOpensOnEverything() = runTest {
        assertEquals(OriginFilter.All, SqlDeviceListPreferences(inMemoryDriver()).readOriginFilter())
    }

    /** A value this build does not know — written by an older or newer one — reads as the default. */
    @Test
    fun anUnknownStoredValueFallsBackToEverything() = runTest {
        val driver = inMemoryDriver()
        MiboSmartDatabase(driver.create()).deviceQueries
            .setPreference("device_list.origin_filter", "Hubs")

        assertEquals(OriginFilter.All, SqlDeviceListPreferences(driver).readOriginFilter())
    }

    /**
     * One JDBC driver, shared by every instance a test builds from it: two instances over one
     * file is exactly the "this launch, the next launch" situation the preference exists for.
     */
    private fun inMemoryDriver(): DatabaseDriverFactory {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MiboSmartDatabase.Schema.create(driver)
        return DatabaseDriverFactory { driver }
    }
}
