package io.github.npauloj.mibosmart.domain.session

import kotlin.test.Test
import kotlin.test.assertFalse

class TokenTest {

    /** SPEC S9 / ADR-008: whatever prints a token — a log line, a crash message — prints nothing. */
    @Test
    fun toStringHidesTheCredential() {
        val printed = Token("um-token-secreto").toString()

        assertFalse(printed.contains("um-token-secreto"), "the token leaked through toString: $printed")
    }
}
