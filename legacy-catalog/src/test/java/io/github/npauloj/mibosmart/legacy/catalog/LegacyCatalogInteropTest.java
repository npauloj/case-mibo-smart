package io.github.npauloj.mibosmart.legacy.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The other direction of the interop (ADR-007): Java calling the Kotlin bridge, written in Java so
 * that the compiler — not a comment — is what proves the signatures are callable from it.
 *
 * Everything here would fail to compile if the Kotlin side dropped {@code @JvmStatic},
 * {@code @JvmOverloads}, {@code @JvmField} or {@code @Throws}: a companion function would become
 * {@code CatalogBridge.Companion.of(...)}, the one-argument lookup would not exist, the field would
 * become {@code getBUNDLED()} and the checked exception would vanish from the signature.
 */
class LegacyCatalogInteropTest {

    /** {@code @JvmField} — a Kotlin companion `val` reached as a plain static field. */
    @Test
    void bundledCatalogueIsReachableAsAStaticField() throws UnknownCodeException {
        assertEquals("Abertura por biometria", CatalogBridge.BUNDLED.strictLabel("biometria"));
    }

    /** {@code @JvmStatic} — a factory called as `CatalogBridge.of(...)`, with no `Companion` in between. */
    @Test
    void kotlinFactoryIsCalledAsAStaticMethod() {
        CatalogBridge bridge = CatalogBridge.of(
                PartnerCatalog.builder().entry("portao", "Abertura do portão").build());

        assertEquals("Abertura do portão", bridge.label("portao"));
    }

    /** {@code @JvmOverloads} — both arities exist for Java, which has no default arguments. */
    @Test
    void bothAritiesOfTheOverloadedLookupExist() {
        CatalogBridge bridge = CatalogBridge.BUNDLED;

        assertEquals("interno", bridge.label("interno"), "an unknown code falls back to itself");
        assertEquals("Outra abertura", bridge.label("interno", "Outra abertura"));
    }

    /** {@code @Throws} — the SDK's checked exception is still checked on the way out of Kotlin. */
    @Test
    void theCheckedExceptionSurvivesTheRoundTrip() {
        UnknownCodeException raised = assertThrows(
                UnknownCodeException.class,
                () -> CatalogBridge.BUNDLED.strictLabel("interno"));

        assertEquals("interno", raised.getCode());
    }

    /** The Java half on its own: a model code is in the same table as the opening types. */
    @Test
    void theBundledCatalogueAlsoNamesModelCodes() throws UnknownCodeException {
        PartnerCatalog catalog = PartnerCatalog.bundled();

        assertTrue(catalog.knows("IOT-ZG2-IB"));
        assertEquals("Central Zigbee", catalog.describe("IOT-ZG2-IB"));
        assertEquals("nao-catalogado", catalog.describe("nao-catalogado", "nao-catalogado"));
    }
}
