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

    /** The four parts `docs/api-contract.md` §5 needs, all read off the lock's own row. */
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
         * The row does not carry all four parts (`docs/api-contract.md` §3, §5).
         *
         * One reason and not several: `idProduto` arrives blank on some devices, and
         * `dispositivoPai` / `idProdutoDispositivoPai` arrive only on sub-devices — but a lock
         * missing any of them is equally unaddressable, and splitting that into separate sentences
         * would ask the user to tell apart cases they can do nothing about either way.
         */
        data object ProductIdMissing : Unavailable
    }
}

/**
 * How the partner would address [lock], assembled from [lock]'s own row and nothing else.
 *
 * Nothing here calls anything, and nothing here searches: the partner puts the hub's `ns` and the
 * hub's `idProduto` on the sub-device's row (`docs/api-contract.md` §3), so every part is already in
 * hand. That is what makes opening a lock cost zero requests (ADR-006) *and* what makes it work while
 * the hub sits on a page nobody has loaded.
 */
internal fun addressing(lock: Device): LockAddressing {
    val hub = lock.parent
    val hubProductId = lock.parentProductId?.takeIf { it.isNotBlank() }
    if (hub == null || hubProductId == null || lock.productId.isBlank()) {
        return LockAddressing.Unavailable.ProductIdMissing
    }
    return LockAddressing.Addressable(
        LockAddress(
            lock = lock.id,
            hub = hub,
            hubProductId = hubProductId,
            lockProductId = lock.productId,
        ),
    )
}
