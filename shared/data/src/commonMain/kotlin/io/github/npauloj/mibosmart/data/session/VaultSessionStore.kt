package io.github.npauloj.mibosmart.data.session

import io.github.npauloj.mibosmart.data.platform.vault.SecureTokenStore
import io.github.npauloj.mibosmart.domain.session.SessionStore
import io.github.npauloj.mibosmart.domain.session.Token
import kotlin.coroutines.cancellation.CancellationException

/**
 * The session's credential in the platform's vault — Keystore on Android, Keychain on iOS (SPEC S2,
 * second half; ADR-008).
 *
 * Same interface and same behaviour as [InMemorySessionStore]: this slice changes **where** the token
 * lives, not what the app does with it. Reverting the one binding line in `dataModule` restores the
 * in-memory store.
 *
 * Widening to ADR-008's `write(token, issuedAt)` and `clear()` belongs to the slices that have a
 * caller for them — the expiry guard and logout (ADR-010).
 */
internal class VaultSessionStore(private val vault: SecureTokenStore) : SessionStore {

    /**
     * A vault that fails is a session the app does not have.
     *
     * The platform store throws for reasons the user cannot act on — a Keystore key invalidated by a
     * screen-lock change, a Keychain error — and the only caller of this function decides where to
     * route from its answer. Crashing on startup because a cipher failed would be the worst of the
     * three possible outcomes; asking for the token again is the recoverable one.
     */
    override suspend fun read(): Token? =
        try {
            vault.read()?.let(::Token)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            null
        }

    /**
     * A failed write is not swallowed: `AuthenticateToken` already turns it into a named failure on
     * the token screen, which beats telling the user the session was saved when it was not.
     */
    override suspend fun write(token: Token) {
        vault.write(token.value)
    }
}
