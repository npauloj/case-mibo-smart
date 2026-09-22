package io.github.npauloj.mibosmart.app.di

import io.github.npauloj.mibosmart.app.camera.WatchLiveVideo
import io.github.npauloj.mibosmart.app.devices.ListDevices
import io.github.npauloj.mibosmart.app.lock.LoadLock
import io.github.npauloj.mibosmart.app.lock.ToggleLock
import io.github.npauloj.mibosmart.app.session.AuthenticateToken
import io.github.npauloj.mibosmart.app.session.Logout
import io.github.npauloj.mibosmart.app.session.SessionStartup
import io.github.npauloj.mibosmart.domain.session.RefusedRequests
import io.github.npauloj.mibosmart.domain.session.SessionGuard
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import org.koin.dsl.koinApplication

/**
 * The graph the app really starts with: a missing binding would only show up as a crash on the first
 * screen, which no unit test of a single class can catch.
 */
class AppModulesTest {

    @Test
    fun theTokenUseCaseResolvesFromTheRealGraph() {
        val koin = koinApplication { modules(appModule(apiHost = "https://api.example.invalid")) }.koin

        assertNotNull(koin.get<AuthenticateToken>())

        koin.close()
    }

    /** The first thing the app resolves: a missing binding here is a crash before any screen. */
    @Test
    fun theStartupUseCaseResolvesFromTheRealGraph() {
        val koin = koinApplication { modules(appModule(apiHost = "https://api.example.invalid")) }.koin

        assertNotNull(koin.get<SessionStartup>())

        koin.close()
    }

    /**
     * The guard and the stream it listens to (SPEC S6).
     *
     * Resolved from the real graph because the failure they have is not a compile error: a guard
     * wired to a second, empty refusal stream would never fire, and no unit test of either class on
     * its own could tell.
     */
    @Test
    fun theSessionGuardAndItsRefusalStreamResolveFromTheRealGraph() {
        val koin = koinApplication { modules(appModule(apiHost = "https://api.example.invalid")) }.koin

        assertNotNull(koin.get<SessionGuard>())
        assertNotNull(koin.get<Logout>())
        assertSame(
            koin.get<RefusedRequests>(),
            koin.get<RefusedRequests>(),
            "the guard and the transport must share one refusal stream",
        )

        koin.close()
    }

    @Test
    fun theLockUseCaseResolvesFromTheRealGraph() {
        val koin = koinApplication { modules(appModule(apiHost = "https://api.example.invalid")) }.koin

        assertNotNull(koin.get<LoadLock>())
        // The command path is wired too: LockViewModel takes it, so a missing binding is a screen
        // that crashes on the door rather than on the reads (ADR-014).
        assertNotNull(koin.get<ToggleLock>())

        koin.close()
    }

    @Test
    fun theLiveVideoUseCaseResolvesFromTheRealGraph() {
        val koin = koinApplication { modules(appModule(apiHost = "https://api.example.invalid")) }.koin

        assertNotNull(koin.get<WatchLiveVideo>())

        koin.close()
    }

    @Test
    fun theDeviceListUseCaseResolvesFromTheRealGraph() {
        val koin = koinApplication { modules(appModule(apiHost = "https://api.example.invalid")) }.koin

        assertNotNull(koin.get<ListDevices>())

        koin.close()
    }
}
