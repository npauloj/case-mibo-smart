package io.github.npauloj.mibosmart.app.devices

import io.github.npauloj.mibosmart.domain.device.CachedDevices
import io.github.npauloj.mibosmart.domain.device.Device
import io.github.npauloj.mibosmart.domain.device.DeviceListPreferences
import io.github.npauloj.mibosmart.domain.device.DeviceRepository
import io.github.npauloj.mibosmart.domain.device.OriginFilter
import io.github.npauloj.mibosmart.domain.error.SmartHomeException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Instant

/**
 * What opening the device list means: the chip the user last chose, one partner call for the
 * page they are looking at, and a closed set of outcomes (SPEC D1, D3, D4, E2).
 */
class ListDevices(
    private val deviceRepository: DeviceRepository,
    private val preferences: DeviceListPreferences,
) {

    /** The chip the list opens on (SPEC D4); [OriginFilter.All] until the user picks another. */
    suspend fun rememberedFilter(): OriginFilter = preferences.readOriginFilter()

    /** Records the chip the user just chose, so the next launch opens on it (SPEC D4). */
    suspend fun rememberFilter(filter: OriginFilter) = preferences.writeOriginFilter(filter)

    /**
     * What is already known, before the partner is asked (SPEC U2) — empty when nothing is
     * cached.
     */
    suspend fun cached(): List<Device> = cachedPage()?.devices.orEmpty()

    /**
     * One page of [origin] (SPEC D1, D2, D3, D8).
     * @param mayUseCache whether the stored page still describes *this* query.
     */
    suspend operator fun invoke(
        origin: OriginFilter,
        page: Int,
        mayUseCache: Boolean,
    ): DeviceListResult =
        try {
            deviceRepository.page(origin, page).let { fetched ->
                if (fetched.devices.isEmpty()) {
                    DeviceListResult.Empty
                } else {
                    DeviceListResult.Loaded(fetched.devices, hasMore = fetched.hasMore)
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Throwable) {
            failure.toResult().let { if (mayUseCache) it.orStaleCache() else it }
        }

    /** SPEC D8: only the call that never arrived falls back on the cache. */
    private suspend fun DeviceListResult.orStaleCache(): DeviceListResult {
        if (this != DeviceListResult.Offline) return this
        val cached = cachedPage()?.takeIf { it.devices.isNotEmpty() } ?: return this
        return DeviceListResult.Stale(cached.devices, cached.fetchedAt)
    }

    /** A cache that cannot be read is a cache miss. */
    private suspend fun cachedPage(): CachedDevices? =
        try {
            deviceRepository.cachedPage()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            null
        }
}

/**
 * Everything listing devices can end in (ADR-002), and the app-side half of SPEC **E2**: every
 * category the taxonomy can currently raise has a branch here, so adding a subtype to
 * [SmartHomeException] without deciding what the list does with it stops compiling.
 */
sealed interface DeviceListResult {

    /** The page came back with devices, already classified and ordered. */
    data class Loaded(val devices: List<Device>, val hasMore: Boolean) : DeviceListResult

    /** The page came back empty — a state, not a failure (SPEC D3), and the end of the list (D2). */
    data object Empty : DeviceListResult

    /**
     * Page 1 never arrived and the cache had one (SPEC D8): the rows are real, [fetchedAt] is
     * how old they are, and the screen says so instead of showing an empty error.
     */
    data class Stale(val devices: List<Device>, val fetchedAt: Instant) : DeviceListResult

    /** HTTP 401: the guard sends the user back to the token screen (SPEC D9, S6). */
    data object TokenRejected : DeviceListResult

    /**
     * HTTP 403 (SPEC S3.1). [serverMessage] is the partner's own sentence, the one category
     * where the server's words are fit to show (SPEC U6).
     */
    data class TokenExpired(val serverMessage: String?) : DeviceListResult

    /** The call never reached the partner; without a cache there is nothing to show (SPEC D8, S4). */
    data object Offline : DeviceListResult

    /** The partner does not know a device the call named (SPEC E2). */
    data object DeviceNotFound : DeviceListResult

    /** The answer was not the documented envelope (SPEC E3). */
    data object UnexpectedResponse : DeviceListResult

    /** The partner named an error of its own; its message stays in the exception (SPEC U6). */
    data object Failed : DeviceListResult
}

private fun Throwable.toResult(): DeviceListResult = when (this) {
    is SmartHomeException -> toResult()
    else -> DeviceListResult.Failed
}

/**
 * No `else` on purpose: [SmartHomeException] is sealed, so a subtype added by a later slice
 * fails to compile here until someone decides what the list does with it — which is the whole
 * of SPEC E2.
 */
private fun SmartHomeException.toResult(): DeviceListResult = when (this) {
    is SmartHomeException.TokenRejected -> DeviceListResult.TokenRejected
    is SmartHomeException.TokenExpired -> DeviceListResult.TokenExpired(serverMessage)
    is SmartHomeException.DeviceNotFound -> DeviceListResult.DeviceNotFound
    is SmartHomeException.Offline -> DeviceListResult.Offline
    is SmartHomeException.UnexpectedResponse -> DeviceListResult.UnexpectedResponse
    is SmartHomeException.QuotaExceeded -> DeviceListResult.Failed
    is SmartHomeException.Forbidden -> DeviceListResult.Failed
    is SmartHomeException.ApiError -> DeviceListResult.Failed
}
