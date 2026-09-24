package io.github.npauloj.mibosmart.app

import kotlin.time.Clock
import kotlin.time.Instant

/** A clock that does not move, so an instant-dependent rule can be asserted exactly. */
internal class FixedClock(private val instant: Instant) : Clock {
    override fun now(): Instant = instant
}
