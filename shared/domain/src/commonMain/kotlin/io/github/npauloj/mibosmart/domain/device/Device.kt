package io.github.npauloj.mibosmart.domain.device

import kotlin.jvm.JvmInline
import kotlinx.datetime.Instant

/**
 * Opaque identity of a device as the partner needs it back.
 *
 * The domain never inspects the value: the data module encodes whatever the partner requires to
 * address the device (serial, `ns`, product id, composite hub address for a sub-device) and decodes
 * it again on the way out. Keeping it opaque is what lets a second partner reuse every use case
 * (ADR-004).
 */
@JvmInline
value class DeviceId(val value: String)

/** Whether the device belongs to the account or was shared with it (RF07 filter). */
enum class DeviceOrigin { Linked, Shared }

enum class DeviceStatus { Online, Offline }

/** Classified from the partner's model string at list time, without extra calls (ADR-006, SPEC D5). */
sealed interface DeviceKind {
    data object Camera : DeviceKind
    data object Lock : DeviceKind
    data object Hub : DeviceKind
    data class Other(val model: String) : DeviceKind
}

/**
 * A device as the app reasons about it.
 *
 * @property lastSeen last moment the partner saw the device online, when known — shown as
 *   "visto pela última vez há X" for offline devices (SPEC U3).
 * @property parent the hub a sub-device (e.g. a lock) hangs from, when it has one (SPEC D6).
 */
data class Device(
    val id: DeviceId,
    val name: String,
    val model: String,
    val status: DeviceStatus,
    val lastSeen: Instant?,
    val origin: DeviceOrigin,
    val kind: DeviceKind,
    val parent: DeviceId?,
)
