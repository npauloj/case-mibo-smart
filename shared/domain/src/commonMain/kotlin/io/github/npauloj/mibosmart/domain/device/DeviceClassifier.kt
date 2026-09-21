package io.github.npauloj.mibosmart.domain.device

/**
 * What a device *is*, decided from the model string alone (SPEC D5, `docs/api-contract.md` §4).
 *
 * The account has ~300 requests for the whole case (ADR-006), and `funcoes` costs one call per
 * device: seventeen devices would spend a twentieth of the budget just to draw a list. So the list
 * classifies on `modelo`, and the only call that confirms a capability is the one made at the moment
 * it matters — the camera's `RTSV*` check, immediately before opening a stream.
 */
object DeviceClassifier {

    /**
     * @param model the partner's `modelo`.
     * @param isSubDevice the partner's `subdispositivo` — a lock is always one, hanging off a hub.
     */
    fun classify(model: String, isSubDevice: Boolean): DeviceKind = when {
        // The observed camera models are "iM7 3M Full Color", "iM7-FC", "iM3-C": the family is the
        // prefix, and it is the only one that starts with it, so no other rule can shadow this.
        model.startsWith(CAMERA_PREFIX) -> DeviceKind.Camera
        // `MFR` alone is not enough: the same family name appears on models that are not sub-devices,
        // and every lock endpoint addresses the device through its hub (api-contract §5).
        isSubDevice && model.contains(LOCK_FAMILY) -> DeviceKind.Lock
        model.equals(HUB_MODEL, ignoreCase = true) -> DeviceKind.Hub
        else -> DeviceKind.Other(model)
    }

    private const val CAMERA_PREFIX = "iM"
    private const val LOCK_FAMILY = "MFR"
    private const val HUB_MODEL = "IOT-ZG2-IB"
}
