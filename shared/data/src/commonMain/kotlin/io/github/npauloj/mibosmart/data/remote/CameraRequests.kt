package io.github.npauloj.mibosmart.data.remote

import io.github.npauloj.mibosmart.domain.device.DeviceId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The three camera and streaming calls as the partner wants them (`docs/api-contract.md` §3 and §6).
 *
 * The defaults below are the contract's, not Kotlin's: they are written into the request object
 * instead of being declared as property defaults because `kotlinx.serialization` omits defaults from
 * the encoded body, and SPEC V2 is an assertion about the exact bytes sent.
 */
internal object CameraRequests {

    /** The substream: lower bandwidth, which is what a phone over mobile data can actually keep up with. */
    private const val STREAM_ID = 1

    /** Half a gigabyte per session. Only what is really consumed is debited (§6). */
    private const val STREAM_GB = 0.5

    /** Lens/channel; every camera in the account is single-lens (§6). */
    private const val VIDEO_CHANNEL = 0

    fun functions(camera: DeviceId): CameraNamespaceRequestDto = CameraNamespaceRequestDto(camera.value)

    fun createStream(camera: DeviceId): CreateStreamRequestDto = CreateStreamRequestDto(
        namespace = camera.value,
        streamGb = STREAM_GB,
        videoChannel = VIDEO_CHANNEL,
        streamId = STREAM_ID,
    )

    fun endSession(sessionId: String): EndSessionRequestDto = EndSessionRequestDto(sessionId)
}

/** `funcoes` takes the plain serial and nothing else. */
@Serializable
internal data class CameraNamespaceRequestDto(@SerialName("ns") val namespace: String)

/** `funcoes` → `{ "funcoes": "…,RTSV2,RTSV1,…" }`, one comma-separated string (§3). */
@Serializable
internal data class CameraFunctionsDto(@SerialName("funcoes") val functions: String)

/** `criar-fluxo-video` request; the field order here is the order asserted on the wire (SPEC V2). */
@Serializable
internal data class CreateStreamRequestDto(
    @SerialName("ns") val namespace: String,
    @SerialName("stream_gb") val streamGb: Double,
    @SerialName("canalVideo") val videoChannel: Int,
    @SerialName("streamId") val streamId: Int,
)

/** `criar-fluxo-video` → the session, the fMP4 url and the partner's own player page (§6). */
@Serializable
internal data class StreamSessionDto(
    @SerialName("session_id") val sessionId: String,
    @SerialName("url") val url: String,
    @SerialName("monitor_url") val monitorUrl: String? = null,
    @SerialName("quota_gb") val quotaGb: Double? = null,
)

/** `encerrar-sessao` takes the session and gives the quota back (§6). */
@Serializable
internal data class EndSessionRequestDto(@SerialName("session_id") val sessionId: String)
