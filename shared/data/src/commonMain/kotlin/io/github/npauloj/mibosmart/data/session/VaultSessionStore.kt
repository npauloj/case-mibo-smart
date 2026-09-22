package io.github.npauloj.mibosmart.data.session

import io.github.npauloj.mibosmart.data.platform.vault.SecureTokenStore
import io.github.npauloj.mibosmart.domain.session.Session
import io.github.npauloj.mibosmart.domain.session.SessionStore
import io.github.npauloj.mibosmart.domain.session.Token
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * The session in the platform's vault — Keystore on Android, Keychain on iOS (SPEC S2, second half;
 * ADR-008).
 *
 * `issuedAt` and the session's `lifetime` travel inside the one string the vault already holds instead
 * of as further entries. ADR-008 asks for the session to be stored **atomically**, and one entry is
 * the only shape that cannot half-fail; it also leaves the two hand-verified platform actuals
 * (ADR-008 §Confirmation, ADR-013) untouched, so widening the session contract costs no new device
 * round of manual proof (ADR-020).
 *
 * The encoding is `<epochMillis>:<lifetimeSeconds>:<token>`. Only the **first two** separators are
 * read, which is what makes it safe for any token value rather than an assumption about the token's
 * alphabet.
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
     * A stored value that does not decode is treated the same way, which is also what an entry written
     * by a build from before this contract looks like — a bare token, or a `<millis>:<token>` pair
     * without a lifetime. A session whose deadline the app cannot read is one it cannot reason about,
     * and that is worse than one more paste (SPEC S7).
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
    override suspend fun write(token: Token, issuedAt: Instant, lifetime: Duration) {
        vault.write(
            "${issuedAt.toEpochMilliseconds()}$SEPARATOR${lifetime.inWholeSeconds}$SEPARATOR${token.value}",
        )
    }

    /**
     * A failed clear is not swallowed either: the caller of "Sair" has to be able to tell the user
     * the credential is still on the device rather than pretend it is gone (SPEC S8, ADR-008).
     */
    override suspend fun clear() {
        vault.clear()
    }

    private fun decode(stored: String): Session? {
        val afterIssuedAt = stored.indexOf(SEPARATOR)
        if (afterIssuedAt <= 0) return null
        val issuedAt = stored.substring(0, afterIssuedAt).toLongOrNull() ?: return null

        val rest = stored.substring(afterIssuedAt + 1)
        val afterLifetime = rest.indexOf(SEPARATOR)
        if (afterLifetime <= 0) return null
        val lifetimeSeconds = rest.substring(0, afterLifetime).toLongOrNull() ?: return null

        val token = rest.substring(afterLifetime + 1)
        if (token.isEmpty()) return null

        return Session(Token(token), Instant.fromEpochMilliseconds(issuedAt), lifetimeSeconds.seconds)
    }

    private companion object {
        const val SEPARATOR = ':'
    }
}
