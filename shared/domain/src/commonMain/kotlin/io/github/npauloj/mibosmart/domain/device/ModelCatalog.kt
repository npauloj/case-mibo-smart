package io.github.npauloj.mibosmart.domain.device

/**
 * The partner's own words for one of its codes — a device model code, a lock opening type (ADR-007).
 *
 * The app names everything it understands itself, in its own resources: `usuarioRemoto` reads
 * "Abertura remota" because the app decided so, not because a catalogue said it. What is left over
 * is the open set — SPEC L9 marks `tipo` `[ASSUMED]`, and a model code is whatever the partner
 * stamped on the hardware — and that is what a catalogue is for.
 *
 * It is a domain contract with no domain logic on purpose: on Android the implementation reads a
 * legacy Java SDK (`:legacy-catalog`), and on iOS there is no such library, so the platform that has
 * one is the only one that answers with more than [RawCodes] does.
 */
interface ModelCatalog {

    /**
     * What [code] is called, or [code] itself when the catalogue has no entry for it.
     *
     * Never null and never blank-for-a-code: a screen that asked about a code has something to
     * render either way, which is what keeps "no catalogue" from being a state of its own.
     */
    fun label(code: String): String
}

/**
 * The catalogue that knows nothing: every code is its own label.
 *
 * It is what the app runs on wherever the Java SDK does not exist — iOS — and it is also the default
 * a test or a preview gets, so the codepath under test is the one that ships (SPEC L9's raw `tipo`).
 */
object RawCodes : ModelCatalog {

    override fun label(code: String): String = code
}
