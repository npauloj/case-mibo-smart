package io.github.npauloj.mibosmart.app

import kotlin.time.Clock
import kotlin.time.Instant

/**
 * A clock that does not move, so an instant-dependent rule can be asserted exactly.
 *
 * The app injects `kotlin.time.Clock` wherever "now" is part of a rule — "última atualização há X"
 * (SPEC U3) and the session's countdown (SPEC S7) — and this is the one fake behind it. Where a rule
 * needs time to *pass*, the virtual time of `runTest` moves instead; the clock stays still and the
 * `delay` under test is what advances.
 */
internal class FixedClock(private val instant: Instant) : Clock {
    override fun now(): Instant = instant
}
