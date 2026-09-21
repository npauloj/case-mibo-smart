package io.github.npauloj.mibosmart.domain.device

/**
 * The few choices the device list remembers between launches — today, the origin chip (SPEC D4).
 *
 * A port of its own rather than a method on [DeviceRepository]: nothing here costs a request or
 * touches the partner, and a preference read must stay free even when the account's budget is spent
 * (ADR-006). Where it is stored is `:shared:data`'s business (ADR-004).
 */
interface DeviceListPreferences {

    /** The chip the user last chose, or [OriginFilter.All] when they never chose one. */
    suspend fun readOriginFilter(): OriginFilter

    suspend fun writeOriginFilter(filter: OriginFilter)
}
