package io.github.npauloj.mibosmart.data.session

import kotlin.time.Instant

/** The session fixtures the data tests share. */
internal object SessionSamples {

    /** When the fixture session was accepted by the partner. */
    val IssuedAt: Instant = Instant.fromEpochMilliseconds(1_758_456_000_000)
}
