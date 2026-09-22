package io.github.npauloj.mibosmart.legacy.catalog;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A partner-supplied catalogue of codes: what a model code or a lock opening type is called in
 * words a person reads.
 *
 * It stands in for the kind of SDK a partner ships and an app does not get to rewrite (ADR-007) —
 * so it is written the way such a library is: a private constructor behind a static factory, a
 * builder for the callers that need their own entries, overloaded lookups, and a *checked*
 * exception when a code is not in the table. The labels are Portuguese constants because they are
 * the partner's own words; everything the app writes itself stays in Compose resources.
 */
public final class PartnerCatalog {

    private final Map<String, String> entries;

    private PartnerCatalog(Map<String, String> entries) {
        this.entries = entries;
    }

    /**
     * The catalogue the partner ships with the SDK.
     *
     * It covers the lock opening types the app has no name of its own for (SPEC L9 lists `tipo` as
     * an open set) and the model codes of the families the account carries.
     */
    public static PartnerCatalog bundled() {
        return BUNDLED;
    }

    /** For a caller that has its own table — and for the tests that need an empty one. */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * The words for {@code code}.
     *
     * @throws UnknownCodeException when the catalogue has no entry — the caller decides what to
     *     show instead, because this library has nothing better to offer than the code itself.
     */
    public String describe(String code) throws UnknownCodeException {
        String label = code == null ? null : entries.get(code);
        if (label == null) {
            throw new UnknownCodeException(code);
        }
        return label;
    }

    /** The same lookup for a caller that already knows what it wants to show instead. */
    public String describe(String code, String fallback) {
        try {
            return describe(code);
        } catch (UnknownCodeException unknown) {
            return fallback;
        }
    }

    /** Whether asking for {@code code} would throw. */
    public boolean knows(String code) {
        return code != null && entries.containsKey(code);
    }

    /** The mutable half, kept apart from the catalogue itself so an instance cannot change. */
    public static final class Builder {

        private final Map<String, String> entries = new LinkedHashMap<String, String>();

        public Builder entry(String code, String label) {
            entries.put(code, label);
            return this;
        }

        public PartnerCatalog build() {
            return new PartnerCatalog(Collections.unmodifiableMap(new LinkedHashMap<String, String>(entries)));
        }
    }

    private static final PartnerCatalog BUNDLED = builder()
            // Lock opening types beyond the two the app models itself (`usuarioRemoto`, `interno`).
            .entry("biometria", "Abertura por biometria")
            .entry("senha", "Abertura por senha")
            .entry("cartao", "Abertura por cartão")
            .entry("chave", "Abertura com chave")
            .entry("app", "Abertura pelo aplicativo do parceiro")
            // Model codes, for the same reason: a code on screen is not a name.
            .entry("IOT-ZG2-IB", "Central Zigbee")
            .entry("iM7-FC", "Câmera Full Color")
            .build();
}
