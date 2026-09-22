package io.github.npauloj.mibosmart.legacy.catalog;

/**
 * The catalogue has no entry for a code.
 *
 * Checked on purpose: it is what a partner SDK written for Java 6 does, and it is what the Kotlin
 * side has to answer for with {@code @Throws} and an explicit {@code catch} (ADR-007). A caller that
 * ignores it does not compile.
 */
public class UnknownCodeException extends Exception {

    private static final long serialVersionUID = 1L;

    private final String code;

    public UnknownCodeException(String code) {
        super("No catalogue entry for code '" + code + "'");
        this.code = code;
    }

    /** The code that was asked for, so the caller can fall back to it without keeping it around. */
    public String getCode() {
        return code;
    }
}
