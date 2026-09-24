package io.github.npauloj.mibosmart.domain.lock

import io.github.npauloj.mibosmart.domain.device.DeviceId

/**
 * Everything the partner needs to address one lock (SPEC L1).
 * @property lock the lock itself, as the device list identifies it.
 * @property hub the device the lock hangs from
 * ([io.github.npauloj.mibosmart.domain.device.Device.parent]).
 * @property hubProductId the hub's product id, which the composite namespace also needs.
 * @property lockProductId the lock's own product id, sent beside the namespace on every
 * request.
 */
data class LockAddress(
    val lock: DeviceId,
    val hub: DeviceId,
    val hubProductId: String,
    val lockProductId: String,
)
