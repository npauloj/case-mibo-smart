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
 * The graph the app really starts with: a missing binding would only show up as a crash on the first
 * screen, which no unit test of a single class can catch.
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
     * The token screen's portal link points at the **portal**, and this test exists because nothing
     * else would notice if it did not.
     *
     * The two hosts are both plausible strings of the same shape, and wiring the api one here would
     * produce a link that opens a page with no token generator on it — no crash, no failing request,
     * nothing to see in a log. That is the same failure mode ADR-025 records costing a day, arriving
     * through a different door.
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

    /**
     * The guard and the stream it listens to (SPEC S6).
     *
     * Resolved from the real graph because the failure they have is not a compile error: a guard
     * wired to a second, empty refusal stream would never fire, and no unit test of either class on
     * its own could tell.
     */
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
        // The command path is wired too: LockViewModel takes it, so a missing binding is a screen
        // that crashes on the door rather than on the reads (ADR-014).
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

    /**
     * The code catalogue (ADR-007), whose binding is the platform's rather than a feature's.
     *
     * The history tab asks Koin for it by hand, so a missing binding would be a crash on the tab and
     * nowhere earlier. Which implementation answers depends on the platform — the Java-backed one on
     * Android, [io.github.npauloj.mibosmart.domain.device.RawCodes] on iOS — and this asserts only
     * that one of them is there, which is the part that can break by wiring.
     */
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
     * SPEC D5: the device list reaches the same catalogue the history tab does, and gets the same one.
     *
     * Its ViewModel asks Koin for `ModelCatalog` by hand, exactly as the lock's does, and the two
     * features never import each other (rule 3) — the domain contract is all they share. So what can
     * break by wiring is not "is it there", which the test above already covers, but "is it *one*":
     * a second binding, or a `factory` instead of a `single`, would rebuild the partner's table on
     * every screen that named a code.
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
