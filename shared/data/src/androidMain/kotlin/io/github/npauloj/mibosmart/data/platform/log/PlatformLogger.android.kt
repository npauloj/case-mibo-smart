package io.github.npauloj.mibosmart.data.platform.log

import android.util.Log
import io.ktor.client.plugins.logging.Logger

/** Ktor's lines go to logcat under one tag, so `adb logcat -s MiboSmartHttp` shows the HTTP traffic alone. */
internal actual fun platformLogger(): Logger = object : Logger {
    override fun log(message: String) {
        Log.d(TAG, message)
    }
}

private const val TAG = "MiboSmartHttp"
