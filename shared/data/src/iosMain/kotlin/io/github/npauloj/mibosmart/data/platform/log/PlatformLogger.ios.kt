package io.github.npauloj.mibosmart.data.platform.log

import io.ktor.client.plugins.logging.Logger

/**
 * `println` on Kotlin/Native reaches the Xcode console, which is where an iOS developer already looks.
 *
 * `NSLog` would add the Foundation import for a line prefix nobody needs here; if the iOS slices ever
 * want unified-logging categories, that is the moment to change this one function.
 */
internal actual fun platformLogger(): Logger = object : Logger {
    override fun log(message: String) {
        println("[MiboSmartHttp] $message")
    }
}
