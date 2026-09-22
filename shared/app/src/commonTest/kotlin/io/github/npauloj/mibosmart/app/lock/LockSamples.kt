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

    /**
     * The zone the history tests read `tempoLocal` in.
     *
     * It is UTC so that a wall-clock time in a test lines up with [Now] by reading, not by
     * arithmetic: the partner sends no offset (SPEC L9) and the app uses the device's zone, which is
     * exactly the input a test must pin down.
     */
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

/**
 * The lock screen wired to [repository], with the kill switch in one place.
 *
 * Both writes and the ViewModel read the **same** [LockWritesSwitch]: a build where the use cases
 * refuse to write but the screen still offers the controls — or the other way round — is a bug, not
 * a configuration, and a shared factory is what keeps a test from inventing one.
 */
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

/**
 * The history tab wired to [repository], on a clock and a zone that do not move.
 *
 * Both are pinned for the same reason: "há 5 min" and "21/09/2026 11:55" are the acceptance
 * criterion (SPEC U4), and either the machine's clock or its time zone would otherwise decide what
 * the test asserts.
 */
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

/**
 * A catalogue with exactly the entries a test names (ADR-007).
 *
 * The real one is the partner's Java library and exists on Android only; what the tab has to get
 * right is the same either way — the catalogue's words when there are any, the partner's raw `tipo`
 * when there are not.
 */
internal class FakeModelCatalog(vararg entries: Pair<String, String>) : ModelCatalog {

    private val labels = entries.toMap()

    override fun label(code: String): String = labels[code] ?: code
}
