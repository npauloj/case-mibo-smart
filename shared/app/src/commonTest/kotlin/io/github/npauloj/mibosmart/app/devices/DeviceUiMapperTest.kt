package io.github.npauloj.mibosmart.app.devices

import io.github.npauloj.mibosmart.domain.device.DeviceId
import io.github.npauloj.mibosmart.domain.device.DeviceKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** SPEC D6 and U3: what a row says about a device, decided here and not in the composable. */
class DeviceUiMapperTest {

    /** SPEC D6: a lock is only reachable through its hub, so the row names the hub. */
    @Test
    fun subdeviceShowsParent() {
        val hub = device("MCA 1002", id = HUB_ID, kind = DeviceKind.Hub)
        val lock = device("MFR 1001", kind = DeviceKind.Lock, parent = DeviceId(HUB_ID))

        val rows = listOf(hub, lock).toRows(NOW)

        assertEquals("MCA 1002", rows.single { it.name == "MFR 1001" }.parentName)
        assertNull(rows.single { it.name == "MCA 1002" }.parentName, "a hub hangs off nothing")
    }

    /**
     * A sub-device whose hub is not on this page names no parent rather than a wrong one: page 2 is
     * D-02's, and "MCA 1002" invented here would be a claim the app cannot back.
     */
    @Test
    fun subdeviceWhoseParentIsNotOnThePageNamesNoParent() {
        val lock = device("MFR 2040", kind = DeviceKind.Lock, parent = DeviceId("PLACEHOLDER-ABSENT-HUB"))

        assertNull(listOf(lock).toRows(NOW).single().parentName)
    }

    /** SPEC U3: an offline device says when it was last seen, in the largest readable unit. */
    @Test
    fun offlineRowShowsLastSeen() {
        val cases = mapOf(
            30.seconds to LastSeen.Ago(1, ElapsedUnit.Minutes),
            45.minutes to LastSeen.Ago(45, ElapsedUnit.Minutes),
            3.hours to LastSeen.Ago(3, ElapsedUnit.Hours),
            12.days to LastSeen.Ago(12, ElapsedUnit.Days),
        )

        cases.forEach { (elapsed, expected) ->
            val offline = device("iM3-C", isOnline = false, lastSeen = NOW - elapsed)

            assertEquals(expected, listOf(offline).toRows(NOW).single().lastSeen, "after $elapsed")
        }
    }

    /** SPEC U3: no `ultimaVezOnline` means the partner never saw it online — not "há 0 min". */
    @Test
    fun offlineWithoutTimestampSaysNever() {
        val offline = device("MFV 7000", kind = DeviceKind.Other("MFV 7000"), isOnline = false)

        assertEquals(LastSeen.Never, listOf(offline).toRows(NOW).single().lastSeen)
    }

    /** An online device is online: the "visto pela última vez" line is for offline rows only (U3). */
    @Test
    fun onlineRowHasNoLastSeenLine() {
        val online = device("iM7-FC", isOnline = true, lastSeen = NOW - 2.hours)

        assertNull(listOf(online).toRows(NOW).single().lastSeen)
    }

    /** SPEC D6: cameras and addressable locks open a screen; hubs and the rest are informational. */
    @Test
    fun onlyCamerasAndLocksAreActionable() {
        val devices = listOf(
            device("camera", kind = DeviceKind.Camera),
            // The lock comes with its hub: without one it has no address, which is the next test.
            device("lock", kind = DeviceKind.Lock, parent = DeviceId(HUB_ID)),
            device("hub", id = HUB_ID, kind = DeviceKind.Hub),
            device("sensor", kind = DeviceKind.Other("MSM 1001")),
        )

        assertEquals(
            listOf(true, true, false, false),
            devices.toRows(NOW).map { it.isActionable },
        )
        assertTrue(devices.toRows(NOW).all { it.unavailable == null })
    }

    /**
     * SPEC D6 and D2: a lock the loaded rows cannot address is still listed — it just says why and
     * takes no tap, instead of opening a screen with nothing to talk to (SPEC U6).
     */
    @Test
    fun aLockThatCannotBeAddressedSaysSoInsteadOfOpening() {
        val orphan = device("MFR 2040", kind = DeviceKind.Lock, parent = DeviceId("PLACEHOLDER-ABSENT-HUB"))
        val withoutProductId = listOf(
            device("MFR 1001", kind = DeviceKind.Lock, parent = DeviceId(HUB_ID), productId = ""),
            device("MCA 1002", id = HUB_ID, kind = DeviceKind.Hub),
        )

        val orphanRow = listOf(orphan).toRows(NOW).single()
        val blankRow = withoutProductId.toRows(NOW).first { it.name == "MFR 1001" }

        assertEquals(LockAddressing.Unavailable.HubNotLoaded, orphanRow.unavailable)
        assertEquals(LockAddressing.Unavailable.ProductIdMissing, blankRow.unavailable)
        assertFalse(orphanRow.isActionable)
        assertFalse(blankRow.isActionable)
    }

    private companion object {
        val NOW = Instant.parse("2026-09-21T12:00:00Z")
        const val HUB_ID = "PLACEHOLDER-HUB-NS"
    }
}
