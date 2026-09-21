package io.github.npauloj.mibosmart.data.device

import io.github.npauloj.mibosmart.data.local.DeviceCache
import io.github.npauloj.mibosmart.data.remote.DeviceDto
import io.github.npauloj.mibosmart.data.remote.SmartHomeApi
import io.github.npauloj.mibosmart.data.remote.smartHomeJson
import io.github.npauloj.mibosmart.data.remote.toDevice
import io.github.npauloj.mibosmart.data.remote.wireValue
import io.github.npauloj.mibosmart.domain.device.CachedDevices
import io.github.npauloj.mibosmart.domain.device.DevicePage
import io.github.npauloj.mibosmart.domain.device.DeviceRepository
import io.github.npauloj.mibosmart.domain.device.OriginFilter
import io.github.npauloj.mibosmart.domain.device.orderedForList
import io.github.npauloj.mibosmart.domain.error.SmartHomeException
import io.github.npauloj.mibosmart.domain.session.SessionStore
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer

/**
 * The account's devices, from `listar-dispositivos` (SPEC D1).
 *
 * The credential is read from the [SessionStore] rather than handed down from the screen: the token
 * is the one value ADR-008 keeps out of the upper layers, and a use case that had to carry it would
 * put it in a ViewModel's state.
 */
internal class SmartHomeDeviceRepository(
    private val api: SmartHomeApi,
    private val sessionStore: SessionStore,
    private val cache: DeviceCache,
    private val now: () -> Instant = { Clock.System.now() },
) : DeviceRepository {

    override suspend fun page(origin: OriginFilter, page: Int): DevicePage {
        // No session, no call: spending a request to be told 401 is the one outcome already known
        // (ADR-006). The guard that routes back to the token screen reacts to the same type (SPEC D9).
        val token = sessionStore.read()?.token ?: throw SmartHomeException.TokenRejected()
        val payload = api.listDevices(token, pageSize = PAGE_SIZE, page = page, origin = origin.wireValue)
        val dtos = try {
            smartHomeJson.decodeFromJsonElement(ListSerializer(DeviceDto.serializer()), payload)
        } catch (malformed: SerializationException) {
            throw SmartHomeException.UnexpectedResponse("`data` is not a list of devices", malformed)
        }
        val devices = dtos.map(DeviceDto::toDevice).orderedForList()
        // SPEC D10: page 1 is the page the next cold start renders (SPEC U2) and the one an offline
        // retry falls back on (SPEC D8). Only page 1, and only whole: the cache is a *first screen*,
        // not a copy of the account, and a page 2 written over it would open the app halfway down a
        // list. It is written for whichever filter fetched it, which is the same filter the next
        // launch restores (SPEC D4) — that is what keeps the two from disagreeing.
        if (page == FIRST_PAGE) cache.write(devices, now())
        // Blind pagination: `data` is a bare array with no total and no page count
        // (`docs/api-contract.md` §3), so a page exactly as long as the one requested is the only
        // evidence another may exist — and a short one is proof none does (SPEC D2).
        return DevicePage(devices = devices, hasMore = dtos.size == PAGE_SIZE)
    }

    override suspend fun cachedPage(): CachedDevices? = cache.read()

    private companion object {
        /** SPEC D1: the page size the contract documents, and the one the account is billed for. */
        const val PAGE_SIZE = 20
        const val FIRST_PAGE = 1
    }
}
