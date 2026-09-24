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
 * The last device page the partner answered with, kept on disk so a cold start costs nothing
 * and an offline user still sees a list (ADR-006, SPEC D8, D10, U2).
 */
internal interface DeviceCache {

    /** The stored page, or null when nothing was stored or the stored shape is obsolete. */
    suspend fun read(): CachedDevices?

    /** Replaces the stored page wholesale: a page is only ever meaningful in one piece. */
    suspend fun write(devices: List<Device>, fetchedAt: Instant)
}

/**
 * The SQLDelight cache (ADR-006).
 * @param schemaVersion the shape the rows are written with.
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
                    isSubDevice = (device.parent != null).toLong(),
                    fetchedAt = fetchedAt.toEpochMilliseconds(),
                    productId = device.productId,
                    parentProductId = device.parentProductId,
                )
            }
        }
    }

    /**
     * Runs [block] against rows known to have this build's shape, emptying the table once per
     * instance when the stored version is not [schemaVersion] (SchemaVersion.kt).
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

/** A stored row as the domain's [Device]. */
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
