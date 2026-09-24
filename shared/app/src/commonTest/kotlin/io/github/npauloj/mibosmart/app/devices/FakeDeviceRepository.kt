package io.github.npauloj.mibosmart.app.devices

import io.github.npauloj.mibosmart.domain.device.CachedDevices
import io.github.npauloj.mibosmart.domain.device.Device
import io.github.npauloj.mibosmart.domain.device.DeviceListPreferences
import io.github.npauloj.mibosmart.domain.device.DevicePage
import io.github.npauloj.mibosmart.domain.device.DeviceRepository
import io.github.npauloj.mibosmart.domain.device.OriginFilter

/** What a page was asked for — the `(origem, pagina)` SPEC D11 is about, as the test can read it. */
internal data class DeviceQuery(val origin: OriginFilter = OriginFilter.All, val page: Int = 1)

/**
 * A partner that answers whatever the test wants, and records every query it was asked — the
 * account pays per request (ADR-006), so "how many calls, and for what" is part of the
 * behaviour under test.
 * @param cached what the local cache holds before the test starts.
 * @param answer the devices of the requested page.
 */
internal class FakeDeviceRepository(
    private val cached: suspend () -> CachedDevices? = { null },
    private val answer: suspend (DeviceQuery) -> List<Device> = { emptyList() },
) : DeviceRepository {

    /** Every page asked for, in order. */
    val queries: MutableList<DeviceQuery> = mutableListOf()

    val calls: Int get() = queries.size

    override suspend fun page(origin: OriginFilter, page: Int): DevicePage {
        val query = DeviceQuery(origin, page)
        queries += query
        return answer(query).let { DevicePage(devices = it, hasMore = it.size == FULL_PAGE) }
    }

    override suspend fun cachedPage(): CachedDevices? = cached()

    internal companion object {
        /** The `tamanhoPagina` the repository sends (SPEC D1) — what "a full page" means. */
        const val FULL_PAGE = 20
    }
}

/** The remembered chip without a database (SPEC D4). */
internal class FakeDeviceListPreferences(
    private var filter: OriginFilter = OriginFilter.All,
) : DeviceListPreferences {

    /** Every chip written, in order: SPEC D4 is also about *when* the choice is recorded. */
    val written: MutableList<OriginFilter> = mutableListOf()

    override suspend fun readOriginFilter(): OriginFilter = filter

    override suspend fun writeOriginFilter(filter: OriginFilter) {
        this.filter = filter
        written += filter
    }
}
