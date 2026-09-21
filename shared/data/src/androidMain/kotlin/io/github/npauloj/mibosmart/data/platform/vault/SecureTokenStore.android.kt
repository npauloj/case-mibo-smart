package io.github.npauloj.mibosmart.data.platform.vault

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.koin.core.scope.Scope

/**
 * The `Context` is read from the graph on first use, not while the graph is being built: nothing
 * should open the Keystore because a module was declared (ADR-008).
 */
internal actual fun Scope.secureTokenStore(): SecureTokenStore =
    KeystoreTokenStore(context = { get<Context>() })

/**
 * AES/GCM key generated inside `AndroidKeyStore`; only the ciphertext and its IV leave it, into a
 * private `SharedPreferences` file that `allowBackup="false"` keeps out of cloud backups (ADR-008).
 *
 * The key never leaves the hardware-backed store, so the preferences file is useless on its own —
 * which is what the manual evidence of this slice checks, reading the file back with `run-as`.
 */
private class KeystoreTokenStore(private val context: () -> Context) : SecureTokenStore {

    override fun read(): String? {
        val preferences = context().getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val ciphertext = preferences.getString(CIPHERTEXT, null) ?: return null
        val iv = preferences.getString(IV, null) ?: return null
        val key = existingKey() ?: return null
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv.decodeBase64()))
        return cipher.doFinal(ciphertext.decodeBase64()).decodeToString()
    }

    override fun write(token: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, existingKey() ?: generateKey())
        val ciphertext = cipher.doFinal(token.encodeToByteArray())
        context().getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putString(CIPHERTEXT, ciphertext.encodeBase64())
            .putString(IV, cipher.iv.encodeBase64())
            // `commit`, not `apply`: the token must be on disk before the write returns, because the
            // scenario this slice exists for is the process dying right after it.
            .commit()
    }

    /**
     * The ciphertext goes, and so does the key that could read it.
     *
     * Deleting the alias is what makes "Sair" irreversible even against a copy of the preferences
     * file taken beforehand (ADR-008); the next [write] generates a fresh key, so nothing downstream
     * has to know the alias was gone.
     */
    override fun clear() {
        context().getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .remove(CIPHERTEXT)
            .remove(IV)
            // `commit` for the same reason `write` uses it: the credential must be off disk before
            // the call returns, not whenever the background write happens to land.
            .commit()
        KeyStore.getInstance(KEYSTORE).apply { load(null) }.deleteEntry(ALIAS)
    }

    private fun existingKey(): SecretKey? {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        return (keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
    }

    private fun generateKey(): SecretKey =
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(
                    ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    // No biometric gate: the credential must survive a cold start unattended (ADR-008).
                    .setUserAuthenticationRequired(false)
                    .build(),
            )
        }.generateKey()

    private fun ByteArray.encodeBase64(): String = Base64.encodeToString(this, Base64.NO_WRAP)

    private fun String.decodeBase64(): ByteArray = Base64.decode(this, Base64.NO_WRAP)

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "mibosmart.session.token"
        const val PREFERENCES = "mibosmart.vault"
        const val CIPHERTEXT = "token"
        const val IV = "token.iv"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
    }
}
