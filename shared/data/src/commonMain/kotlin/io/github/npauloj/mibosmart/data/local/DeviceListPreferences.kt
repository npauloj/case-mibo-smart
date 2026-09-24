package io.github.npauloj.mibosmart.data.local

import io.github.npauloj.mibosmart.data.local.db.MiboSmartDatabase
import io.github.npauloj.mibosmart.data.platform.db.DatabaseDriverFactory
import io.github.npauloj.mibosmart.domain.device.DeviceListPreferences
import io.github.npauloj.mibosmart.domain.device.OriginFilter

/** The list's preferences in the app's own SQLite file (SPEC D4). */
internal class SqlDeviceListPreferences(driverFactory: DatabaseDriverFactory) : DeviceListPreferences {

    private val queries by lazy { MiboSmartDatabase(driverFactory.create()).deviceQueries }

    /**
     * A value written by an older build — or by a newer one, after a downgrade — is not a
     * choice this build can honour, so it reads as the default rather than as a failure: SPEC
     * D4's fallback is `todos`, and a list that refuses to open because of a remembered chip
     * would be worse than one that opens on the wrong chip.
     */
    override suspend fun readOriginFilter(): OriginFilter =
        queries.preference(ORIGIN_FILTER_KEY).executeAsOneOrNull()
            ?.let { stored -> OriginFilter.entries.firstOrNull { it.name == stored } }
            ?: OriginFilter.All

    /**
     * Stored by [Enum.name], not by ordinal: reordering the enum would silently reinterpret
     * every stored ordinal, and the one value here is worth less than that class of bug.
     */
    override suspend fun writeOriginFilter(filter: OriginFilter) {
        queries.setPreference(ORIGIN_FILTER_KEY, filter.name)
    }

    private companion object {
        const val ORIGIN_FILTER_KEY = "device_list.origin_filter"
    }
}
