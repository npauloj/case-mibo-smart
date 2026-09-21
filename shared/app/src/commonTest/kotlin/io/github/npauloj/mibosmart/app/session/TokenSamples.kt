package io.github.npauloj.mibosmart.app.session

import kotlin.time.Instant

/**
 * The token fixtures every session test shares.
 *
 * They are assembled from parts instead of written out: a well-formed token in a source file looks
 * exactly like a leaked one to the CI secret scan (ADR-008), and these are not credentials — they are
 * shapes.
 */
internal object TokenSamples {

    /**
     * `Ot_` followed by 32 alphanumeric characters — the real format (docs/guides/token.md §2).
     *
     * The body deliberately carries letters beyond `a`–`f`: a real token probed on 2026-09-21 does,
     * and the hexadecimal fixture this file shipped with could not have caught the bug ADR-012
     * records — every test passed while the app rejected real tokens.
     */
    val Valid: String = TokenFormat.PREFIX + "0a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p"

    /** A body made only of hexadecimal characters — still valid, since hex is a subset. */
    val ValidHexBody: String = TokenFormat.PREFIX + "0123456789abcdef".repeat(2)

    /** The same token as a clipboard that dropped the second half. */
    val Truncated: String = Valid.take(20)

    /**
     * The instant the session tests call "now".
     *
     * Fixed so the expiry boundary of SPEC S7 can be asserted to the second instead of raced against
     * the wall clock.
     */
    val Now: Instant = Instant.fromEpochMilliseconds(1_758_456_000_000)
}
