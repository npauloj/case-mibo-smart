package io.github.npauloj.mibosmart.app.lock

import io.github.npauloj.mibosmart.domain.error.SmartHomeException
import io.github.npauloj.mibosmart.domain.lock.VolumeLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

/** SPEC L7: the volume the screen shows is the volume the lock confirmed, never the one asked for. */
@OptIn(ExperimentalCoroutinesApi::class)
class ChangeVolumeTest {

    /**
     * The request carries the level the user picked, and the screen shows it **only after** the
     * partner answers.
     *
     * The gate is what makes "not optimistic" provable: while `mudar-volume` is in flight the
     * selector still reads Low, the level the lock actually reported on entry. A screen that moved
     * on the tap would claim a quieter door than the one in the hallway.
     */
    @Test
    fun exactRequestAndOptimisticOff() = runTest {
        val partnerAnswered = CompletableDeferred<Unit>()
        val repository = FakeLockRepository(answerWrite = { partnerAnswered.await() })
        val viewModel = lockViewModel(repository)
        viewModel.onOpen(LockSamples.destination())

        val change = launch { viewModel.onChangeVolume(VolumeLevel.High) }
        advanceUntilIdle()

        assertEquals(
            listOf<FakeLockRepository.Write>(
                FakeLockRepository.Write.Volume(LockSamples.Address, VolumeLevel.High),
            ),
            repository.writes.toList(),
            "mudar-volume must carry the level the user picked, addressed like every other lock call",
        )
        val inFlight = assertIs<LockUiState.Ready>(viewModel.state.value)
        assertEquals(VolumeLevel.Low, inFlight.lock.volume, "the screen moved before the lock did")
        assertEquals(LockWrite.Volume(VolumeLevel.High), inFlight.writeInFlight)

        partnerAnswered.complete(Unit)
        change.join()

        val settled = assertIs<LockUiState.Ready>(viewModel.state.value)
        assertEquals(VolumeLevel.High, settled.lock.volume, "the confirmed level never reached the screen")
        assertNull(settled.writeInFlight)
        assertNull(settled.writeFailure)
    }

    /**
     * The kill switch, at the only place that can guarantee anything: **no request at all**.
     *
     * A disabled chip is an affordance; this is the rule. The counter covers reads too, so a build
     * with lock writes off cannot even spend the budget finding out it may not write.
     */
    @Test
    fun killSwitchOffSendsNothing() = runTest {
        val repository = FakeLockRepository()

        val result = ChangeVolume(repository, LockWritesSwitch(isOn = false))(
            LockSamples.Address,
            VolumeLevel.High,
        )

        assertEquals(ChangeVolumeResult.WritesDisabled, result)
        assertEquals(0, repository.calls, "a build with lock writes off must reach the partner zero times")
    }

    /** The same, from the screen: the chips are dead and the user is told why (SPEC U6). */
    @Test
    fun killSwitchOffLeavesTheScreenSayingSo() = runTest {
        val repository = FakeLockRepository()
        val viewModel = lockViewModel(repository, writesEnabled = false)

        viewModel.onOpen(LockSamples.destination())
        viewModel.onChangeVolume(VolumeLevel.High)

        val state = assertIs<LockUiState.Ready>(viewModel.state.value)
        assertEquals(false, state.areWritesEnabled, "the screen has to disable what the build refuses")
        assertEquals(emptyList(), repository.writes)
        assertEquals(VolumeLevel.Low, state.lock.volume)
    }

    /** A refused write leaves the reading alone and names the reason beside its control (SPEC U6). */
    @Test
    fun aFailedWriteKeepsTheLevelTheLockIsAt() = runTest {
        val repository = FakeLockRepository(answerWrite = { throw SmartHomeException.Offline(null) })
        val viewModel = lockViewModel(repository)

        viewModel.onOpen(LockSamples.destination())
        viewModel.onChangeVolume(VolumeLevel.High)

        val state = assertIs<LockUiState.Ready>(viewModel.state.value, "a failed write is not a failed screen")
        assertEquals(VolumeLevel.Low, state.lock.volume)
        assertEquals(
            WriteFailure(LockWrite.Volume(VolumeLevel.High), LockError.Offline),
            state.writeFailure,
        )
    }

    /** The partner's own sentence wins when it sent one (SPEC S3.1). */
    @Test
    fun anExpiredSessionKeepsThePartnersSentence() = runTest {
        val expired = "Token expirado, por favor gere um novo token"
        val repository = FakeLockRepository(answerWrite = { throw SmartHomeException.TokenExpired(expired) })

        val result = ChangeVolume(repository, LockWritesSwitch(isOn = true))(
            LockSamples.Address,
            VolumeLevel.High,
        )

        assertEquals(ChangeVolumeResult.TokenExpired(expired), result)
    }

    /** Picking the level it is already at would spend a request to change nothing (ADR-006). */
    @Test
    fun theLevelItIsAlreadyAtIsNotSent() = runTest {
        val repository = FakeLockRepository()
        val viewModel = lockViewModel(repository)

        viewModel.onOpen(LockSamples.destination())
        viewModel.onChangeVolume(checkNotNull(LockSamples.Locked.volume))

        assertEquals(emptyList(), repository.writes)
    }

    /** A second tap while the first is in flight is inert — a door is worth guarding twice (L6). */
    @Test
    fun aSecondPickWhileTheFirstIsInFlightIsIgnored() = runTest {
        val partnerAnswered = CompletableDeferred<Unit>()
        val repository = FakeLockRepository(answerWrite = { partnerAnswered.await() })
        val viewModel = lockViewModel(repository)
        viewModel.onOpen(LockSamples.destination())

        val first = launch { viewModel.onChangeVolume(VolumeLevel.High) }
        advanceUntilIdle()
        viewModel.onChangeVolume(VolumeLevel.Medium)

        assertEquals(1, repository.writes.size, "the second tap sent a second write")

        partnerAnswered.complete(Unit)
        first.join()
    }
}
