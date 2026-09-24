package io.github.npauloj.mibosmart.app.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** SPEC S1.2: what the app can tell about a token before spending one of ~300 requests (ADR-006). */
class TokenFormatTest {

    @Test
    fun acceptsTheDocumentedFormat() {
        assertTrue(TokenFormat.isValid(TokenSamples.Valid), "Ot_ plus 32 alphanumeric is the real format")
        assertEquals(35, TokenFormat.LENGTH)
        assertTrue(
            TokenFormat.isValid(TokenSamples.ValidHexBody),
            "a body that happens to be all hexadecimal is still a body",
        )
        assertTrue(
            TokenFormat.isValid(TokenFormat.PREFIX + "ABCDEF0123456789abcdef0123456789"),
            "only one real token has been observed; refusing a case would lock a user out (ADR-012)",
        )
    }

    /**
     * The regression this file exists for: the first version of [TokenFormat] required
     * hexadecimal, and a real token carries letters beyond `a`-`f`, so the app disabled
     * "Validar" for a token the platform accepts (ADR-012).
     */
    @Test
    fun acceptsLettersBeyondHexadecimal() {
        assertTrue(
            TokenFormat.isValid(TokenFormat.PREFIX + "ghijklmnopqrstuvwxyz012345678901"),
            "the body is alphanumeric, not hexadecimal",
        )
    }

    @Test
    fun rejectsTruncatedPaste() {
        assertFalse(TokenFormat.isValid(TokenSamples.Truncated), "half a token is not a token")
        assertFalse(TokenFormat.isValid(TokenSamples.Valid + "0"), "one character too many either")
        assertFalse(TokenFormat.isValid(""), "an empty field has nothing to validate")
        assertFalse(TokenFormat.isValid("   "), "and neither does a blank one")
    }

    @Test
    fun rejectsNonHexCharacters() {
        assertFalse(
            TokenFormat.isValid(TokenSamples.Valid.dropLast(1) + "-"),
            "the body is alphanumeric: punctuation is out, however right the length is",
        )
        assertFalse(
            TokenFormat.isValid(TokenSamples.Valid.dropLast(1) + "ç"),
            "and so is anything outside ASCII",
        )
        assertFalse(
            TokenFormat.isValid("ot_" + TokenSamples.Valid.drop(3)),
            "the prefix is Ot_, and the platform's own docs spell it with a capital O",
        )
    }

    @Test
    fun trimsSurroundingWhitespace() {
        assertTrue(
            TokenFormat.isValid("  ${TokenSamples.Valid}\n"),
            "clipboard content routinely carries a trailing newline; the app sends the trimmed value",
        )
        assertFalse(
            TokenFormat.isValid(TokenSamples.Valid.replaceRange(10, 11, " ")),
            "whitespace inside is not surrounding whitespace",
        )
    }

    @Test
    fun countsCharactersForTheProgressCounter() {
        assertEquals(0, TokenFormat.characterCount(""), "the counter starts at zero")
        assertEquals(0, TokenFormat.characterCount("  \n"), "blank input has counted nothing")
        assertEquals(20, TokenFormat.characterCount(TokenSamples.Truncated))
        assertEquals(35, TokenFormat.characterCount("${TokenSamples.Valid}\n"), "counts what will be sent")
        assertEquals(
            2,
            TokenFormat.characterCount("a😀"),
            "an emoji is one character to the person counting, two UTF-16 units to Kotlin",
        )
    }
}
