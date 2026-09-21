package io.github.npauloj.mibosmart.app.session

/**
 * The shape a partner token has, and the only thing the app can check without spending a request
 * (SPEC S1.2).
 *
 * The account's budget is ~300 requests for the whole case (ADR-006) and the partner answers a typo
 * with the same "Erro desconhecido" it answers an expired token with, so a malformed token that
 * reaches the API costs one request *and* teaches the user nothing. Checking the documented format
 * here is free.
 *
 * Format per `docs/guides/token.md` §2: the public prefix `Ot_` followed by exactly 32 hexadecimal
 * characters. Upper-case hex is accepted — `A`–`F` are hexadecimal too, and refusing them would lock
 * a user out of a token the platform considers valid, which is worse than spending one request.
 */
object TokenFormat {

    /** Public by design: it is the documented format, not part of the secret. */
    const val PREFIX: String = "Ot_"

    /** How many hexadecimal characters follow [PREFIX]. */
    const val BODY_LENGTH: Int = 32

    /** The full length a well-formed token has, and what the on-screen counter counts towards. */
    val LENGTH: Int = PREFIX.length + BODY_LENGTH

    private val pattern = Regex("$PREFIX[0-9a-fA-F]{$BODY_LENGTH}")

    /**
     * Whether [raw] could be a token at all.
     *
     * Surrounding whitespace is trimmed first: clipboard content routinely carries a trailing
     * newline, and [TokenEntryViewModel] sends the trimmed value, so the check must judge the same
     * string the API would receive.
     */
    fun isValid(raw: String): Boolean = pattern.matches(raw.trim())

    /**
     * How many characters of [LENGTH] have been entered, counted the way a person counts them: one
     * per codepoint, so a pasted emoji counts once instead of twice.
     */
    fun characterCount(raw: String): Int = codepointStarts(raw.trim()).size
}

/**
 * The index in [text] where each codepoint starts.
 *
 * Kotlin indexes strings by UTF-16 unit, so a non-BMP character occupies two of them. Both the
 * counter and [TokenMask] have to work in codepoints instead: the counter so it does not double-count
 * what the user sees as one character, and the mask so it never cuts a surrogate pair in half.
 */
internal fun codepointStarts(text: String): IntArray {
    val starts = ArrayList<Int>(text.length)
    var index = 0
    while (index < text.length) {
        starts.add(index)
        val isPair = text[index].isHighSurrogate() &&
            index + 1 < text.length &&
            text[index + 1].isLowSurrogate()
        index += if (isPair) 2 else 1
    }
    return starts.toIntArray()
}
