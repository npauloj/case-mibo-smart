package io.github.npauloj.mibosmart.data.local

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.npauloj.mibosmart.data.local.db.MiboSmartDatabase
import io.github.npauloj.mibosmart.data.platform.db.DatabaseDriverFactory
import io.github.npauloj.mibosmart.domain.device.Device
import io.github.npauloj.mibosmart.domain.device.DeviceClassifier
import io.github.npauloj.mibosmart.domain.device.DeviceId
import io.github.npauloj.mibosmart.domain.device.DeviceKind
import io.github.npauloj.mibosmart.domain.device.DeviceOrigin
import io.github.npauloj.mibosmart.domain.device.DeviceStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

/**
 * SPEC D10 against a real SQLite file, on the JVM host: the Android and iOS drivers need a device,
 * the JDBC one does not, and the SQL the three of them run is the same file.
 *
 * Every serial here is a **placeholder** — the test account's `ns` never enters a versioned file
 * (ADR-008).
 */
class DeviceCacheTest {

    /**
     * SPEC D10: what goes in comes out — including the things the row does not store, because the
     * kind is re-derived from the model and the order is the domain's, not the file's.
     */
    @Test
    fun roundTripsPage() = runTest {
        val driver = inMemoryDriver()
        val cache = SqlDeviceCache(driver)

        cache.write(PAGE, FETCHED_AT)
        val cached = assertNotNull(cache.read())

        assertEquals(FETCHED_AT, cached.fetchedAt)
        // Every field of every device, not a sample of them: a column silently dropped on the way in
        // is the failure this test exists to catch.
        assertEquals(PAGE.sortedBy { it.id.value }, cached.devices.sortedBy { it.id.value })
        // Read back in `orderedForList()` order — online actionable, then the rest online, then
        // offline — whatever order the rows were written or SELECTed in.
        assertEquals(listOf("iM7-FC", "MFR 1001", "MCA 1002", "iM3-C"), cached.devices.map { it.name })
        val lock = cached.devices.first { it.name == "MFR 1001" }
        assertEquals(DeviceKind.Lock, lock.kind, "the kind is re-derived, so a sub-device must stay one")
        assertEquals(HUB, lock.parent?.value)
        assertEquals(DeviceOrigin.Shared, lock.origin)
        assertEquals(LAST_SEEN, cached.devices.last().lastSeen)
    }

    /** A cache nobody ever wrote to is a miss, not an empty list — the caller must still fetch. */
    @Test
    fun readsNullBeforeAnythingIsWritten() = runTest {
        assertNull(SqlDeviceCache(inMemoryDriver()).read())
    }

    /**
     * The rule SchemaVersion.kt exists for: rows written with one shape are discarded, not served,
     * when the build that reads them expects another — so the next load refetches instead of
     * rendering (or crashing on) a row of the wrong shape.
     */
    @Test
    fun schemaVersionMismatchDropsAndRefetches() = runTest {
        // One file, two builds: the same driver, read by a cache that expects the next version.
        val driver = inMemoryDriver()
        SqlDeviceCache(driver, schemaVersion = 1).write(PAGE, FETCHED_AT)

        val afterBump = SqlDeviceCache(driver, schemaVersion = 2)

        assertNull(afterBump.read(), "rows of an obsolete shape were served instead of being dropped")
        // And the new version is recorded, so the page written next survives the following read.
        afterBump.write(PAGE, FETCHED_AT)
        assertEquals(PAGE.size, assertNotNull(SqlDeviceCache(driver, schemaVersion = 2).read()).devices.size)
    }

    /**
     * One JDBC driver, shared by every `SqlDeviceCache` a test builds from it: two caches over one
     * file is exactly the "old build, new build" situation the version rule is about.
     */
    private fun inMemoryDriver(): DatabaseDriverFactory {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MiboSmartDatabase.Schema.create(driver)
        return DatabaseDriverFactory { driver }
    }

    private companion object {
        val FETCHED_AT = Instant.parse("2026-09-21T12:00:00Z")
        val LAST_SEEN = Instant.parse("2026-09-18T13:27:04Z")
        const val HUB = "PLACEHOLDER-HUB-NS"

        /** A hub, the lock hanging off it, and an offline camera — the shapes a row can take. */
        val PAGE = listOf(
            device("PLACEHOLDER-CAM-NS", "iM7-FC", "iM7-FC"),
            device(HUB, "MCA 1002", "IOT-ZG2-IB"),
            device(
                id = "PLACEHOLDER-LOCK-NS",
                name = "MFR 1001",
                model = "IOT-MFR1001-IB",
                parent = HUB,
                origin = DeviceOrigin.Shared,
            ),
            device("PLACEHOLDER-CAM-NS-2", "iM3-C", "iM3-C", isOnline = false, lastSeen = LAST_SEEN),
        )

        fun device(
            id: String,
            name: String,
            model: String,
            isOnline: Boolean = true,
            lastSeen: Instant? = null,
            parent: String? = null,
            origin: DeviceOrigin = DeviceOrigin.Linked,
        ) = Device(
            id = DeviceId(id),
            name = name,
            model = model,
            status = if (isOnline) DeviceStatus.Online else DeviceStatus.Offline,
            lastSeen = lastSeen,
            origin = origin,
            kind = DeviceClassifier.classify(model = model, isSubDevice = parent != null),
            parent = parent?.let(::DeviceId),
        )
    }
}
