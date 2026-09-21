package io.github.npauloj.mibosmart.app.session

/**
 * The token fixtures every session test shares.
 *
 * They are assembled from parts instead of written out: a well-formed token in a source file looks
 * exactly like a leaked one to the CI secret scan (ADR-008), and these are not credentials — they are
 * shapes.
 */
internal object TokenSamples {

    /** `Ot_` followed by 32 hexadecimal characters — the documented format (docs/guides/token.md §2). */
    val Valid: String = TokenFormat.PREFIX + "0123456789abcdef".repeat(2)

    /** The same token as a clipboard that dropped the second half. */
    val Truncated: String = Valid.take(20)
}
