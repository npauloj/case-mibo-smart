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

/**
 * Which devices the list is asking for — the three chips of SPEC D4, as a domain choice.
 *
 * [All] is not `null`: the partner has a value for "everything" and so does the screen, and an
 * absent filter would have to be re-invented as one at every layer it crossed. The Portuguese wire
 * words it maps to (`todos|vinculados|compartilhados`) live only in `:shared:data` (ADR-004).
 */
enum class OriginFilter {
    All,
    Linked,
    Shared,
    ;

    /**
     * Whether a device with this [origin] belongs under this chip.
     *
     * The same question `origem` answers on the wire, asked locally. It lives in the domain
     * because it *is* the meaning of the filter, and because the screen needs it to answer a chip
     * without spending a request when the complete set is already in hand (ADR-027).
     */
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
 *
 * @property lastSeen last moment the partner saw the device online, when known — shown as
 *   "visto pela última vez há X" for offline devices (SPEC U3).
 * @property parent the hub a sub-device (e.g. a lock) hangs from, when it has one (SPEC D6).
 * @property productId the partner's own product identity for this device, kept beside [id] because a
 *   lock is addressed by **both** — its hub's product id and its own (SPEC L1,
 *   `docs/api-contract.md` §5). It is legitimately blank on devices the partner sends none for (some
 *   cameras), which is exactly why the value is carried rather than assumed: a blank one must stop a
 *   [io.github.npauloj.mibosmart.domain.lock.LockAddress] from being assembled, and it can only do
 *   that if it is here to be read.
 * @property parentProductId [parent]'s own product id, which the partner puts on **this** row
 *   (`docs/api-contract.md` §3). It is the fourth part of a lock address, and having it here is what
 *   makes a lock addressable without its hub being loaded: null on anything that is not a
 *   sub-device, and — like [productId] — refused rather than guessed when it is missing.
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

/**
 * The last page the partner answered with, and the moment it did (SPEC D8, D10).
 *
 * [fetchedAt] is the whole point: rows without it can be shown, but not honestly — "última
 * atualização há N min" is what tells the user whether the list in front of them is worth trusting.
 */
data class CachedDevices(val devices: List<Device>, val fetchedAt: Instant)

/**
 * One page of the list, and whether asking for the next one is worth a request (SPEC D2).
 *
 * The partner sends a bare array — no total, no page count (`docs/api-contract.md` §3) — so "is
 * there more" is not a fact the caller can read off [devices]: it is *inferred* from the page being
 * exactly as long as the one that was asked for, and only the module that chose that length can
 * say so. [hasMore] is that module's answer, which is what keeps the page size out of the domain.
 */
data class DevicePage(val devices: List<Device>, val hasMore: Boolean)

/**
 * The partner-side half of the device list: the domain states the intent, `:shared:data` knows the
 * wire and where the credential comes from (ADR-004).
 *
 * Failures are the typed exceptions of `domain.error` rather than return values (ADR-002); the use
 * case that calls this turns them into a result the UI can render.
 */
interface DeviceRepository {

    /**
     * One page of the account's devices, already classified and ordered (SPEC D1, D2, D5, U8).
     *
     * Exactly one partner call (ADR-006) and no retry of its own — which is also why the caller, not
     * this interface, decides when a next page is worth asking for. [page] is 1-based, as the
     * partner counts. Page 1 is written to the cache [cachedPage] reads (SPEC D10).
     */
    suspend fun page(origin: OriginFilter, page: Int): DevicePage

    /**
     * The last page 1 [page] stored, or null when nothing was ever stored (SPEC D8, D10, U2).
     *
     * It costs no request, which is what lets a cold start render it before the partner is even asked
     * and lets an offline list show something instead of an error (ADR-006).
     */
    suspend fun cachedPage(): CachedDevices?
}
