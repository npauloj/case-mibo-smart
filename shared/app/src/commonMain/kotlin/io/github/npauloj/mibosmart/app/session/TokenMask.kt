package io.github.npauloj.mibosmart.app.session

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * How the token field renders what was typed: the public `Ot_` prefix and the last 4 characters
 * in clear, everything between them replaced by bullets (SPEC S1.1, within the limit S9 sets).
 */
object TokenMask : VisualTransformation {

    /** What a hidden character looks like: one bullet per codepoint, never per UTF-16 unit. */
    const val BULLET: Char = '•'

    /** The tail S9 permits on screen. */
    const val VISIBLE_SUFFIX: Int = 4

    /**
     * Below this length nothing is shown at all: the last 4 characters of a 5-character
     * fragment are most of it, which would leak what the mask exists to hide.
     */
    const val MIN_LENGTH_FOR_SUFFIX: Int = 8

    override fun filter(text: AnnotatedString): TransformedText = mask(text.text)

    /**
     * The transformation as a pure function of the text, so it can be asserted without a
     * composition.
     */
    fun mask(original: String): TransformedText {
        val starts = codepointStarts(original)
        val count = starts.size
        val short = count < MIN_LENGTH_FOR_SUFFIX
        val visibleHead = if (!short && original.startsWith(TokenFormat.PREFIX)) TokenFormat.PREFIX.length else 0
        val visibleTail = if (short) 0 else VISIBLE_SUFFIX

        val masked = StringBuilder(original.length)
        val originalToTransformed = IntArray(original.length + 1)
        val transformedToOriginal = ArrayList<Int>(original.length + 1)

        for (codepoint in 0 until count) {
            val start = starts[codepoint]
            val end = if (codepoint + 1 < count) starts[codepoint + 1] else original.length
            val isVisible = codepoint < visibleHead || codepoint >= count - visibleTail
            if (isVisible) {
                for (index in start until end) {
                    originalToTransformed[index] = masked.length + (index - start)
                    transformedToOriginal.add(index)
                }
                masked.append(original, start, end)
            } else {
                for (index in start until end) originalToTransformed[index] = masked.length
                transformedToOriginal.add(start)
                masked.append(BULLET)
            }
        }
        originalToTransformed[original.length] = masked.length
        transformedToOriginal.add(original.length)

        return TransformedText(
            text = AnnotatedString(masked.toString()),
            offsetMapping = TokenOffsetMapping(originalToTransformed, transformedToOriginal.toIntArray()),
        )
    }
}

/** The two index tables [TokenMask] filled in, read back with the offsets coerced into range. */
private class TokenOffsetMapping(
    private val originalToTransformed: IntArray,
    private val transformedToOriginal: IntArray,
) : OffsetMapping {

    override fun originalToTransformed(offset: Int): Int =
        originalToTransformed[offset.coerceIn(0, originalToTransformed.lastIndex)]

    override fun transformedToOriginal(offset: Int): Int =
        transformedToOriginal[offset.coerceIn(0, transformedToOriginal.lastIndex)]
}
