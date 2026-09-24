package io.github.npauloj.mibosmart.domain.device

/**
 * The few choices the device list remembers between launches — today, the origin chip (SPEC
 * D4).
 */
interface DeviceListPreferences {

    /** The chip the user last chose, or [OriginFilter.All] when they never chose one. */
    suspend fun readOriginFilter(): OriginFilter

    suspend fun writeOriginFilter(filter: OriginFilter)
}
