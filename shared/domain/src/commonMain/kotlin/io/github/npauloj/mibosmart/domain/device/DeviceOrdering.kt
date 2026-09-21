package io.github.npauloj.mibosmart.domain.device

/**
 * The order rows appear in, as a rule rather than as whatever the partner sent (SPEC U8).
 *
 * The API returns the page in an order it does not document, so two consecutive loads can shuffle the
 * list under the user's finger — the complaint cluster U8 answers. Sorting here, in the domain, also
 * means the cache (D-01b) and the filter (D-02) cannot each invent an order of their own.
 */
fun List<Device>.orderedForList(): List<Device> = sortedWith(DeviceListOrder)

/**
 * Online cameras and locks first, then the remaining online devices, then everything offline; inside
 * each group, by name.
 *
 * The comparison is case-insensitive and accent-sensitive: `commonMain` has no collator, so a
 * locale-aware sort would need an `expect/actual` for a difference nobody can see in a list whose
 * names are partner model codes. What matters is that the order is **total and deterministic** — ties
 * are broken by the device id, so two devices sharing a name never swap places between loads.
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
