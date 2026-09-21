package io.github.npauloj.mibosmart.data.session

import io.github.npauloj.mibosmart.data.platform.vault.SecureTokenStore

/**
 * The platform vault without a platform.
 *
 * The Keystore and the Keychain cannot run on the JVM host where these tests live, so the actuals are
 * proven by hand on a device (ADR-008 §Confirmation) and everything *around* them is proven here.
 * [failure] is the vault throwing — an invalidated key, a Keychain error — which is a state the real
 * actuals do reach.
 */
internal class FakeSecureTokenStore(private val failure: Throwable? = null) : SecureTokenStore {

    private var stored: String? = null

    override fun read(): String? {
        failure?.let { throw it }
        return stored
    }

    override fun write(token: String) {
        stored = token
    }
}
