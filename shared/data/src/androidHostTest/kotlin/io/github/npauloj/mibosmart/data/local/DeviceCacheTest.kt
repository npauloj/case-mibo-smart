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
 * SPEC D10 against a real SQLite file, on the JVM host: the Android and iOS drivers need a
 * device, the JDBC one does not, and the SQL the three of them run is the same file.
 */
class DeviceCacheTest {

    /**
     * SPEC D10: what goes in comes out — including the things the row does not store, because
     * the kind is re-derived from the model and the order is the domain's, not the file's.
     */
    @Test
    fun roundTripsPage() = runTest {
        val driver = inMemoryDriver()
        val cache = SqlDeviceCache(driver)

        cache.write(PAGE, FETCHED_AT)
        val cached = assertNotNull(cache.read())

        assertEquals(FETCHED_AT, cached.fetchedAt)
        assertEquals(PAGE.sortedBy { it.id.value }, cached.devices.sortedBy { it.id.value })
        assertEquals(listOf("iM7-FC", "MFR 1001", "MCA 1002", "iM3-C"), cached.devices.map { it.name })
        val lock = cached.devices.first { it.name == "MFR 1001" }
        assertEquals(DeviceKind.Lock, lock.kind, "the kind is re-derived, so a sub-device must stay one")
        assertEquals(HUB, lock.parent?.value)
        assertEquals(DeviceOrigin.Shared, lock.origin)
        assertEquals(LAST_SEEN, cached.devices.last().lastSeen)
    }

    /**
     * SPEC L1: the product id survives the file, so a cold start can address a lock from the
     * cached page alone — which is what keeps opening a lock at zero requests (ADR-006, SPEC
     * U2).
     */
    @Test
    fun productIdSurvivesTheRoundTrip() = runTest {
        val cache = SqlDeviceCache(inMemoryDriver())

        cache.write(PAGE, FETCHED_AT)
        val cached = assertNotNull(cache.read()).devices.associateBy { it.name }

        assertEquals(LOCK_PRODUCT_ID, assertNotNull(cached["MFR 1001"]).productId)
        assertEquals(HUB_PRODUCT_ID, assertNotNull(cached["MCA 1002"]).productId)
        assertEquals("", assertNotNull(cached["iM7-FC"]).productId)
    }

    /**
     * SPEC L1 and `docs/api-contract.md` §3: the hub's product id survives the file too, on the
     * sub-device's own row — which is what lets a cold start open a lock whose hub is not even
     * on the cached page (ADR-006, SPEC D2).
     */
    @Test
    fun parentProductIdSurvivesTheRoundTrip() = runTest {
        val cache = SqlDeviceCache(inMemoryDriver())

        cache.write(PAGE, FETCHED_AT)
        val cached = assertNotNull(cache.read()).devices.associateBy { it.name }

        assertEquals(HUB_PRODUCT_ID, assertNotNull(cached["MFR 1001"]).parentProductId)
        assertNull(assertNotNull(cached["MCA 1002"]).parentProductId)
        assertNull(assertNotNull(cached["iM7-FC"]).parentProductId)
    }

    /**
     * The migration half of the rule: a row written before the newest column existed would read
     * back with whatever the migration backfills — `''` for `productId` (`2.sqm`), NULL for
     * `parentProductId` (`3.sqm`) — and a missing part of a lock address must never reach a
     * `LockAddress`.
     */
    @Test
    fun rowsWrittenBeforeTheCurrentShapeAreDiscarded() = runTest {
        val driver = inMemoryDriver()
        SqlDeviceCache(driver, schemaVersion = DEVICE_CACHE_SCHEMA_VERSION - 1).write(PAGE, FETCHED_AT)

        val shipped = SqlDeviceCache(driver)

        assertNull(shipped.read(), "a pre-migration row would have surfaced short of an address part")
    }

    /** A cache nobody ever wrote to is a miss, not an empty list — the caller must still fetch. */
    @Test
    fun readsNullBeforeAnythingIsWritten() = runTest {
        assertNull(SqlDeviceCache(inMemoryDriver()).read())
    }

    /**
     * The rule SchemaVersion.kt exists for: rows written with one shape are discarded, not
     * served, when the build that reads them expects another — so the next load refetches
     * instead of rendering (or crashing on) a row of the wrong shape.
     */
    @Test
    fun schemaVersionMismatchDropsAndRefetches() = runTest {
        val driver = inMemoryDriver()
        SqlDeviceCache(driver, schemaVersion = 1).write(PAGE, FETCHED_AT)

        val afterBump = SqlDeviceCache(driver, schemaVersion = 2)

        assertNull(afterBump.read(), "rows of an obsolete shape were served instead of being dropped")
        afterBump.write(PAGE, FETCHED_AT)
        assertEquals(PAGE.size, assertNotNull(SqlDeviceCache(driver, schemaVersion = 2).read()).devices.size)
    }

    /**
     * One JDBC driver, shared by every `SqlDeviceCache` a test builds from it: two caches over
     * one file is exactly the "old build, new build" situation the version rule is about.
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

        /** Placeholders in the shape `docs/api-contract.md` §5 uses — never the account's own. */
        const val HUB_PRODUCT_ID = "<hub-idProduto>"
        const val LOCK_PRODUCT_ID = "<lock-idProduto>"

        /**
         * A hub, the lock hanging off it, and two cameras — the shapes a row can take,
         * including the blank `idProduto` the partner sends for some camera families.
         */
        val PAGE = listOf(
            device("PLACEHOLDER-CAM-NS", "iM7-FC", "iM7-FC"),
            device(HUB, "MCA 1002", "IOT-ZG2-IB", productId = HUB_PRODUCT_ID),
            device(
                id = "PLACEHOLDER-LOCK-NS",
                name = "MFR 1001",
                model = "IOT-MFR1001-IB",
                parent = HUB,
                origin = DeviceOrigin.Shared,
                productId = LOCK_PRODUCT_ID,
                parentProductId = HUB_PRODUCT_ID,
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
            productId: String = "",
            parentProductId: String? = null,
        ) = Device(
            id = DeviceId(id),
            name = name,
            model = model,
            status = if (isOnline) DeviceStatus.Online else DeviceStatus.Offline,
            lastSeen = lastSeen,
            origin = origin,
            kind = DeviceClassifier.classify(model = model, isSubDevice = parent != null),
            parent = parent?.let(::DeviceId),
            productId = productId,
            parentProductId = parentProductId,
        )
    }
}
