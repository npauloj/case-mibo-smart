package io.github.npauloj.mibosmart.app.devices

import io.github.npauloj.mibosmart.domain.device.CachedDevices
import io.github.npauloj.mibosmart.domain.device.Device
import io.github.npauloj.mibosmart.domain.device.DeviceRepository
import io.github.npauloj.mibosmart.domain.error.SmartHomeException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Instant

/**
 * What opening the device list means: one partner call for page 1, and a closed set of outcomes
 * (SPEC D1, D3, E2).
 *
 * No retry of its own and no fallback *call* — the account pays for every request (ADR-006), and the
 * only retry the app performs is the one the user asks for from the error state. The cache is not a
 * retry: reading it costs nothing, which is what lets SPEC D8 and U2 exist at all.
 */
class ListDevices(private val deviceRepository: DeviceRepository) {

    /**
     * What is already known, before the partner is asked (SPEC U2) — empty when nothing is cached.
     *
     * Deliberately not a [DeviceListResult]: these rows are shown *while* the load runs, so they are
     * not yet an outcome and must not carry the "sem conexão" of [DeviceListResult.Stale].
     */
    suspend fun cached(): List<Device> = cachedPage()?.devices.orEmpty()

    suspend operator fun invoke(): DeviceListResult =
        try {
            deviceRepository.firstPage().let { devices ->
                if (devices.isEmpty()) DeviceListResult.Empty else DeviceListResult.Loaded(devices)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Throwable) {
            failure.toResult().orStaleCache()
        }

    /**
     * SPEC D8: only the call that never arrived falls back on the cache.
     *
     * A rejected or expired token must keep its own outcome — old rows behind a session that no
     * longer works look like a working app and hide the one action the user has to take (SPEC S6).
     */
    private suspend fun DeviceListResult.orStaleCache(): DeviceListResult {
        if (this != DeviceListResult.Offline) return this
        val cached = cachedPage()?.takeIf { it.devices.isNotEmpty() } ?: return this
        return DeviceListResult.Stale(cached.devices, cached.fetchedAt)
    }

    /**
     * A cache that cannot be read is a cache miss.
     *
     * The file is the app's own and nothing above this line can repair it, so a corrupt or
     * unreadable one costs the offline comfort of SPEC D8 and nothing else — never the screen.
     */
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

    /** Page 1 came back with devices, already classified and ordered. */
    data class Loaded(val devices: List<Device>) : DeviceListResult

    /** Page 1 came back empty — a state, not a failure (SPEC D3). */
    data object Empty : DeviceListResult

    /**
     * Page 1 never arrived and the cache had one (SPEC D8): the rows are real, [fetchedAt] is how
     * old they are, and the screen says so instead of showing an empty error.
     */
    data class Stale(val devices: List<Device>, val fetchedAt: Instant) : DeviceListResult

    /** HTTP 401: the guard sends the user back to the token screen (SPEC D9, S6). */
    data object TokenRejected : DeviceListResult

    /**
     * HTTP 403 (SPEC S3.1). [serverMessage] is the partner's own sentence, the one category where
     * the server's words are fit to show (SPEC U6).
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
    // Not a partner outcome but a bug of ours: the list still has to render something.
    else -> DeviceListResult.Failed
}

/**
 * No `else` on purpose: [SmartHomeException] is sealed, so a subtype added by a later slice fails to
 * compile here until someone decides what the list does with it — which is the whole of SPEC E2.
 */
private fun SmartHomeException.toResult(): DeviceListResult = when (this) {
    is SmartHomeException.TokenRejected -> DeviceListResult.TokenRejected
    is SmartHomeException.TokenExpired -> DeviceListResult.TokenExpired(serverMessage)
    is SmartHomeException.DeviceNotFound -> DeviceListResult.DeviceNotFound
    is SmartHomeException.Offline -> DeviceListResult.Offline
    is SmartHomeException.UnexpectedResponse -> DeviceListResult.UnexpectedResponse
    // Streaming quota, which only `criar-fluxo-video` can answer (SPEC V6). Listing devices cannot
    // produce it; the branch exists because the taxonomy is sealed and this `when` has no `else`.
    is SmartHomeException.QuotaExceeded -> DeviceListResult.Failed
    // The account may not call this endpoint. The session is fine, so it must NOT route to the token
    // screen (SPEC S6) — the list fails like any other named error and the credential is untouched.
    is SmartHomeException.Forbidden -> DeviceListResult.Failed
    // The one category that carries the server's own words: they stay in the exception, for the log,
    // and the screen gets a sentence of its own (SPEC U6).
    is SmartHomeException.ApiError -> DeviceListResult.Failed
}
