package io.github.npauloj.mibosmart.app.lock

import io.github.npauloj.mibosmart.domain.lock.LockAddress
import io.github.npauloj.mibosmart.domain.lock.LockCommand
import io.github.npauloj.mibosmart.domain.lock.LockRepository
import io.github.npauloj.mibosmart.domain.lock.LockState
import io.github.npauloj.mibosmart.domain.lock.VolumeLevel

/**
 * A lock that answers whatever the test wants and records every call it was asked for.
 *
 * "Which calls, in what order, how many" is part of the behaviour under test: the account pays for
 * each one (ADR-006), SPEC L1 says there are exactly three reads on entry, and the lock kill switch
 * is only worth anything if [calls] stays at zero when it is off.
 *
 * @param answer runs inside every **read**, before it returns: a gate a test can hold open, or a
 *   failure to raise.
 * @param answerWrite the same for every **write**. It is separate because the two halves fail
 *   independently — a lock whose reads work and whose writes are refused is the interesting case.
 * @param remoteOpenAfterEnabling what `status-abrir-remoto` answers **after** a successful
 *   `habilitar-abrir-remoto`. It is `false` in the test that checks the app believes the re-read
 *   rather than the write.
 * @param answerConfirmation runs inside `status-abertura` **only after a command was sent** — the
 *   confirmation read of SPEC L3. It is separate from [answer] because the interesting case is a
 *   lock whose three reads on entry work and whose confirmation never comes.
 * @param obeysCommands whether `controle-fechadura` actually moves the door. `false` is the lock
 *   that takes the command and does nothing — the disagreement of SPEC L4.
 */
internal class FakeLockRepository(
    private val state: LockState = LockSamples.Locked,
    private val answer: suspend () -> Unit = {},
    private val answerWrite: suspend () -> Unit = {},
    private val remoteOpenAfterEnabling: Boolean = true,
    private val answerConfirmation: suspend () -> Unit = {},
    private val obeysCommands: Boolean = true,
) : LockRepository {

    val reads = mutableListOf<Read>()
    val writes = mutableListOf<Write>()

    /** Every request this lock was asked to make, read or write — what the account is billed for. */
    val calls: Int get() = reads.size + writes.size

    private var isRemoteOpenEnabled = state.isRemoteOpenEnabled
    private var isOpen = state.isOpen

    override suspend fun readOpenState(address: LockAddress): Boolean {
        record(Read(Read.OPEN_STATE, address))
        if (writes.any { it is Write.Command }) answerConfirmation()
        return isOpen
    }

    override suspend fun readRemoteOpenEnabled(address: LockAddress): Boolean {
        record(Read(Read.REMOTE_OPEN, address))
        return isRemoteOpenEnabled
    }

    override suspend fun readVolume(address: LockAddress): VolumeLevel {
        record(Read(Read.VOLUME, address))
        return state.volume
    }

    override suspend fun changeVolume(address: LockAddress, volume: VolumeLevel) {
        writes += Write.Volume(address, volume)
        answerWrite()
    }

    /**
     * The door moves only if the call succeeded **and** the lock was told to obey: a real
     * `controle-fechadura` acknowledges the command, and whether the hardware follows is a separate
     * question (SPEC L3).
     */
    override suspend fun command(address: LockAddress, command: LockCommand) {
        writes += Write.Command(address, command)
        answerWrite()
        if (obeysCommands) isOpen = command.opensTheDoor
    }

    /**
     * The grant only takes effect if the call itself succeeded — [answerWrite] runs first, and a
     * failing one leaves the flag exactly where it was, like the partner would.
     */
    override suspend fun enableRemoteOpen(address: LockAddress) {
        writes += Write.RemoteOpen(address)
        answerWrite()
        isRemoteOpenEnabled = remoteOpenAfterEnabling
    }

    /** Recorded before suspending, so a test can see what is in flight while nothing has answered. */
    private suspend fun record(read: Read) {
        reads += read
        answer()
    }

    data class Read(val endpoint: String, val address: LockAddress) {
        companion object {
            const val OPEN_STATE = "status-abertura"
            const val REMOTE_OPEN = "status-abrir-remoto"
            const val VOLUME = "volume"

            /** The three reads of SPEC L1: what opening the screen costs. */
            val ALL = setOf(OPEN_STATE, REMOTE_OPEN, VOLUME)
        }
    }

    /**
     * A call that changes the lock.
     *
     * [RemoteOpen] carries no flag, and that is the contract being asserted: the repository has no
     * way to say `habilitar: false`, so no test can accidentally prove the app can disable a door's
     * safety net (SPEC L2). The exact bytes are `LockRequestsTest`'s.
     */
    sealed interface Write {

        val address: LockAddress

        data class Volume(override val address: LockAddress, val level: VolumeLevel) : Write

        data class RemoteOpen(override val address: LockAddress) : Write

        /** `controle-fechadura` — the only call in the app that moves something physical. */
        data class Command(override val address: LockAddress, val command: LockCommand) : Write
    }
}
