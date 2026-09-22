package io.github.npauloj.mibosmart.data.local

import io.github.npauloj.mibosmart.data.local.db.CachedDevice
import io.github.npauloj.mibosmart.data.local.db.MiboSmartDatabase
import io.github.npauloj.mibosmart.data.platform.db.DatabaseDriverFactory
import io.github.npauloj.mibosmart.domain.device.CachedDevices
import io.github.npauloj.mibosmart.domain.device.Device
import io.github.npauloj.mibosmart.domain.device.DeviceClassifier
import io.github.npauloj.mibosmart.domain.device.DeviceId
import io.github.npauloj.mibosmart.domain.device.DeviceOrigin
import io.github.npauloj.mibosmart.domain.device.DeviceStatus
import io.github.npauloj.mibosmart.domain.device.orderedForList
import kotlin.time.Instant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The last device page the partner answered with, kept on disk so a cold start costs nothing and an
 * offline user still sees a list (ADR-006, SPEC D8, D10, U2).
 *
 * An interface because the ViewModel's behaviour must be provable without a SQLite file, and because
 * the timestamp — not the rows — is what the screen actually needs from here.
 */
internal interface DeviceCache {

    /** The stored page, or null when nothing was stored or the stored shape is obsolete. */
    suspend fun read(): CachedDevices?

    /** Replaces the stored page wholesale: a page is only ever meaningful in one piece. */
    suspend fun write(devices: List<Device>, fetchedAt: Instant)
}

/**
 * The SQLDelight cache (ADR-006).
 *
 * @param schemaVersion the shape the rows are written with. A parameter rather than a direct read of
 *   [DEVICE_CACHE_SCHEMA_VERSION] so a test can bump it, which is the only way to prove the rule that
 *   protects every future change to `Device.sq`.
 */
internal class SqlDeviceCache(
    driverFactory: DatabaseDriverFactory,
    private val schemaVersion: Int = DEVICE_CACHE_SCHEMA_VERSION,
) : DeviceCache {

    private val queries by lazy { MiboSmartDatabase(driverFactory.create()).deviceQueries }

    private val mutex = Mutex()
    private var schemaChecked = false

    override suspend fun read(): CachedDevices? = withCurrentSchema {
        val rows = queries.selectAll().executeAsList().ifEmpty { return@withCurrentSchema null }
        CachedDevices(
            // Ordering is the domain's, never the file's: SELECT without ORDER BY promises nothing,
            // and `DeviceOrdering` exists so the cache cannot invent an order of its own.
            devices = rows.map(CachedDevice::toDevice).orderedForList(),
            fetchedAt = Instant.fromEpochMilliseconds(rows.first().fetchedAt),
        )
    }

    override suspend fun write(devices: List<Device>, fetchedAt: Instant) = withCurrentSchema {
        queries.transaction {
            queries.deleteAll()
            devices.forEach { device ->
                queries.insert(
                    id = device.id.value,
                    name = device.name,
                    model = device.model,
                    online = device.isOnline.toLong(),
                    lastSeen = device.lastSeen?.toEpochMilliseconds(),
                    origin = if (device.origin == DeviceOrigin.Linked) LINKED else SHARED,
                    parentId = device.parent?.value,
                    // The partner's `subdispositivo` is not on the domain model, and it does not need
                    // to be: a sub-device is exactly a device that hangs from a hub, which is the one
                    // thing `parent` records (Device.parent, api-contract §5).
                    isSubDevice = (device.parent != null).toLong(),
                    fetchedAt = fetchedAt.toEpochMilliseconds(),
                    // Stored, unlike `kind`, because neither is derivable from anything else on the
                    // row: they are the partner's own `idProduto` for this device and for its hub,
                    // and the lock edge needs both back without a second call (SPEC L1, ADR-006).
                    productId = device.productId,
                    parentProductId = device.parentProductId,
                )
            }
        }
    }

    /**
     * Runs [block] against rows known to have this build's shape, emptying the table once per
     * instance when the stored version is not [schemaVersion] (SchemaVersion.kt).
     *
     * The check is guarded because two screens can read the cache from different coroutines, and
     * two concurrent "drop and refetch" passes would be one write too many.
     */
    private suspend fun <T> withCurrentSchema(block: () -> T): T {
        mutex.withLock {
            if (!schemaChecked) {
                queries.transaction {
                    if (queries.schemaVersion().executeAsOneOrNull() != schemaVersion.toLong()) {
                        queries.deleteAll()
                        queries.setSchemaVersion(schemaVersion.toLong())
                    }
                }
                schemaChecked = true
            }
        }
        return block()
    }

    private fun Boolean.toLong(): Long = if (this) 1L else 0L

    private companion object {
        const val LINKED = "linked"
        const val SHARED = "shared"
    }
}

/**
 * A stored row as the domain's [Device].
 *
 * `kind` is re-derived rather than stored: SPEC D5 owns that rule, and a cached row must never be
 * able to disagree with a freshly fetched one.
 */
private fun CachedDevice.toDevice(): Device = Device(
    id = DeviceId(id),
    name = name,
    model = model,
    status = if (online != 0L) DeviceStatus.Online else DeviceStatus.Offline,
    lastSeen = lastSeen?.let(Instant::fromEpochMilliseconds),
    origin = if (origin == "linked") DeviceOrigin.Linked else DeviceOrigin.Shared,
    kind = DeviceClassifier.classify(model = model, isSubDevice = isSubDevice != 0L),
    parent = parentId?.let(::DeviceId),
    productId = productId,
    parentProductId = parentProductId,
)
