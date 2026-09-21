package io.github.npauloj.mibosmart.app.devices

import io.github.npauloj.mibosmart.domain.device.Device
import io.github.npauloj.mibosmart.domain.device.DeviceId
import io.github.npauloj.mibosmart.domain.device.DeviceKind
import io.github.npauloj.mibosmart.domain.device.DeviceListPreferences
import io.github.npauloj.mibosmart.domain.device.DeviceOrigin
import io.github.npauloj.mibosmart.domain.device.DeviceStatus
import io.github.npauloj.mibosmart.domain.device.OriginFilter
import kotlin.time.Instant

/** The use case as every test builds it: a fake partner, and a chip nobody has chosen yet. */
internal fun listDevices(
    repository: FakeDeviceRepository,
    preferences: DeviceListPreferences = FakeDeviceListPreferences(),
) = ListDevices(deviceRepository = repository, preferences = preferences)

/**
 * Page 1 of [origin], with the stored page allowed to answer for it — the call the list makes when
 * it opens (SPEC D1, D8). Tests about paging state the page and the cache explicitly.
 */
internal suspend fun ListDevices.firstPage(origin: OriginFilter = OriginFilter.All) =
    this(origin = origin, page = 1, mayUseCache = true)

/**
 * Domain devices for the app-side tests, with **placeholder** serials only — the test account's `ns`
 * values never enter a versioned file (ADR-008).
 */
internal fun device(
    name: String,
    id: String = "PLACEHOLDER-$name",
    kind: DeviceKind = DeviceKind.Camera,
    isOnline: Boolean = true,
    lastSeen: Instant? = null,
    parent: DeviceId? = null,
    origin: DeviceOrigin = DeviceOrigin.Linked,
) = Device(
    id = DeviceId(id),
    name = name,
    model = "irrelevant",
    status = if (isOnline) DeviceStatus.Online else DeviceStatus.Offline,
    lastSeen = lastSeen,
    origin = origin,
    kind = kind,
    parent = parent,
)
