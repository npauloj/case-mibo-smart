package io.github.npauloj.mibosmart.app.lock

import io.github.npauloj.mibosmart.domain.error.SmartHomeException
import io.github.npauloj.mibosmart.domain.lock.LockState
import io.github.npauloj.mibosmart.domain.lock.VolumeLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest

/** SPEC L2 (read half) and the request discipline of ADR-006 on the lock screen. */
@OptIn(ExperimentalCoroutinesApi::class)
class LockViewModelTest {

    /**
     * SPEC L2, read half: the precondition is a state the screen explains, not a switch it flips.
     *
     * The open/closed state and the volume stay on screen — the lock is readable, it just refuses
     * commands — and nothing in this slice can grant the precondition: the repository was asked for
     * the three reads of SPEC L1 and for nothing else. The action that enables remote opening is
     * L-01b's, and it does not exist yet, here or anywhere.
     */
    @Test
    fun remoteDisabledIsExplained() = runTest {
        val repository = FakeLockRepository(
            LockState(isOpen = false, isRemoteOpenEnabled = false, volume = VolumeLevel.Mute),
        )
        val viewModel = viewModelWith(repository)

        viewModel.onOpen(LockSamples.destination())

        val state = assertIs<LockUiState.Ready>(viewModel.state.value, "a readable lock is not an error")
        assertTrue(state.isRemoteOpenDisabled, "the user is not told why the lock takes no command")
        assertFalse(state.lock.isOpen, "the door's state is still readable while commands are refused")
        assertEquals(VolumeLevel.Mute, state.lock.volume)
        assertEquals(
            FakeLockRepository.Read.ALL,
            repository.reads.map { it.endpoint }.toSet(),
            "this slice may only read: no request of it changes the lock",
        )
    }

    @Test
    fun aLoadedLockThatAcceptsCommandsIsNotExplainedAway() = runTest {
        val viewModel = viewModelWith(FakeLockRepository())

        viewModel.onOpen(LockSamples.destination())

        val state = assertIs<LockUiState.Ready>(viewModel.state.value)
        assertFalse(state.isRemoteOpenDisabled)
        assertFalse(state.isOffline, "an online lock has nothing to date-stamp")
    }

    /** Entering the same lock twice — a rotation, a back-and-forth — must not pay twice (ADR-006). */
    @Test
    fun theSameDestinationIsReadOnce() = runTest {
        val repository = FakeLockRepository()
        val viewModel = viewModelWith(repository)
        val destination = LockSamples.destination()

        viewModel.onOpen(destination)
        viewModel.onOpen(destination)

        assertEquals(3, repository.reads.size, "three requests per lock, not three per recomposition")
    }

    /** SPEC U6: a failure names its cause and offers exactly one action — asking again. */
    @Test
    fun retryIsTheOneActionAfterAFailure() = runTest {
        val repository = FakeLockRepository(answer = { throw SmartHomeException.Offline(null) })
        val viewModel = viewModelWith(repository)

        viewModel.onOpen(LockSamples.destination())
        assertEquals(LockError.Offline, assertIs<LockUiState.Failed>(viewModel.state.value).error)
        // A failing read cancels the other two, so how many were recorded is not fixed — what matters
        // is that asking again asks the partner again.
        val afterFirstAttempt = repository.reads.size

        viewModel.onRetry()

        assertIs<LockUiState.Failed>(viewModel.state.value, "the partner is still unreachable")
        assertTrue(repository.reads.size > afterFirstAttempt, "the retry never reached the partner")
    }

    /** Nothing to retry before a destination was opened: the intent is inert, not a crash. */
    @Test
    fun retryWithoutADestinationDoesNothing() = runTest {
        val repository = FakeLockRepository()

        viewModelWith(repository).onRetry()

        assertEquals(0, repository.reads.size)
    }

    private fun viewModelWith(repository: FakeLockRepository) =
        LockViewModel(LoadLock(repository), FixedClock())
}
