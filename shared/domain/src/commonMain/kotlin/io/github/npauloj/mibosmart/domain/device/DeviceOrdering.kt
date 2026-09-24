package io.github.npauloj.mibosmart.domain.device

/** The order rows appear in, as a rule rather than as whatever the partner sent (SPEC U8). */
fun List<Device>.orderedForList(): List<Device> = sortedWith(DeviceListOrder)

/**
 * Online cameras and locks first, then the remaining online devices, then everything offline;
 * inside each group, by name.
 */
private object DeviceListOrder : Comparator<Device> {

    override fun compare(a: Device, b: Device): Int {
        val byGroup = a.group().compareTo(b.group())
        if (byGroup != 0) return byGroup
        val byName = a.name.lowercase().compareTo(b.name.lowercase())
        return if (byName != 0) byName else a.id.value.compareTo(b.id.value)
    }

    private fun Device.group(): Int = when {
        isOnline && isActionable -> ACTIONABLE_ONLINE
        isOnline -> OTHER_ONLINE
        else -> OFFLINE
    }

    private const val ACTIONABLE_ONLINE = 0
    private const val OTHER_ONLINE = 1
    private const val OFFLINE = 2
}
