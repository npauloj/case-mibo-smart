package io.github.npauloj.mibosmart.app.lock

import io.github.npauloj.mibosmart.domain.device.Device
import io.github.npauloj.mibosmart.domain.device.DeviceId
import io.github.npauloj.mibosmart.domain.device.DeviceKind
import io.github.npauloj.mibosmart.domain.device.DeviceOrigin
import io.github.npauloj.mibosmart.domain.device.DeviceStatus
import io.github.npauloj.mibosmart.domain.lock.LockAddress
import io.github.npauloj.mibosmart.domain.lock.LockState
import io.github.npauloj.mibosmart.domain.lock.VolumeLevel
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The lock the lock tests talk about.
 *
 * Every identifier is a placeholder in the shape `docs/api-contract.md` uses: the test account's
 * namespaces and product ids open a real door and are never committed (ADR-008).
 */
internal object LockSamples {

    const val LOCK_NAMESPACE = "<lock-ns>"
    const val HUB_NAMESPACE = "<hub-ns>"
    const val HUB_PRODUCT_ID = "<hub-product-id>"
    const val LOCK_PRODUCT_ID = "<lock-product-id>"

    val Address = LockAddress(
        lock = DeviceId(LOCK_NAMESPACE),
        hub = DeviceId(HUB_NAMESPACE),
        hubProductId = HUB_PRODUCT_ID,
        lockProductId = LOCK_PRODUCT_ID,
    )

    val Locked = LockState(isOpen = false, isRemoteOpenEnabled = true, volume = VolumeLevel.Low)

    /** The moment the tests call "now", so "há 3 h" is a fact and not a function of the test machine. */
    val Now: Instant = Instant.parse("2026-09-21T12:00:00Z")

    fun device(
        status: DeviceStatus = DeviceStatus.Online,
        lastSeen: Instant? = null,
    ): Device = Device(
        id = DeviceId(LOCK_NAMESPACE),
        name = "Fechadura da entrada",
        model = "IOT-MFR1001-IB",
        status = status,
        lastSeen = lastSeen,
        origin = DeviceOrigin.Linked,
        kind = DeviceKind.Lock,
        parent = DeviceId(HUB_NAMESPACE),
    )

    fun destination(
        status: DeviceStatus = DeviceStatus.Online,
        lastSeen: Instant? = null,
    ): LockDestination = LockDestination(device(status, lastSeen), Address)
}

/** A clock that does not move, so a relative time can be asserted (SPEC U3). */
internal class FixedClock(private val instant: Instant = LockSamples.Now) : Clock {
    override fun now(): Instant = instant
}
