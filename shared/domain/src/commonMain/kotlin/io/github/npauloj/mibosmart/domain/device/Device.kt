package io.github.npauloj.mibosmart.domain.device

import kotlin.jvm.JvmInline
import kotlin.time.Instant

/** Opaque identity of a device as the partner needs it back. */
@JvmInline
value class DeviceId(val value: String)

/** Whether the device belongs to the account or was shared with it (RF07 filter). */
enum class DeviceOrigin { Linked, Shared }

/** Which devices the list is asking for — the three chips of SPEC D4, as a domain choice. */
enum class OriginFilter {
    All,
    Linked,
    Shared,
    ;

    /** Whether a device with this [origin] belongs under this chip. */
    fun accepts(origin: DeviceOrigin): Boolean = when (this) {
        All -> true
        Linked -> origin == DeviceOrigin.Linked
        Shared -> origin == DeviceOrigin.Shared
    }
}

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
 * @property lastSeen last moment the partner saw the device online, when known — shown as
 * "visto pela última vez há X" for offline devices (SPEC U3).
 * @property parent the hub a sub-device (e.g.
 * @property productId the partner's own product identity for this device, kept beside [id]
 * because a lock is addressed by **both** — its hub's product id and its own (SPEC L1,
 * `docs/api-contract.md` §5).
 * @property parentProductId [parent]'s own product id, which the partner puts on **this** row
 * (`docs/api-contract.md` §3).
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
    val productId: String,
    val parentProductId: String?,
) {
    val isOnline: Boolean get() = status == DeviceStatus.Online

    /** Cameras and locks are the two kinds a row can open; hubs and the rest are informational (SPEC D6). */
    val isActionable: Boolean get() = kind == DeviceKind.Camera || kind == DeviceKind.Lock
}

/** The last page the partner answered with, and the moment it did (SPEC D8, D10). */
data class CachedDevices(val devices: List<Device>, val fetchedAt: Instant)

/** One page of the list, and whether asking for the next one is worth a request (SPEC D2). */
data class DevicePage(val devices: List<Device>, val hasMore: Boolean)

/**
 * The partner-side half of the device list: the domain states the intent, `:shared:data` knows
 * the wire and where the credential comes from (ADR-004).
 */
interface DeviceRepository {

    /** One page of the account's devices, already classified and ordered (SPEC D1, D2, D5, U8). */
    suspend fun page(origin: OriginFilter, page: Int): DevicePage

    /** The last page 1 [page] stored, or null when nothing was ever stored (SPEC D8, D10, U2). */
    suspend fun cachedPage(): CachedDevices?
}
