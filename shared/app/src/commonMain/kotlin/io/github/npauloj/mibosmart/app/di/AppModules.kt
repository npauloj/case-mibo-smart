package io.github.npauloj.mibosmart.app.di

import io.github.npauloj.mibosmart.app.AppCoroutineScope
import io.github.npauloj.mibosmart.app.AppViewModel
import io.github.npauloj.mibosmart.app.camera.LiveVideoSwitch
import io.github.npauloj.mibosmart.app.camera.cameraAppModule
import io.github.npauloj.mibosmart.app.devices.deviceAppModule
import io.github.npauloj.mibosmart.app.lock.LockWritesSwitch
import io.github.npauloj.mibosmart.app.lock.lockAppModule
import io.github.npauloj.mibosmart.app.session.PortalUrl
import io.github.npauloj.mibosmart.app.session.DebugBuild
import io.github.npauloj.mibosmart.app.session.sessionAppModule
import io.github.npauloj.mibosmart.data.di.dataModule
import kotlin.time.Clock
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

/**
 * Use cases and ViewModels; the partner implementation comes from `:shared:data`.
 * @param apiHost the partner host.
 * @param portalHost the partner's streaming host, which is a different address.
 * @param liveVideoEnabled the live-video kill switch (`smarthome.liveVideoEnabled`).
 * @param lockWritesEnabled the lock-writes kill switch (`smarthome.lockWritesEnabled`).
 * @param debugBuild whether this is a developer's build (`BuildConfig.DEBUG`).
 */
fun appModule(
    apiHost: String,
    portalHost: String,
    liveVideoEnabled: Boolean = true,
    lockWritesEnabled: Boolean = false,
    debugBuild: Boolean = false,
): Module = module {
    includes(dataModule(apiHost, portalHost))

    single<Clock> { Clock.System }

    single { AppCoroutineScope() }

    // Sem valor padrão de propósito: um default igual ao `apiHost` reproduz o defeito do ADR-025 em
    // silêncio, porque os dois hosts respondem 200 e nada no app os distingue.
    single { PortalUrl(portalHost) }

    single { LiveVideoSwitch(liveVideoEnabled) }

    single { LockWritesSwitch(lockWritesEnabled) }

    single { DebugBuild(debugBuild) }

    viewModelOf(::AppViewModel)

    includes(featureModules)
}

/** One entry per feature, alphabetical. */
private val featureModules: List<Module> = listOf(
    cameraAppModule,
    deviceAppModule,
    lockAppModule,
    sessionAppModule,
)

fun initKoin(
    apiHost: String,
    portalHost: String,
    liveVideoEnabled: Boolean = true,
    lockWritesEnabled: Boolean = false,
    debugBuild: Boolean = false,
    appDeclaration: KoinAppDeclaration = {},
) {
    startKoin {
        appDeclaration()
        modules(
            appModule(
                apiHost = apiHost,
                portalHost = portalHost,
                liveVideoEnabled = liveVideoEnabled,
                lockWritesEnabled = lockWritesEnabled,
                debugBuild = debugBuild,
            ),
        )
    }
}
