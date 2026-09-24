package io.github.npauloj.mibosmart.domain.camera

import io.github.npauloj.mibosmart.domain.device.DeviceId

/**
 * One live stream the partner opened for one camera (`docs/api-contract.md` §6).
 * @property id what `encerrar-sessao` needs to give the quota back — the single most important
 * field here, because a session nobody closes keeps spending the account's 2 GB (SPEC V8).
 * @property url the fragmented-MP4 stream.
 * @property monitorUrl the partner's own player page, used by the web fallback of V-02 and by
 * the iOS player of V-01b.
 * @property quotaGb what the partner says this session is allowed to spend, when it says so.
 */
data class StreamSession(
    val id: String,
    val url: String,
    val monitorUrl: String? = null,
    val quotaGb: Double? = null,
)

/**
 * The partner-side half of live video: the domain states the intent, `:shared:data` knows the
 * wire, the credential and where a cached answer comes from (ADR-004).
 */
interface StreamingRepository {

    /** Whether the camera announces real-time streaming (`funcoes` contains `RTSV`, SPEC V1). */
    suspend fun announcesLiveVideo(camera: DeviceId): Boolean

    /** Opens a session with the documented defaults and spends streaming quota (SPEC V2). */
    suspend fun openSession(camera: DeviceId): StreamSession

    /** Gives the quota back. Called on the way out, from a scope the screen cannot cancel (SPEC V8). */
    suspend fun closeSession(sessionId: String)
}
