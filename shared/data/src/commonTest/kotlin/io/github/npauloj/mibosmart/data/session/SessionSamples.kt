package io.github.npauloj.mibosmart.data.session

import kotlin.time.Instant

/** The session fixtures the data tests share. */
internal object SessionSamples {

    /**
     * When the fixture session was accepted by the partner.
     *
     * A fixed instant rather than "now": nothing in `:shared:data` reads the expiry policy — that is
     * the app module's (SPEC S7) — so every test here only needs the value to be stable and to round
     * trip through the vault unchanged.
     */
    val IssuedAt: Instant = Instant.fromEpochMilliseconds(1_758_456_000_000)
}
