package io.github.npauloj.mibosmart.app.session

import kotlin.time.Instant

/** The token fixtures every session test shares. */
internal object TokenSamples {

    /** `Ot_` followed by 32 alphanumeric characters — the real format (docs/guides/token.md §2). */
    val Valid: String = TokenFormat.PREFIX + "0a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p"

    /** A body made only of hexadecimal characters — still valid, since hex is a subset. */
    val ValidHexBody: String = TokenFormat.PREFIX + "0123456789abcdef".repeat(2)

    /** The same token as a clipboard that dropped the second half. */
    val Truncated: String = Valid.take(20)

    /** The instant the session tests call "now". */
    val Now: Instant = Instant.fromEpochMilliseconds(1_758_456_000_000)
}
