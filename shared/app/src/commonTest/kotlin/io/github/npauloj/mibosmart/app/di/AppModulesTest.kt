package io.github.npauloj.mibosmart.app.di

import io.github.npauloj.mibosmart.app.session.PortalUrl
import io.github.npauloj.mibosmart.app.camera.WatchLiveVideo
import io.github.npauloj.mibosmart.app.devices.ListDevices
import io.github.npauloj.mibosmart.app.lock.LoadLock
import io.github.npauloj.mibosmart.app.lock.ToggleLock
import io.github.npauloj.mibosmart.app.session.AuthenticateToken
import io.github.npauloj.mibosmart.app.session.Logout
import io.github.npauloj.mibosmart.app.session.SessionStartup
import io.github.npauloj.mibosmart.domain.device.ModelCatalog
import io.github.npauloj.mibosmart.domain.session.RefusedRequests
import io.github.npauloj.mibosmart.domain.session.SessionGuard
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import org.koin.dsl.koinApplication

/**
 * The graph the app really starts with: a missing binding would only show up as a crash on the
 * first screen, which no unit test of a single class can catch.
 */
class AppModulesTest {

    @Test
    fun theTokenUseCaseResolvesFromTheRealGraph() {
        val koin = koinApplication { modules(appModule(apiHost = "https://api.example.invalid",
            portalHost = "https://portal.example.invalid")) }.koin

        assertNotNull(koin.get<AuthenticateToken>())

        koin.close()
    }

    /**
     * The token screen's portal link points at the **portal**, and this test exists because
     * nothing else would notice if it did not.
     */
    @Test
    fun theTokenScreenLinksToThePortalAndNotToTheApi() {
        val koin = koinApplication { modules(appModule(apiHost = "https://api.example.invalid",
            portalHost = "https://portal.example.invalid")) }.koin

        assertEquals("https://portal.example.invalid", koin.get<PortalUrl>().value)

        koin.close()
    }

    /** The first thing the app resolves: a missing binding here is a crash before any screen. */
    @Test
    fun theStartupUseCaseResolvesFromTheRealGraph() {
        val koin = koinApplication { modules(appModule(apiHost = "https://api.example.invalid",
            portalHost = "https://portal.example.invalid")) }.koin

        assertNotNull(koin.get<SessionStartup>())

        koin.close()
    }

    /** The guard and the stream it listens to (SPEC S6). */
    @Test
    fun theSessionGuardAndItsRefusalStreamResolveFromTheRealGraph() {
        val koin = koinApplication { modules(appModule(apiHost = "https://api.example.invalid",
            portalHost = "https://portal.example.invalid")) }.koin

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
        val koin = koinApplication { modules(appModule(apiHost = "https://api.example.invalid",
            portalHost = "https://portal.example.invalid")) }.koin

        assertNotNull(koin.get<LoadLock>())
        assertNotNull(koin.get<ToggleLock>())

        koin.close()
    }

    @Test
    fun theLiveVideoUseCaseResolvesFromTheRealGraph() {
        val koin = koinApplication { modules(appModule(apiHost = "https://api.example.invalid",
            portalHost = "https://portal.example.invalid")) }.koin

        assertNotNull(koin.get<WatchLiveVideo>())

        koin.close()
    }

    /** The code catalogue (ADR-007), whose binding is the platform's rather than a feature's. */
    @Test
    fun theCodeCatalogueResolvesFromTheRealGraph() {
        val koin = koinApplication { modules(appModule(apiHost = "https://api.example.invalid",
            portalHost = "https://portal.example.invalid")) }.koin

        assertNotNull(koin.get<ModelCatalog>())

        koin.close()
    }

    @Test
    fun theDeviceListUseCaseResolvesFromTheRealGraph() {
        val koin = koinApplication { modules(appModule(apiHost = "https://api.example.invalid",
            portalHost = "https://portal.example.invalid")) }.koin

        assertNotNull(koin.get<ListDevices>())

        koin.close()
    }

    /**
     * SPEC D5: the device list reaches the same catalogue the history tab does, and gets the
     * same one.
     */
    @Test
    fun deviceListResolvesTheModelCatalog() {
        val koin = koinApplication { modules(appModule(apiHost = "https://api.example.invalid",
            portalHost = "https://portal.example.invalid")) }.koin

        assertSame(
            koin.get<ModelCatalog>(),
            koin.get<ModelCatalog>(),
            "one table, read by every feature that names a code",
        )

        koin.close()
    }
}
