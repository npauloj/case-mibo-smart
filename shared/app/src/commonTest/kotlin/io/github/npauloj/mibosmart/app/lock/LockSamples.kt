package io.github.npauloj.mibosmart.app.lock

import io.github.npauloj.mibosmart.app.FixedClock
import io.github.npauloj.mibosmart.domain.device.Device
import io.github.npauloj.mibosmart.domain.device.DeviceId
import io.github.npauloj.mibosmart.domain.device.DeviceKind
import io.github.npauloj.mibosmart.domain.device.DeviceOrigin
import io.github.npauloj.mibosmart.domain.device.DeviceStatus
import io.github.npauloj.mibosmart.domain.device.ModelCatalog
import io.github.npauloj.mibosmart.domain.device.RawCodes
import io.github.npauloj.mibosmart.domain.lock.LockAddress
import io.github.npauloj.mibosmart.domain.lock.LockState
import io.github.npauloj.mibosmart.domain.lock.OpeningEvent
import io.github.npauloj.mibosmart.domain.lock.OpeningKind
import io.github.npauloj.mibosmart.domain.lock.VolumeLevel
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** The lock the lock tests talk about. */
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

    /** The zone the history tests read `tempoLocal` in. */
    val Zone: TimeZone = TimeZone.UTC

    /** An opening at [Now] minus [minutesAgo], in [Zone] — the shape `historico-abertura` returns. */
    fun opening(minutesAgo: Int, kind: OpeningKind, actor: String? = null): OpeningEvent =
        OpeningEvent(at = (Now - minutesAgo.minutes).toLocalDateTime(Zone), kind = kind, actor = actor)

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
        productId = LOCK_PRODUCT_ID,
        parentProductId = HUB_PRODUCT_ID,
    )

    fun destination(
        status: DeviceStatus = DeviceStatus.Online,
        lastSeen: Instant? = null,
    ): LockDestination = LockDestination(device(status, lastSeen), Address)
}

/** The lock screen wired to [repository], with the kill switch in one place. */
internal fun lockViewModel(
    repository: FakeLockRepository,
    writesEnabled: Boolean = true,
): LockViewModel {
    val lockWrites = LockWritesSwitch(writesEnabled)
    return LockViewModel(
        loadLock = LoadLock(repository),
        toggleLock = ToggleLock(repository, lockWrites),
        changeLockVolume = ChangeVolume(repository, lockWrites),
        enableLockRemoteOpen = EnableRemoteOpen(repository, lockWrites),
        lockWrites = lockWrites,
        clock = FixedClock(LockSamples.Now),
    )
}

/** The history tab wired to [repository], on a clock and a zone that do not move. */
internal fun openingHistoryViewModel(
    repository: FakeLockRepository,
    catalog: ModelCatalog = RawCodes,
): OpeningHistoryViewModel =
    OpeningHistoryViewModel(
        openingHistory = OpeningHistory(repository),
        clock = FixedClock(LockSamples.Now),
        catalog = catalog,
        timeZone = LockSamples.Zone,
    )
