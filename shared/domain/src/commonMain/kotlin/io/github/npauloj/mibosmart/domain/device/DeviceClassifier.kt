package io.github.npauloj.mibosmart.domain.device

/**
 * What a device *is*, decided from the model string alone (SPEC D5, `docs/api-contract.md` §4).
 */
object DeviceClassifier {

    /**
     * @param model the partner's `modelo`.
     * @param isSubDevice the partner's `subdispositivo` — a lock is always one, hanging off a
     * hub.
     */
    fun classify(model: String, isSubDevice: Boolean): DeviceKind = when {
        model.startsWith(CAMERA_PREFIX) -> DeviceKind.Camera
        isSubDevice && model.contains(LOCK_FAMILY) -> DeviceKind.Lock
        model.equals(HUB_MODEL, ignoreCase = true) -> DeviceKind.Hub
        else -> DeviceKind.Other(model)
    }

    private const val CAMERA_PREFIX = "iM"
    private const val LOCK_FAMILY = "MFR"
    private const val HUB_MODEL = "IOT-ZG2-IB"
}
