package io.github.npauloj.mibosmart.domain.session

import io.github.npauloj.mibosmart.domain.error.SmartHomeException
import kotlinx.coroutines.flow.Flow

/** A request the partner refused, carrying **the credential it was sent with** (SPEC S6). */
data class TokenRefusal(val sentWith: Token, val reason: SessionEndReason)

/** Why the user is back on the token screen, in the only two shapes there are (SPEC S6, U6). */
sealed interface SessionEndReason {

    /** A 401, or a 403 whose body carried nothing worth showing: the app supplies the sentence. */
    data object Expired : SessionEndReason

    /** A 403's `msg` — "Token expirado, por favor gere um novo token" — shown as-is (SPEC S3.1). */
    data class StatedByPartner(val message: String) : SessionEndReason
}

/** A session the guard has just cleared: why, and where to come back to (SPEC U5). */
data class SessionEnded<out D>(val reason: SessionEndReason, val returnTo: D)

/**
 * Where the layer that sends requests announces a refusal, and where the guard listens (SPEC
 * S6).
 */
interface RefusedRequests {

    /** Every refusal since the app started. The session guard is the only collector. */
    val refusals: Flow<TokenRefusal>

    /** Announces [refusal]; called by whoever sent the request, which is who knows the token. */
    fun report(refusal: TokenRefusal)
}

/** What a refused request does to the stored session (SPEC S6, U5). */
class SessionGuard(private val sessionStore: SessionStore) {

    /**
     * Clears the session and says where to return, or answers `null` when the refusal is not
     * about the session the app is holding.
     * @param returnTo the screen the user is on, handed back in [SessionEnded.returnTo] (SPEC
     * U5).
     */
    suspend fun <D> onRefusal(refusal: TokenRefusal, returnTo: D): SessionEnded<D>? {
        val stored = sessionStore.read() ?: return null
        if (stored.token != refusal.sentWith) return null

        sessionStore.clear()
        return SessionEnded(refusal.reason, returnTo)
    }
}

/** The failure as a refusal, or `null` when it says nothing about the session. */
fun SmartHomeException.asTokenRefusal(sentWith: Token): TokenRefusal? = when (this) {
    is SmartHomeException.TokenRejected -> TokenRefusal(sentWith, SessionEndReason.Expired)
    is SmartHomeException.TokenExpired -> TokenRefusal(sentWith, expiryReason())
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
