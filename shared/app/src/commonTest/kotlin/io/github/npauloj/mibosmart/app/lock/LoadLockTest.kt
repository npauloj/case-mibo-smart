package io.github.npauloj.mibosmart.app.lock

import io.github.npauloj.mibosmart.domain.device.DeviceId
import io.github.npauloj.mibosmart.domain.error.SmartHomeException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

/** SPEC L1: how a lock is addressed, and what opening its screen costs. */
@OptIn(ExperimentalCoroutinesApi::class)
class LoadLockTest {

    /**
     * A lock only answers as a sub-device of its hub: the plain namespace returns "Dispositivo
     * não encontrado" (`docs/api-contract.md` §3).
     */
    @Test
    fun compositeAddress() = runTest {
        val repository = FakeLockRepository()

        LoadLock(repository)(LockSamples.Address)

        assertEquals(3, repository.reads.size)
        repository.reads.forEach { read ->
            assertEquals(DeviceId(LockSamples.LOCK_NAMESPACE), read.address.lock, read.endpoint)
            assertEquals(DeviceId(LockSamples.HUB_NAMESPACE), read.address.hub, read.endpoint)
            assertEquals(LockSamples.HUB_PRODUCT_ID, read.address.hubProductId, read.endpoint)
            assertEquals(LockSamples.LOCK_PRODUCT_ID, read.address.lockProductId, read.endpoint)
        }
    }

    /** Three requests, together, once (SPEC L1, ADR-006). */
    @Test
    fun exactlyThreeRequestsInParallel() = runTest {
        val partnerAnswered = CompletableDeferred<Unit>()
        val repository = FakeLockRepository(answer = { partnerAnswered.await() })
        var result: LoadLockResult? = null

        val load = launch { result = LoadLock(repository)(LockSamples.Address) }
        advanceUntilIdle()

        assertEquals(3, repository.reads.size, "the three reads are not in flight together")
        assertEquals(
            FakeLockRepository.Read.ALL,
            repository.reads.map { it.endpoint }.toSet(),
            "the screen entry must read the open state, the remote-open precondition and the volume",
        )

        partnerAnswered.complete(Unit)
        load.join()

        assertEquals(3, repository.reads.size, "no read may be issued twice: the account pays for each")
        val loaded = assertIs<LoadLockResult.Loaded>(result)
        assertEquals(LockSamples.Locked, loaded.lock)
    }

    /** The volume may refuse without taking the door's state with it (ADR-026). */
    @Test
    fun aVolumeThatRefusesDoesNotTakeTheDoorWithIt() = runTest {
        val repository = FakeLockRepository(
            volumeFailure = SmartHomeException.UnexpectedResponse("500 Erro desconhecido"),
        )

        val result = LoadLock(repository)(LockSamples.Address)

        val loaded = assertIs<LoadLockResult.Loaded>(result)
        assertEquals(LockSamples.Locked.isOpen, loaded.lock.isOpen)
        assertEquals(LockSamples.Locked.isRemoteOpenEnabled, loaded.lock.isRemoteOpenEnabled)
        assertNull(loaded.lock.volume, "an unread volume must be absent, never guessed")
        assertEquals(3, repository.reads.size, "the volume is still asked for once, and only once")
    }

    /** The two that the screen cannot exist without still veto it. */
    @Test
    fun aDoorStateThatRefusesStillFailsTheScreen() = runTest {
        val repository = FakeLockRepository(answer = { throw SmartHomeException.Offline(null) })

        assertEquals(LoadLockResult.Offline, LoadLock(repository)(LockSamples.Address))
    }

    /** A failed read leaves the screen with a named cause, not with two of three values (ADR-002). */
    @Test
    fun aFailedReadIsNamedAndCancelsTheOthers() = runTest {
        val repository = FakeLockRepository(answer = { throw SmartHomeException.Offline(null) })

        val result = LoadLock(repository)(LockSamples.Address)

        assertEquals(LoadLockResult.Offline, result)
    }

    @Test
    fun aRejectedTokenIsNotRetried() = runTest {
        val repository = FakeLockRepository(answer = { throw SmartHomeException.TokenRejected() })

        val result = LoadLock(repository)(LockSamples.Address)

        assertEquals(LoadLockResult.TokenRejected, result)
        assertTrue(repository.reads.size <= 3, "a refused token must never be retried (SPEC E5)")
    }
}
