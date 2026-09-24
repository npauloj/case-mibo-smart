package io.github.npauloj.mibosmart.app.session

/**
 * The shape a partner token has, and the only thing the app can check without spending a
 * request (SPEC S1.2).
 */
object TokenFormat {

    /** Public by design: it is the documented format, not part of the secret. */
    const val PREFIX: String = "Ot_"

    /** How many alphanumeric characters follow [PREFIX]. */
    const val BODY_LENGTH: Int = 32

    /** The full length a well-formed token has, and what the on-screen counter counts towards. */
    val LENGTH: Int = PREFIX.length + BODY_LENGTH

    /** The character class the body may use. */
    const val BODY_CLASS: String = "[0-9A-Za-z]"

    private val pattern = Regex("$PREFIX$BODY_CLASS{$BODY_LENGTH}")

    /** Whether [raw] could be a token at all. */
    fun isValid(raw: String): Boolean = pattern.matches(raw.trim())

    /**
     * How many characters of [LENGTH] have been entered, counted the way a person counts them:
     * one per codepoint, so a pasted emoji counts once instead of twice.
     */
    fun characterCount(raw: String): Int = codepointStarts(raw.trim()).size
}

/** The index in [text] where each codepoint starts. */
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
