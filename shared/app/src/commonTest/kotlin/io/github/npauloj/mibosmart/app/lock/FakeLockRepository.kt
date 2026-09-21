package io.github.npauloj.mibosmart.app.lock

import io.github.npauloj.mibosmart.domain.lock.LockAddress
import io.github.npauloj.mibosmart.domain.lock.LockRepository
import io.github.npauloj.mibosmart.domain.lock.LockState
import io.github.npauloj.mibosmart.domain.lock.VolumeLevel

/**
 * A lock that answers whatever the test wants and records every read it was asked for.
 *
 * "Which reads, in what order, how many" is part of the behaviour under test: the account pays for
 * each one (ADR-006) and SPEC L1 says there are exactly three of them, together.
 */
internal class FakeLockRepository(
    private val state: LockState = LockSamples.Locked,
    private val answer: suspend () -> Unit = {},
) : LockRepository {

    val reads = mutableListOf<Read>()

    override suspend fun readOpenState(address: LockAddress): Boolean {
        record(Read(Read.OPEN_STATE, address))
        return state.isOpen
    }

    override suspend fun readRemoteOpenEnabled(address: LockAddress): Boolean {
        record(Read(Read.REMOTE_OPEN, address))
        return state.isRemoteOpenEnabled
    }

    override suspend fun readVolume(address: LockAddress): VolumeLevel {
        record(Read(Read.VOLUME, address))
        return state.volume
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

            /** The three reads of SPEC L1 — and, in this slice, everything a lock can be asked. */
            val ALL = setOf(OPEN_STATE, REMOTE_OPEN, VOLUME)
        }
    }
}
