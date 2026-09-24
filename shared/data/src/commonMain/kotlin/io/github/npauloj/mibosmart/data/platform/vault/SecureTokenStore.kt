package io.github.npauloj.mibosmart.data.platform.vault

import org.koin.core.scope.Scope

/** The platform's secure storage for the one credential this app holds (ADR-008). */
internal interface SecureTokenStore {

    /** The stored token, or `null` when nothing was ever written. */
    fun read(): String?

    /** Stores [token], replacing any value written before. */
    fun write(token: String)

    /** Removes the stored token, so [read] answers `null` again (SPEC S8). */
    fun clear()
}

/** The platform's implementation, built from the Koin graph. */
internal expect fun Scope.secureTokenStore(): SecureTokenStore
