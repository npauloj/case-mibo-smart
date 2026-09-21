package io.github.npauloj.mibosmart.data.platform.log

import io.ktor.client.plugins.logging.Logger

/**
 * The Ktor [Logger] that actually reaches the platform's log stream.
 *
 * Ktor's `Logger.DEFAULT` writes where nothing on Android reads: with it installed, `adb logcat`
 * showed **zero** lines from this app while it was talking to the partner API — which is how a wrong
 * error mapping survived to a real device (ADR-012). Debugging three API slices without request logs
 * is debugging blind.
 *
 * What is logged is still governed by [io.github.npauloj.mibosmart.data.remote.installSanitizedLogging]:
 * `LogLevel.HEADERS` with `Authorization` redacted, never `ALL` (ADR-008, SPEC S9). This declaration
 * only decides *where* the lines go.
 *
 * `expect/actual` lives in a package named `platform` — architecture rule 8 (ADR-001, ADR-009).
 */
internal expect fun platformLogger(): Logger
