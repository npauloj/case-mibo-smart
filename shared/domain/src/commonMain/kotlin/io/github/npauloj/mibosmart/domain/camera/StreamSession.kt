package io.github.npauloj.mibosmart.domain.camera

import io.github.npauloj.mibosmart.domain.device.DeviceId

/**
 * One live stream the partner opened for one camera (`docs/api-contract.md` §6).
 *
 * @property id what `encerrar-sessao` needs to give the quota back — the single most important field
 *   here, because a session nobody closes keeps spending the account's 2 GB (SPEC V8).
 * @property url the fragmented-MP4 stream. It expires **15 seconds** after the partner answers, which
 *   is why the use case publishes this value before it suspends again (SPEC V2).
 * @property monitorUrl the partner's own player page, used by the web fallback of V-02 and by the iOS
 *   player of V-01b. Null when the partner does not send one.
 * @property quotaGb what the partner says this session is allowed to spend, when it says so.
 */
data class StreamSession(
    val id: String,
    val url: String,
    val monitorUrl: String? = null,
    val quotaGb: Double? = null,
)

/**
 * The partner-side half of live video: the domain states the intent, `:shared:data` knows the wire,
 * the credential and where a cached answer comes from (ADR-004).
 *
 * Nothing here mentions playback. A player is a UI surface, not a business rule, and keeping it out of
 * the domain is what lets Media3 and a WebView be two spellings of the same feature (ADR-005).
 *
 * Failures are the typed exceptions of `domain.error` rather than return values (ADR-002).
 */
interface StreamingRepository {

    /**
     * Whether the camera announces real-time streaming (`funcoes` contains `RTSV`, SPEC V1).
     *
     * At most one partner call per camera per install: the answer is cached, because `funcoes` costs a
     * request and a camera does not grow the capability between two taps (ADR-006).
     */
    suspend fun announcesLiveVideo(camera: DeviceId): Boolean

    /** Opens a session with the documented defaults and spends streaming quota (SPEC V2). */
    suspend fun openSession(camera: DeviceId): StreamSession

    /** Gives the quota back. Called on the way out, from a scope the screen cannot cancel (SPEC V8). */
    suspend fun closeSession(sessionId: String)
}
