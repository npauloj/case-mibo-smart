package io.github.npauloj.mibosmart.data.lock

import io.github.npauloj.mibosmart.data.remote.LockOpenStateDto
import io.github.npauloj.mibosmart.data.remote.LockRemoteOpenDto
import io.github.npauloj.mibosmart.data.remote.LockRequests
import io.github.npauloj.mibosmart.data.remote.LockVolumeDto
import io.github.npauloj.mibosmart.data.remote.SmartHomeApi
import io.github.npauloj.mibosmart.domain.error.SmartHomeException
import io.github.npauloj.mibosmart.domain.lock.LockAddress
import io.github.npauloj.mibosmart.domain.lock.LockCommand
import io.github.npauloj.mibosmart.domain.lock.LockRepository
import io.github.npauloj.mibosmart.domain.lock.VolumeLevel
import io.github.npauloj.mibosmart.domain.session.SessionStore
import io.github.npauloj.mibosmart.domain.session.Token
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * The three lock reads against the partner API (SPEC L1).
 *
 * The session is read here rather than passed in from above: which credential a request carries is a
 * transport concern, and keeping it out of [LockRepository] is what lets a second partner implement
 * the same three functions (ADR-004).
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

    /**
     * `mudar-volume` (SPEC L7).
     *
     * The success payload of the lock writes was never probed — it changes a real device, so it is
     * open question 4 of `docs/api-contract.md` §8. Nothing here reads it: [EnvelopeReader] has
     * already turned every documented failure into a typed exception by the time this returns, so
     * "it did not throw" is the whole answer, and the caller re-reads the lock when it needs a value.
     */
    override suspend fun changeVolume(address: LockAddress, volume: VolumeLevel) {
        api.changeLockVolume(token(), LockRequests.changeVolume(address, volume))
    }

    /**
     * `controle-fechadura` (SPEC L3): the request the whole confirmation state machine exists for.
     *
     * Same silence on the body, for a stronger reason than the other writes: even a success here
     * means only that the partner accepted the command. Whether the door moved is
     * [readOpenState]'s answer, and the use case above asks it.
     */
    override suspend fun command(address: LockAddress, command: LockCommand) {
        api.commandLock(token(), LockRequests.command(address, command))
    }

    /** `habilitar-abrir-remoto`, always with `habilitar: true` (SPEC L2); same silence on the body. */
    override suspend fun enableRemoteOpen(address: LockAddress) {
        api.enableLockRemoteOpen(token(), LockRequests.enableRemoteOpen(address))
    }

    /**
     * The session's credential.
     *
     * A lock screen reached without a session is, to every screen above, the same thing as a refused
     * one: there is nothing to retry and the only way forward is a new token (SPEC S6). It is
     * reported as such rather than as a new error category nothing else could produce.
     */
    private suspend fun token(): Token =
        sessionStore.read()?.token ?: throw SmartHomeException.TokenRejected()

    /** The partner's `data` payload as [T], or [SmartHomeException.UnexpectedResponse] (SPEC E3). */
    private fun <T> decode(strategy: DeserializationStrategy<T>, payload: JsonElement): T = try {
        json.decodeFromJsonElement(strategy, payload)
    } catch (malformed: SerializationException) {
        throw SmartHomeException.UnexpectedResponse("the lock payload is not the documented shape", malformed)
    }
}
