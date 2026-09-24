package io.github.npauloj.mibosmart.data.session

import io.github.npauloj.mibosmart.data.platform.vault.SecureTokenStore

/** The platform vault without a platform. */
internal class FakeSecureTokenStore(private val failure: Throwable? = null) : SecureTokenStore {

    private var stored: String? = null

    override fun read(): String? {
        failure?.let { throw it }
        return stored
    }

    override fun write(token: String) {
        stored = token
    }

    override fun clear() {
        stored = null
    }
}
