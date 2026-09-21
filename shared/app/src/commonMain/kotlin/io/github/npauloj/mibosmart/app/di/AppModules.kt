package io.github.npauloj.mibosmart.app.di

import io.github.npauloj.mibosmart.app.AppCoroutineScope
import io.github.npauloj.mibosmart.app.AppViewModel
import io.github.npauloj.mibosmart.app.camera.LiveVideoSwitch
import io.github.npauloj.mibosmart.app.camera.cameraAppModule
import io.github.npauloj.mibosmart.app.devices.deviceAppModule
import io.github.npauloj.mibosmart.app.lock.LockWritesSwitch
import io.github.npauloj.mibosmart.app.lock.lockAppModule
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
 *
 * **This file holds what belongs to no single feature and nothing else** — a feature's use cases and
 * ViewModels live in its own `<feature>AppModule.kt`, listed in [featureModules] (ADR-014).
 *
 * @param apiHost the partner host. It is configured per machine (`local.properties` → `BuildConfig`)
 *   and never versioned (ADR-008), so it can only arrive from the platform entry point.
 * @param liveVideoEnabled the live-video kill switch (`smarthome.liveVideoEnabled`). It arrives the
 *   same way and for the same reason: off, the app can be run on the shared account without opening
 *   a streaming session (SPEC V1, ADR-006).
 * @param lockWritesEnabled the lock-writes kill switch (`smarthome.lockWritesEnabled`). Same route,
 *   and it defaults to **off**: a lock write ends in a real building, so it is opted into rather than
 *   out of (SPEC L2, L7).
 * @param debugBuild whether this is a developer's build (`BuildConfig.DEBUG`). It gates the request
 *   counter on the account screen and nothing else (ADR-006). It defaults to off, which is the safe
 *   direction: a delivered build that forgot to say so shows one line less, never one more.
 */
fun appModule(
    apiHost: String,
    liveVideoEnabled: Boolean = true,
    lockWritesEnabled: Boolean = false,
    debugBuild: Boolean = false,
): Module = module {
    includes(dataModule(apiHost))

    // The one clock of the app: "última atualização há X" is read against it (SPEC U3), and a test
    // that has to assert those words needs to choose what "now" is. It belongs to no single feature.
    single<Clock> { Clock.System }

    // Work that must outlive the screen that started it — closing a streaming session, today
    // (SPEC V8). It belongs to no single feature either.
    single { AppCoroutineScope() }

    // Configured at the entry point like the host, and read by the camera feature alone.
    single { LiveVideoSwitch(liveVideoEnabled) }

    // The same, for the feature whose calls reach hardware: read by the lock feature alone.
    single { LockWritesSwitch(lockWritesEnabled) }

    // Also configured at the entry point, and read by the account screen alone (ADR-006).
    single { DebugBuild(debugBuild) }

    // Routing between features, so it belongs to none of them.
    viewModelOf(::AppViewModel)

    includes(featureModules)
}

/**
 * One entry per feature, alphabetical.
 *
 * A slice that adds a feature appends its module here and creates the file it names. That single line
 * is the only shared edit left: keep the list one-per-line and sorted, so a merge is always "keep
 * both", never a judgement call.
 */
private val featureModules: List<Module> = listOf(
    cameraAppModule,
    deviceAppModule,
    lockAppModule,
    sessionAppModule,
)

fun initKoin(
    apiHost: String,
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
                liveVideoEnabled = liveVideoEnabled,
                lockWritesEnabled = lockWritesEnabled,
                debugBuild = debugBuild,
            ),
        )
    }
}
