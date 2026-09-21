package io.github.npauloj.mibosmart.data.device

import io.github.npauloj.mibosmart.data.local.DeviceCache
import io.github.npauloj.mibosmart.data.remote.DeviceDto
import io.github.npauloj.mibosmart.data.remote.SmartHomeApi
import io.github.npauloj.mibosmart.data.remote.smartHomeJson
import io.github.npauloj.mibosmart.data.remote.toDevice
import io.github.npauloj.mibosmart.domain.device.CachedDevices
import io.github.npauloj.mibosmart.domain.device.Device
import io.github.npauloj.mibosmart.domain.device.DeviceRepository
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

    override suspend fun firstPage(): List<Device> {
        // No session, no call: spending a request to be told 401 is the one outcome already known
        // (ADR-006). The guard that routes back to the token screen reacts to the same type (SPEC D9).
        val token = sessionStore.read() ?: throw SmartHomeException.TokenRejected()
        val payload = api.listDevices(token, pageSize = PAGE_SIZE, page = FIRST_PAGE)
        val devices = try {
            smartHomeJson.decodeFromJsonElement(ListSerializer(DeviceDto.serializer()), payload)
        } catch (malformed: SerializationException) {
            throw SmartHomeException.UnexpectedResponse("`data` is not a list of devices", malformed)
        }
        // SPEC D10: a page that arrives is the page the next cold start renders (SPEC U2) and the one
        // an offline retry falls back on (SPEC D8). Written here, with the moment it was learned.
        return devices.map(DeviceDto::toDevice).orderedForList().also { cache.write(it, now()) }
    }

    override suspend fun cachedPage(): CachedDevices? = cache.read()

    private companion object {
        /** SPEC D1: the page size the contract documents, and the one the account is billed for. */
        const val PAGE_SIZE = 20
        const val FIRST_PAGE = 1
    }
}
