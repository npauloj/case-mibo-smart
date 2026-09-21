package io.github.npauloj.mibosmart.domain.lock

import io.github.npauloj.mibosmart.domain.device.DeviceId

/**
 * Everything the partner needs to address one lock (SPEC L1).
 *
 * A lock is never addressed on its own: it answers through the hub it hangs from, so a request
 * carries the lock's identity, the hub's identity, the hub's product id **and** the lock's own
 * product id. The four parts are kept apart here and stay uninterpreted — how they are joined into a
 * namespace is the partner's encoding and lives in `:shared:data` (ADR-004,
 * `docs/api-contract.md` §5).
 *
 * @property lock the lock itself, as the device list identifies it.
 * @property hub the device the lock hangs from ([io.github.npauloj.mibosmart.domain.device.Device.parent]).
 * @property hubProductId the hub's product id, which the composite namespace also needs.
 * @property lockProductId the lock's own product id, sent beside the namespace on every request.
 */
data class LockAddress(
    val lock: DeviceId,
    val hub: DeviceId,
    val hubProductId: String,
    val lockProductId: String,
)
