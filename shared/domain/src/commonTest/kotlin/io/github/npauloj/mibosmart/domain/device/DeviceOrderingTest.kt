package io.github.npauloj.mibosmart.domain.device

import kotlin.test.Test
import kotlin.test.assertEquals

/** SPEC U8: the order is the app's, not the partner's, and it is the same on every load. */
class DeviceOrderingTest {

    @Test
    fun onlineActionableFirstThenByName() {
        val shuffled = listOf(
            device("zulu-offline-camera", DeviceKind.Camera, isOnline = false),
            device("Bravo hub", DeviceKind.Hub, isOnline = true),
            device("delta camera", DeviceKind.Camera, isOnline = true),
            device("alfa sensor", DeviceKind.Other("MSM 1001"), isOnline = true),
            device("Charlie lock", DeviceKind.Lock, isOnline = true),
            device("Alfa offline sensor", DeviceKind.Other("MSM 1001"), isOnline = false),
        )

        val names = shuffled.orderedForList().map { it.name }

        assertEquals(
            listOf(
                // Online cameras and locks, by name — the two kinds a row can open.
                "Charlie lock",
                "delta camera",
                // Then the rest of what is online, by name: case must not split the group.
                "alfa sensor",
                "Bravo hub",
                // Then everything offline, by name, whatever its kind.
                "Alfa offline sensor",
                "zulu-offline-camera",
            ),
            names,
        )
    }

    /**
     * Two devices with the same name must not swap places between loads — the complaint U8 answers.
     * Only the id can break the tie, and it is unique.
     */
    @Test
    fun sameNameIsBrokenByIdSoTheOrderIsStable() {
        val first = device("Câmera", DeviceKind.Camera, isOnline = true, id = "ns-b")
        val second = device("Câmera", DeviceKind.Camera, isOnline = true, id = "ns-a")

        assertEquals(listOf("ns-a", "ns-b"), listOf(first, second).orderedForList().map { it.id.value })
        assertEquals(listOf("ns-a", "ns-b"), listOf(second, first).orderedForList().map { it.id.value })
    }

    private fun device(
        name: String,
        kind: DeviceKind,
        isOnline: Boolean,
        id: String = name,
    ) = Device(
        id = DeviceId(id),
        name = name,
        model = "irrelevant",
        status = if (isOnline) DeviceStatus.Online else DeviceStatus.Offline,
        lastSeen = null,
        origin = DeviceOrigin.Linked,
        kind = kind,
        parent = null,
    )
}
