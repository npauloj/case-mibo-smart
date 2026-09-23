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

    /**
     * The **substream** (1), not the main profile (0): lower bandwidth is what a phone on mobile
     * data keeps up with, and the account is billed for what it really consumes (§6).
     *
     * Measured 2026-09-23: the partner ignores this field. Main and substream return the same SDP,
     * the same codec and the same bitrate, on both hosts. It is sent because the contract asks for
     * it, not because it selects anything.
     */
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
    // Required, and it has to stay required: it is the only handle `encerrar-sessao` accepts, and
    // an optional one would let the app open sessions it cannot close. Measured 2026-09-23, the
    // api host answers this call with a url and nothing else, so the field was briefly made
    // nullable to get past a parse failure — which quietly disabled SPEC V8's teardown and left 27
    // sessions open on a shared account. The parse failure was the right alarm; the wrong host was
    // the fault (see `SmartHomeApi.streamingBaseUrl`).
    @SerialName("session_id") val sessionId: String,
    @SerialName("url") val url: String,
    @SerialName("monitor_url") val monitorUrl: String? = null,
    @SerialName("quota_gb") val quotaGb: Double? = null,
)

/** `encerrar-sessao` takes the session and gives the quota back (§6). */
@Serializable
internal data class EndSessionRequestDto(@SerialName("session_id") val sessionId: String)
