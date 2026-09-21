package io.github.npauloj.mibosmart.app.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * SPEC S1.1 and the entry-field half of S9: the field shows enough to check a paste, and nothing more.
 */
class TokenMaskTest {

    @Test
    fun showsPrefixAndLastFourOnly() {
        val token = TokenSamples.Valid
        val masked = TokenMask.mask(token).text.text

        assertEquals(token.length, masked.length, "one bullet per hidden character keeps the counter honest")
        assertTrue(masked.startsWith(TokenFormat.PREFIX), "the prefix is the documented format, not a secret")
        assertEquals(token.takeLast(TokenMask.VISIBLE_SUFFIX), masked.takeLast(TokenMask.VISIBLE_SUFFIX))

        val hidden = TokenFormat.BODY_LENGTH - TokenMask.VISIBLE_SUFFIX
        val middle = masked.substring(TokenFormat.PREFIX.length, masked.length - TokenMask.VISIBLE_SUFFIX)
        assertEquals(TokenMask.BULLET.toString().repeat(hidden), middle, "the middle never renders")
        assertFalse(
            masked.contains(token.substring(TokenFormat.PREFIX.length, token.length - TokenMask.VISIBLE_SUFFIX)),
            "no part of the secret survives the transformation",
        )
    }

    @Test
    fun shortInputIsFullyMasked() {
        val fragment = "Ot_12"
        val masked = TokenMask.mask(fragment).text.text

        assertEquals(TokenMask.BULLET.toString().repeat(fragment.length), masked)
        assertFalse(masked.contains(TokenFormat.PREFIX), "the last 4 of a 5-character fragment would be most of it")
    }

    @Test
    fun offsetMappingRoundTrips() {
        val token = TokenSamples.Valid
        val mapping = TokenMask.mask(token).offsetMapping

        for (offset in 0..token.length) {
            assertEquals(
                offset,
                mapping.transformedToOriginal(mapping.originalToTransformed(offset)),
                "the cursor must land where the user put it, at offset $offset",
            )
        }
        assertEquals(token.length, mapping.originalToTransformed(token.length), "including past the last character")
    }

    @Test
    fun surrogatePairIsOneBullet() {
        // A pasted emoji is two UTF-16 units; masking them separately would split the pair and give
        // Compose an offset mapping it rejects.
        val pasted = "Ot_😀abcdefgh"
        val transformed = TokenMask.mask(pasted)

        assertEquals(pasted.length - 1, transformed.text.text.length, "the pair collapses into a single bullet")
        for (offset in 0..pasted.length) {
            val mapped = transformed.offsetMapping.originalToTransformed(offset)
            assertTrue(mapped in 0..transformed.text.text.length, "offset $offset maps inside the masked text")
            assertTrue(
                transformed.offsetMapping.transformedToOriginal(mapped) in 0..pasted.length,
                "and back inside the original",
            )
        }
    }
}
