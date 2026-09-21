package io.github.npauloj.mibosmart.data.platform.vault

import org.koin.core.scope.Scope

/**
 * The platform's secure storage for the one credential this app holds (ADR-008).
 *
 * Deliberately narrow and deliberately not a domain type: it speaks `String`, not
 * [io.github.npauloj.mibosmart.domain.session.Token], so the vault knows nothing about sessions and
 * the domain knows nothing about Keystore or Keychain.
 * [io.github.npauloj.mibosmart.data.session.VaultSessionStore] is the one translation between them.
 *
 * Both functions are blocking platform I/O — a Keystore cipher, a Keychain query — which is why the
 * `SessionStore` above them suspends (ADR-010).
 */
internal interface SecureTokenStore {

    /**
     * The stored token, or `null` when nothing was ever written.
     *
     * Throws when the platform store itself fails — an invalidated Keystore key, a Keychain error.
     * The caller decides what that means; here it is never an empty result dressed up as a good one.
     */
    fun read(): String?

    /** Stores [token], replacing any value written before. */
    fun write(token: String)
}

/**
 * The platform's implementation, built from the Koin graph.
 *
 * The [Scope] receiver is what ADR-008 means by ":androidApp and iosApp only supply the Android
 * `Context` through Koin": the Android actual resolves it from the graph the app started with, and
 * `commonMain` never names an Android type. `expect/actual` in a package named `platform` — rule 8.
 */
internal expect fun Scope.secureTokenStore(): SecureTokenStore
