package io.github.npauloj.mibosmart

import android.app.Application
import io.github.npauloj.mibosmart.app.di.initKoin
import org.koin.android.ext.koin.androidContext

class MiboSmartApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin(apiHost = BuildConfig.SMARTHOME_API_HOST) {
            androidContext(this@MiboSmartApplication)
        }
    }
}
