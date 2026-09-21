package io.github.npauloj.mibosmart.app.devices

import io.github.npauloj.mibosmart.domain.device.Device
import io.github.npauloj.mibosmart.domain.device.DeviceId
import io.github.npauloj.mibosmart.domain.device.DeviceKind
import io.github.npauloj.mibosmart.domain.device.DeviceOrigin
import io.github.npauloj.mibosmart.domain.device.DeviceStatus
import kotlin.time.Instant

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
