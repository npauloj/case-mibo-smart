package io.github.npauloj.mibosmart.domain.session

import io.github.npauloj.mibosmart.domain.error.SmartHomeException
import kotlinx.coroutines.flow.Flow

/**
 * A request the partner refused, carrying **the credential it was sent with** (SPEC S6).
 *
 * [sentWith] is the whole point of the type. The guard has to tell "the session the app is holding
 * was refused" from "a request that left before the session was replaced came back late", and the
 * only honest way is to compare the two credentials — never their timestamps, which say nothing
 * about which token a request in flight actually carried.
 */
data class TokenRefusal(val sentWith: Token, val reason: SessionEndReason)

/**
 * Why the user is back on the token screen, in the only two shapes there are (SPEC S6, U6).
 *
 * The split is not cosmetic: a 403 hands back the partner's own sentence, which is the one category
 * SPEC U6 allows quoting verbatim (ADR-012), while everything else gets the app's own wording.
 */
sealed interface SessionEndReason {

    /** A 401, or a 403 whose body carried nothing worth showing: the app supplies the sentence. */
    data object Expired : SessionEndReason

    /** A 403's `msg` — "Token expirado, por favor gere um novo token" — shown as-is (SPEC S3.1). */
    data class StatedByPartner(val message: String) : SessionEndReason
}

/**
 * A session the guard has just cleared: why, and where to come back to (SPEC U5).
 *
 * [returnTo] is a type parameter because "the screen the user was on" is the app's vocabulary, not the
 * domain's — the domain must not learn what a screen is (ADR-001, rule 1). It travels through the
 * guard untouched so the two halves of U5 stay one value: a caller cannot route back without also
 * having the reason to show, and cannot show the reason without knowing where to return.
 */
data class SessionEnded<out D>(val reason: SessionEndReason, val returnTo: D)

/**
 * Where the layer that sends requests announces a refusal, and where the guard listens (SPEC S6).
 *
 * A guard that had to be wired into every use case would be a guard with holes: any slice that adds a
 * partner call would have to remember it. One stream, fed at the single point every request passes
 * through, cannot be forgotten (ADR-018).
 */
interface RefusedRequests {

    /** Every refusal since the app started. The session guard is the only collector. */
    val refusals: Flow<TokenRefusal>

    /** Announces [refusal]; called by whoever sent the request, which is who knows the token. */
    fun report(refusal: TokenRefusal)
}

/**
 * What a refused request does to the stored session (SPEC S6, U5).
 *
 * The comparison is on token identity. Renewal (S-03) adds a credential rather than replacing one —
 * measured 2026-09-21, the previous token kept answering `200` — so a request that left with the old
 * token can still come back refused long after the vault holds a new one. Clearing on that refusal
 * would throw away a session that works, which is exactly the failure this class exists to prevent.
 */
class SessionGuard(private val sessionStore: SessionStore) {

    /**
     * Clears the session and says where to return, or answers `null` when the refusal is not about the
     * session the app is holding.
     *
     * @param returnTo the screen the user is on, handed back in [SessionEnded.returnTo] (SPEC U5).
     */
    suspend fun <D> onRefusal(refusal: TokenRefusal, returnTo: D): SessionEnded<D>? {
        val stored = sessionStore.read() ?: return null
        if (stored.token != refusal.sentWith) return null

        sessionStore.clear()
        return SessionEnded(refusal.reason, returnTo)
    }
}

/**
 * The failure as a refusal, or `null` when it says nothing about the session.
 *
 * No `else`: [SmartHomeException] is sealed, so a category added by a later slice stops compiling here
 * until someone states whether it ends the session. That is the whole of ADR-012's correction — the
 * two branches that must **not** clear anything are as deliberate as the two that must.
 */
fun SmartHomeException.asTokenRefusal(sentWith: Token): TokenRefusal? = when (this) {
    // 401: the partner does not recognise the credential. Nothing in the body is fit to quote.
    is SmartHomeException.TokenRejected -> TokenRefusal(sentWith, SessionEndReason.Expired)
    // 403 with the partner's envelope: the session ended, and it said so in words worth showing.
    is SmartHomeException.TokenExpired -> TokenRefusal(sentWith, expiryReason())
    // 403 with the gateway's `{"message"}`: an endpoint this account may not call, with a token that
    // is perfectly valid. Clearing here is the bug ADR-012 was amended to stop (SPEC S6, E1).
    is SmartHomeException.Forbidden -> null
    is SmartHomeException.DeviceNotFound,
    is SmartHomeException.QuotaExceeded,
    is SmartHomeException.Offline,
    is SmartHomeException.UnexpectedResponse,
    is SmartHomeException.ApiError,
    -> null
}

/** A blank `msg` is no message: the app's own sentence beats an empty line on screen (SPEC U6). */
private fun SmartHomeException.TokenExpired.expiryReason(): SessionEndReason =
    serverMessage?.takeIf { it.isNotBlank() }?.let(SessionEndReason::StatedByPartner)
        ?: SessionEndReason.Expired
