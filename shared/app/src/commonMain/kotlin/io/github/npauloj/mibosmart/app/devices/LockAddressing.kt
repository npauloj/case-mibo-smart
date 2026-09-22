package io.github.npauloj.mibosmart.app.devices

import io.github.npauloj.mibosmart.domain.device.Device
import io.github.npauloj.mibosmart.domain.lock.LockAddress

/**
 * Whether the device list can open a lock, and — when it cannot — why (SPEC D6, L1).
 *
 * One answer used twice: the row asks it to decide whether it may be tapped and what sentence it
 * carries, and the tap asks it again for the address it emits. Two rules would be two chances for the
 * screen to offer a tap that leads nowhere.
 */
sealed interface LockAddressing {

    /** The four parts `docs/api-contract.md` §5 needs, all read off rows the app actually holds. */
    data class Addressable(val address: LockAddress) : LockAddressing

    /**
     * The lock is real and listed, but this app cannot name it to the partner.
     *
     * It is never a reason to guess. The namespace is built from three identifiers, and a wrong one
     * addresses **another device** — on a lock, that is someone's front door. So the row stays
     * visible, says which part is missing (SPEC U6) and takes no tap.
     */
    sealed interface Unavailable : LockAddressing {

        /**
         * The hub the lock hangs from is not among the loaded rows (SPEC D2).
         *
         * The list is paged, so this is the ordinary case of a lock on page 1 and its hub on page 2 —
         * and fetching pages until one turns up is exactly the spending ADR-006 forbids.
         */
        data object HubNotLoaded : Unavailable

        /** The partner sent no `idProduto` for the lock or for its hub, so two of the four parts
         * cannot both be filled (`docs/api-contract.md` §5). */
        data object ProductIdMissing : Unavailable
    }
}

/**
 * How the partner would address [lock], assembled **only** from the devices in this list.
 *
 * Nothing here calls anything: the hub is looked up among the rows already loaded, which is what
 * makes opening a lock cost zero requests (ADR-006).
 */
internal fun List<Device>.addressing(lock: Device): LockAddressing {
    val hub = lock.parent?.let { parent -> firstOrNull { it.id == parent } }
        ?: return LockAddressing.Unavailable.HubNotLoaded
    if (lock.productId.isBlank() || hub.productId.isBlank()) {
        return LockAddressing.Unavailable.ProductIdMissing
    }
    return LockAddressing.Addressable(
        LockAddress(
            lock = lock.id,
            hub = hub.id,
            hubProductId = hub.productId,
            lockProductId = lock.productId,
        ),
    )
}
