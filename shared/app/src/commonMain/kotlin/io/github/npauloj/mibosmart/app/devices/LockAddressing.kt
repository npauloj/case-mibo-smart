package io.github.npauloj.mibosmart.app.devices

import io.github.npauloj.mibosmart.domain.device.Device
import io.github.npauloj.mibosmart.domain.lock.LockAddress

/** Whether the device list can open a lock, and — when it cannot — why (SPEC D6, L1). */
sealed interface LockAddressing {

    /** The four parts `docs/api-contract.md` §5 needs, all read off the lock's own row. */
    data class Addressable(val address: LockAddress) : LockAddressing

    /** The lock is real and listed, but this app cannot name it to the partner. */
    sealed interface Unavailable : LockAddressing {

        /** The row does not carry all four parts (`docs/api-contract.md` §3, §5). */
        data object ProductIdMissing : Unavailable
    }
}

/** How the partner would address [lock], assembled from [lock]'s own row and nothing else. */
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
