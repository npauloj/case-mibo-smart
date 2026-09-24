package io.github.npauloj.mibosmart.app.session

import io.github.npauloj.mibosmart.data.session.InMemorySessionStore
import io.github.npauloj.mibosmart.domain.session.Token
import kotlin.test.Test
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

/** SPEC S8: "Sair" takes the credential off the device, and the app has no session afterwards. */
class LogoutTest {

    @Test
    fun clearsSecureStore() = runTest {
        val store = InMemorySessionStore().apply { write(Token(TokenSamples.Valid), TokenSamples.Now) }

        Logout(store)()

        assertNull(store.read(), "the credential survived Sair")
    }

    /** The scenario behind the criterion: signing out and starting cold asks for a token again. */
    @Test
    fun aColdStartAfterLogoutAsksForAToken() = runTest {
        val store = InMemorySessionStore().apply { write(Token(TokenSamples.Valid), TokenSamples.Now) }

        Logout(store)()

        assertNull(SessionStartup(store)(), "a cold start after Sair still found a session")
    }

    /** Signing out twice, or out of a session the vault already lost, is not a failure. */
    @Test
    fun logoutWithoutASessionIsNotAFailure() = runTest {
        val store = InMemorySessionStore()

        Logout(store)()

        assertNull(store.read())
    }
}
