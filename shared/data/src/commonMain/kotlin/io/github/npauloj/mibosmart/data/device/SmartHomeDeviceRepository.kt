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

/** The account's devices, from `listar-dispositivos` (SPEC D1). */
internal class SmartHomeDeviceRepository(
    private val api: SmartHomeApi,
    private val sessionStore: SessionStore,
    private val cache: DeviceCache,
    private val now: () -> Instant = { Clock.System.now() },
) : DeviceRepository {

    override suspend fun page(origin: OriginFilter, page: Int): DevicePage {
        val token = sessionStore.read()?.token ?: throw SmartHomeException.TokenRejected()
        val payload = api.listDevices(token, pageSize = PAGE_SIZE, page = page, origin = origin.wireValue)
        val dtos = try {
            smartHomeJson.decodeFromJsonElement(ListSerializer(DeviceDto.serializer()), payload)
        } catch (malformed: SerializationException) {
            throw SmartHomeException.UnexpectedResponse("`data` is not a list of devices", malformed)
        }
        val devices = dtos.map(DeviceDto::toDevice).orderedForList()
        if (page == FIRST_PAGE) cache.write(devices, now())
        return DevicePage(devices = devices, hasMore = dtos.size == PAGE_SIZE)
    }

    override suspend fun cachedPage(): CachedDevices? = cache.read()

    private companion object {
        /** SPEC D1: the page size the contract documents, and the one the account is billed for. */
        const val PAGE_SIZE = 20
        const val FIRST_PAGE = 1
    }
}
