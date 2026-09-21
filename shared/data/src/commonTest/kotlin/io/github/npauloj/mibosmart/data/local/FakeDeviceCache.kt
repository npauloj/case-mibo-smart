package io.github.npauloj.mibosmart.data.local

import io.github.npauloj.mibosmart.domain.device.CachedDevices
import io.github.npauloj.mibosmart.domain.device.Device
import kotlin.time.Instant

/**
 * The cache without SQLite, for the tests that are about the repository rather than about the file.
 *
 * The real thing is proven against a real database in `DeviceCacheTest` (androidHostTest); what the
 * repository owes the cache is only "write every page you fetch, read what you were given".
 */
internal class FakeDeviceCache(private var stored: CachedDevices? = null) : DeviceCache {

    override suspend fun read(): CachedDevices? = stored

    override suspend fun write(devices: List<Device>, fetchedAt: Instant) {
        stored = CachedDevices(devices, fetchedAt)
    }
}
