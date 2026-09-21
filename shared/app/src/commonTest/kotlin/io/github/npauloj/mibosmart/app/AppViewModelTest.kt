package io.github.npauloj.mibosmart.app

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** SPEC S2: the app opens on the token screen and only an accepted token moves it on. */
class AppViewModelTest {

    @Test
    fun theAppStartsOnTheTokenScreenUntilATokenIsAccepted() {
        val viewModel = AppViewModel()

        assertFalse(viewModel.authenticated.value, "a process with no session starts on the token screen")

        viewModel.onAuthenticated()

        assertTrue(viewModel.authenticated.value)
    }
}
