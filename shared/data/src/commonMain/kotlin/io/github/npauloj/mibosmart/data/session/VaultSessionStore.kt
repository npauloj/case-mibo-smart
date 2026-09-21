package io.github.npauloj.mibosmart.data.session

import io.github.npauloj.mibosmart.data.platform.vault.SecureTokenStore
import io.github.npauloj.mibosmart.domain.session.Session
import io.github.npauloj.mibosmart.domain.session.SessionStore
import io.github.npauloj.mibosmart.domain.session.Token
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Instant

/**
 * The session in the platform's vault — Keystore on Android, Keychain on iOS (SPEC S2, second half;
 * ADR-008).
 *
 * `issuedAt` travels inside the one string the vault already holds instead of as a second entry.
 * ADR-008 asks for `(token, issuedAt)` to be stored **atomically**, and one entry is the only shape
 * that cannot half-fail; it also leaves the two hand-verified platform actuals (ADR-008 §Confirmation,
 * ADR-013) untouched, so widening the session contract costs no new device round of manual proof.
 *
 * The encoding is `<epochMillis>:<token>`. Splitting at the **first** separator is what makes it safe
 * for any token value, not an assumption about the token's alphabet.
 */
internal class VaultSessionStore(private val vault: SecureTokenStore) : SessionStore {

    /**
     * A vault that fails is a session the app does not have.
     *
     * The platform store throws for reasons the user cannot act on — a Keystore key invalidated by a
     * screen-lock change, a Keychain error — and the only caller of this function decides where to
     * route from its answer. Crashing on startup because a cipher failed would be the worst of the
     * three possible outcomes; asking for the token again is the recoverable one.
     *
     * A stored value that does not decode is treated the same way, which is also what a token written
     * by a build from before this contract looks like: no `issuedAt` means no expiry policy, and a
     * session the app cannot reason about is worse than one more paste (SPEC S7).
     */
    override suspend fun read(): Session? =
        try {
            vault.read()?.let(::decode)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            null
        }

    /**
     * A failed write is not swallowed: `AuthenticateToken` already turns it into a named failure on
     * the token screen, which beats telling the user the session was saved when it was not.
     */
    override suspend fun write(token: Token, issuedAt: Instant) {
        vault.write("${issuedAt.toEpochMilliseconds()}$SEPARATOR${token.value}")
    }

    private fun decode(stored: String): Session? {
        val separator = stored.indexOf(SEPARATOR)
        if (separator <= 0) return null
        val issuedAt = stored.substring(0, separator).toLongOrNull() ?: return null
        val token = stored.substring(separator + 1)
        if (token.isEmpty()) return null

        return Session(Token(token), Instant.fromEpochMilliseconds(issuedAt))
    }

    private companion object {
        const val SEPARATOR = ':'
    }
}
