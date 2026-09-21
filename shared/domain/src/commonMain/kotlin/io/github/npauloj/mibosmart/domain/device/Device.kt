package io.github.npauloj.mibosmart.domain.device

import kotlin.jvm.JvmInline
import kotlin.time.Instant

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
) {
    val isOnline: Boolean get() = status == DeviceStatus.Online

    /** Cameras and locks are the two kinds a row can open; hubs and the rest are informational (SPEC D6). */
    val isActionable: Boolean get() = kind == DeviceKind.Camera || kind == DeviceKind.Lock
}

/**
 * The partner-side half of the device list: the domain states the intent, `:shared:data` knows the
 * wire and where the credential comes from (ADR-004).
 *
 * Failures are the typed exceptions of `domain.error` rather than return values (ADR-002); the use
 * case that calls this turns them into a result the UI can render.
 */
interface DeviceRepository {

    /**
     * The first page of the account's devices, already classified and ordered (SPEC D1, D5, U8).
     *
     * Exactly one partner call (ADR-006) and no retry of its own. Paging beyond page 1 and the origin
     * filter arrive with the slice that has a control for them.
     */
    suspend fun firstPage(): List<Device>
}
