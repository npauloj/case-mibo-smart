package io.github.npauloj.mibosmart.data.platform.log

import io.ktor.client.plugins.logging.Logger

/** The Ktor [Logger] that actually reaches the platform's log stream. */
internal expect fun platformLogger(): Logger
