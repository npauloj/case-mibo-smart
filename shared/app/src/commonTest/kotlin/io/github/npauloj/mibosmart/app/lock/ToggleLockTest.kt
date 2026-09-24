package io.github.npauloj.mibosmart.app.lock

import io.github.npauloj.mibosmart.domain.device.DeviceStatus
import io.github.npauloj.mibosmart.domain.error.SmartHomeException
import io.github.npauloj.mibosmart.domain.lock.LockCommand
import io.github.npauloj.mibosmart.domain.lock.VolumeLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * SPEC L3, L4, L6 and the command half of L5: **the API acknowledges a command, the device
 * confirms it, and the screen is only ever allowed to show the second.**
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ToggleLockTest {

    /**
     * SPEC L3: the command goes out, `status-abertura` agrees, and only then does the screen
     * move.
     */
    @Test
    fun happyPathConfirmsWithStatusRead() = runTest {
        val repository = FakeLockRepository()
        val viewModel = lockViewModel(repository)
        viewModel.onOpen(LockSamples.destination())
        val afterEntry = repository.calls

        viewModel.onCommand(LockCommand.Open)

        assertEquals(
            listOf<FakeLockRepository.Write>(
                FakeLockRepository.Write.Command(LockSamples.Address, LockCommand.Open),
            ),
            repository.writes.toList(),
            "controle-fechadura must carry the command the user chose, addressed like every lock call",
        )
        assertEquals(
            FakeLockRepository.Read.OPEN_STATE,
            repository.reads.last().endpoint,
            "the command was believed without re-reading status-abertura",
        )
        assertEquals(2, repository.calls - afterEntry, "a command costs one write and one read, always")
        val settled = assertIs<LockUiState.Ready>(viewModel.state.value, "a confirmed command settles")
        assertTrue(settled.lock.isOpen, "the lock confirmed the door is open and the screen still says closed")
        assertEquals(LockSamples.Locked.volume, settled.lock.volume, "a command disturbed another reading")
    }

    /** SPEC L4: the lock took the command and did not obey. The screen says so and stops. */
    @Test
    fun disagreementBecomesCommandExpiredNoPolling() = runTest {
        val repository = FakeLockRepository(obeysCommands = false)
        val viewModel = lockViewModel(repository)
        viewModel.onOpen(LockSamples.destination())
        val afterEntry = repository.calls

        viewModel.onCommand(LockCommand.Open)

        val expired = assertIs<LockUiState.CommandExpired>(
            viewModel.state.value,
            "a command the device never confirmed must not settle the screen",
        )
        assertEquals(LockCommand.Open, expired.command)
        assertFalse(expired.before.lock.isOpen, "the screen shows the door asked for, not the door reported")
        assertEquals(2, repository.calls - afterEntry)

        advanceTimeBy(10.minutes)
        advanceUntilIdle()
        assertEquals(afterEntry + 2, repository.calls, "the screen polled a lock on a 300-request budget")

        viewModel.onVerify()

        assertEquals(afterEntry + 3, repository.calls, "Verificar must spend exactly one more read")
        assertIs<LockUiState.CommandExpired>(
            viewModel.state.value,
            "a check that still disagrees has still not confirmed anything",
        )
    }

    /** SPEC L4, the other way to not be confirmed: the read never answers. */
    @Test
    fun timeoutBecomesCommandExpired() = runTest {
        val neverAnswers = CompletableDeferred<Unit>()
        val repository = FakeLockRepository(answerConfirmation = { neverAnswers.await() })
        val viewModel = lockViewModel(repository)
        viewModel.onOpen(LockSamples.destination())
        val startedAt = testScheduler.currentTime

        viewModel.onCommand(LockCommand.Open)

        assertEquals(
            ToggleLock.CONFIRMATION_WINDOW.inWholeMilliseconds,
            testScheduler.currentTime - startedAt,
            "the app waited for the confirmation for something other than the documented window",
        )
        val expired = assertIs<LockUiState.CommandExpired>(viewModel.state.value)
        assertEquals(LockCommand.Open, expired.command)
        assertFalse(expired.before.lock.isOpen, "nothing reported the door open, so nothing may show it open")
    }

    /** SPEC L5: the command never left, so the screen says why and then goes back to the lock. */
    @Test
    fun networkFailureShowsCommandFailedThenRestores() = runTest {
        val repository = FakeLockRepository(answerWrite = { throw SmartHomeException.Offline(null) })
        val viewModel = lockViewModel(repository)
        viewModel.onOpen(LockSamples.destination())

        val command = launch { viewModel.onCommand(LockCommand.Open) }
        runCurrent()

        val failed = assertIs<LockUiState.CommandFailed>(viewModel.state.value)
        assertEquals(LockError.Offline, failed.error)
        assertEquals(LockSamples.Locked, failed.before.lock, "a failed command changed the readings")

        advanceUntilIdle()
        command.join()

        val restored = assertIs<LockUiState.Ready>(viewModel.state.value, "the notice never came down")
        assertEquals(LockSamples.Locked, restored.lock)
        assertEquals(1, repository.writes.size, "a failed command must not be retried on its own")
    }

    /** SPEC L5, last clause: an offline lock keeps its last known state and takes no command. */
    @Test
    fun offlineShowsLastKnownState() = runTest {
        val repository = FakeLockRepository()
        val viewModel = lockViewModel(repository)
        viewModel.onOpen(
            LockSamples.destination(status = DeviceStatus.Offline, lastSeen = LockSamples.Now - 3.hours),
        )

        viewModel.onCommand(LockCommand.Open)

        val state = assertIs<LockUiState.Ready>(viewModel.state.value, "an offline lock is still readable")
        assertTrue(state.isOffline)
        assertEquals(LastSeen.Hours(3), state.lastSeen, "the user is not told how old the reading is")
        assertFalse(state.canCommand, "the control has to be dead, not just look dead")
        assertEquals(emptyList(), repository.writes, "a command reached a lock the hub cannot see")
        assertEquals(LockSamples.Locked, state.lock, "the last known state was thrown away")
    }

    /** SPEC L6: a door is the last place a second tap should reach. The guard is the state itself. */
    @Test
    fun ignoresDoubleTapWhileCommandSent() = runTest {
        val partnerAnswered = CompletableDeferred<Unit>()
        val repository = FakeLockRepository(answerWrite = { partnerAnswered.await() })
        val viewModel = lockViewModel(repository)
        viewModel.onOpen(LockSamples.destination())

        val first = launch { viewModel.onCommand(LockCommand.Open) }
        advanceUntilIdle()
        assertIs<LockUiState.CommandSent>(viewModel.state.value)

        viewModel.onCommand(LockCommand.Open)
        viewModel.onChangeVolume(VolumeLevel.High)

        assertEquals(1, repository.writes.size, "a second request left while the first was in flight")

        partnerAnswered.complete(Unit)
        first.join()
        assertTrue(assertIs<LockUiState.Ready>(viewModel.state.value).lock.isOpen)
    }

    /** The kill switch, at the only place that can guarantee anything: **no request at all**. */
    @Test
    fun killSwitchOffSendsNoCommand() = runTest {
        val repository = FakeLockRepository()

        val result = ToggleLock(repository, LockWritesSwitch(isOn = false))(
            LockSamples.Address,
            LockCommand.Open,
            LockSamples.Locked,
        )

        assertEquals(ToggleLockResult.WritesDisabled, result)
        assertEquals(0, repository.calls, "a build with lock writes off must reach the partner zero times")
    }

    /** SPEC U6 on the one action `CommandExpired` offers: a check that cannot answer says so. */
    @Test
    fun aCheckThatCannotAnswerNamesTheReason() = runTest {
        val repository = FakeLockRepository(
            answerConfirmation = { throw SmartHomeException.Offline(null) },
        )
        val viewModel = lockViewModel(repository)
        viewModel.onOpen(LockSamples.destination())
        viewModel.onCommand(LockCommand.Open)
        assertNull(
            assertIs<LockUiState.CommandExpired>(viewModel.state.value).checkFailure,
            "the confirmation read is not the user's question; it has nothing of its own to report",
        )

        viewModel.onVerify()

        val checked = assertIs<LockUiState.CommandExpired>(viewModel.state.value)
        assertEquals(LockError.Offline, checked.checkFailure)
        assertFalse(checked.isChecking, "the action stayed disabled after the check came back")
    }
}
