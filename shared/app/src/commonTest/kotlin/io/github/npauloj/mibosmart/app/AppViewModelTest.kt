package io.github.npauloj.mibosmart.app

import io.github.npauloj.mibosmart.app.session.TokenSamples
import io.github.npauloj.mibosmart.data.session.InMemorySessionStore
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * The state the app holds between screens (ADR-003, ADR-010). Which screen a session opens is SPEC
 * S5's, and lives in `session/SessionStartTest`; this pins what the state says before it knows.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /**
     * Nothing is routed to until the vault has answered.
     *
     * A default of "token screen" would flash the way-in at a signed-in user for a frame, and a
     * default of "device list" would show a session the store cannot back — the failure ADR-010 is
     * about. The honest default is "not known yet", and it is only one vault read wide.
     */
    @Test
    fun noDestinationIsChosenBeforeTheStoreAnswers() = runTest(dispatcher) {
        val viewModel = appViewModel(InMemorySessionStore(), FixedClock(TokenSamples.Now))

        assertNull(viewModel.state.value.destination, "a destination was guessed before the vault read")
        assertFalse(viewModel.state.value.expiringSoon)
    }
}
