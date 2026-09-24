package io.github.npauloj.mibosmart.data.lock

import io.github.npauloj.mibosmart.data.remote.LockOpenStateDto
import io.github.npauloj.mibosmart.data.remote.LockOpeningEventDto
import io.github.npauloj.mibosmart.data.remote.LockRemoteOpenDto
import io.github.npauloj.mibosmart.data.remote.LockRequests
import io.github.npauloj.mibosmart.data.remote.LockVolumeDto
import io.github.npauloj.mibosmart.data.remote.SmartHomeApi
import io.github.npauloj.mibosmart.data.remote.toOpeningEvent
import io.github.npauloj.mibosmart.domain.error.SmartHomeException
import io.github.npauloj.mibosmart.domain.lock.LockAddress
import io.github.npauloj.mibosmart.domain.lock.LockCommand
import io.github.npauloj.mibosmart.domain.lock.LockRepository
import io.github.npauloj.mibosmart.domain.lock.OpeningEvent
import io.github.npauloj.mibosmart.domain.lock.VolumeLevel
import io.github.npauloj.mibosmart.domain.session.SessionStore
import io.github.npauloj.mibosmart.domain.session.Token
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * The lock's whole surface against the partner API: four reads (SPEC L1, L9) and three writes
 * (SPEC L3, L6, L7).
 */
internal class SmartHomeLockRepository(
    private val api: SmartHomeApi,
    private val sessionStore: SessionStore,
    private val json: Json,
) : LockRepository {

    override suspend fun readOpenState(address: LockAddress): Boolean =
        decode(LockOpenStateDto.serializer(), api.readLockOpenState(token(), LockRequests.read(address))).isOpen

    override suspend fun readRemoteOpenEnabled(address: LockAddress): Boolean =
        decode(LockRemoteOpenDto.serializer(), api.readLockRemoteOpen(token(), LockRequests.read(address))).isEnabled

    override suspend fun readVolume(address: LockAddress): VolumeLevel {
        val level = decode(LockVolumeDto.serializer(), api.readLockVolume(token(), LockRequests.volume(address))).level
        return VolumeLevel.ofLevel(level)
            ?: throw SmartHomeException.UnexpectedResponse("the lock reported a volume outside 0..3")
    }

    /** `historico-abertura` (SPEC L9): one request, and the `data` payload is a JSON **array**. */
    override suspend fun readOpeningHistory(address: LockAddress, entries: Int): List<OpeningEvent> =
        decode(
            ListSerializer(LockOpeningEventDto.serializer()),
            api.readLockOpeningHistory(token(), LockRequests.openingHistory(address, entries)),
        ).map { it.toOpeningEvent() }

    /** `mudar-volume` (SPEC L7). */
    override suspend fun changeVolume(address: LockAddress, volume: VolumeLevel) {
        api.changeLockVolume(token(), LockRequests.changeVolume(address, volume))
    }

    /**
     * `controle-fechadura` (SPEC L3): the request the whole confirmation state machine exists
     * for.
     */
    override suspend fun command(address: LockAddress, command: LockCommand) {
        api.commandLock(token(), LockRequests.command(address, command))
    }

    /** `habilitar-abrir-remoto`, always with `habilitar: true` (SPEC L2); same silence on the body. */
    override suspend fun enableRemoteOpen(address: LockAddress) {
        api.enableLockRemoteOpen(token(), LockRequests.enableRemoteOpen(address))
    }

    /** The session's credential. */
    private suspend fun token(): Token =
        sessionStore.read()?.token ?: throw SmartHomeException.TokenRejected()

    /** The partner's `data` payload as [T], or [SmartHomeException.UnexpectedResponse] (SPEC E3). */
    private fun <T> decode(strategy: DeserializationStrategy<T>, payload: JsonElement): T = try {
        json.decodeFromJsonElement(strategy, payload)
    } catch (malformed: SerializationException) {
        throw SmartHomeException.UnexpectedResponse("the lock payload is not the documented shape", malformed)
    }
}
