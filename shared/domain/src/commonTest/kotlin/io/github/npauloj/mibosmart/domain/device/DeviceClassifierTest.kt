package io.github.npauloj.mibosmart.domain.device

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * SPEC D5, against the inventory of the test account as `docs/api-contract.md` §4 describes it
 * — **models only**: serials and `idProduto` values never enter a versioned file (ADR-008).
 */
class DeviceClassifierTest {

    @Test
    fun classifiesTestAccountInventory() {
        val expected = mapOf(
            ("iM7 3M Full Color" to false) to DeviceKind.Camera,
            ("iM7-FC" to false) to DeviceKind.Camera,
            ("iM3-C" to false) to DeviceKind.Camera,
            ("IOT-MFR1001-IB" to true) to DeviceKind.Lock,
            ("IOT-MFR2020V-IB" to true) to DeviceKind.Lock,
            ("IOT-MFR2040-IB" to true) to DeviceKind.Lock,
            ("IOT-MFR7001V-IB" to true) to DeviceKind.Lock,
            ("IOT-MFR2030-IB" to true) to DeviceKind.Lock,
            ("IOT-ZG2-IB" to false) to DeviceKind.Hub,
            ("MSM 1001" to false) to DeviceKind.Other("MSM 1001"),
            ("MFD 2020 D" to false) to DeviceKind.Other("MFD 2020 D"),
            ("MSI 1001" to false) to DeviceKind.Other("MSI 1001"),
            ("MFV 7000" to false) to DeviceKind.Other("MFV 7000"),
            ("EFLS 6030" to false) to DeviceKind.Other("EFLS 6030"),
            ("ECZ 1002" to false) to DeviceKind.Other("ECZ 1002"),
            ("MTU 1001" to false) to DeviceKind.Other("MTU 1001"),
        )

        expected.forEach { (input, kind) ->
            val (model, isSubDevice) = input

            assertEquals(kind, DeviceClassifier.classify(model, isSubDevice), "classifying $model")
        }
    }

    /** The rule the ordering depends on: `MFR` alone is a family name, not a lock. */
    @Test
    fun mfrModelThatIsNotASubDeviceIsNotALock() {
        assertEquals(
            DeviceKind.Other("IOT-MFR1001-IB"),
            DeviceClassifier.classify("IOT-MFR1001-IB", isSubDevice = false),
        )
    }

    /** A model the case has never seen still classifies, rather than throwing on a list load. */
    @Test
    fun unknownModelIsOther() {
        assertEquals(DeviceKind.Other("XYZ-9999"), DeviceClassifier.classify("XYZ-9999", isSubDevice = false))
        assertEquals(DeviceKind.Other(""), DeviceClassifier.classify("", isSubDevice = true))
    }
}
