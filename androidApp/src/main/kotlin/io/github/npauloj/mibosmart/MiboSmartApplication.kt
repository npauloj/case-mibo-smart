package io.github.npauloj.mibosmart

import android.app.Application
import io.github.npauloj.mibosmart.app.di.initKoin
import org.koin.android.ext.koin.androidContext

class MiboSmartApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin(
            apiHost = BuildConfig.SMARTHOME_API_HOST,
            liveVideoEnabled = BuildConfig.SMARTHOME_LIVE_VIDEO_ENABLED,
            lockWritesEnabled = BuildConfig.SMARTHOME_LOCK_WRITES_ENABLED,
            // The request counter of ADR-006 is shown on the account screen in a debug build only.
            debugBuild = BuildConfig.DEBUG,
        ) {
            androidContext(this@MiboSmartApplication)
        }
    }
}
