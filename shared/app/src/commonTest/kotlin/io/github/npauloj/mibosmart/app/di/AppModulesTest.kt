package io.github.npauloj.mibosmart.app.di

import io.github.npauloj.mibosmart.app.devices.ListDevices
import io.github.npauloj.mibosmart.app.session.AuthenticateToken
import kotlin.test.Test
import kotlin.test.assertNotNull
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

    @Test
    fun theDeviceListUseCaseResolvesFromTheRealGraph() {
        val koin = koinApplication { modules(appModule(apiHost = "https://api.example.invalid")) }.koin

        assertNotNull(koin.get<ListDevices>())

        koin.close()
    }
}
