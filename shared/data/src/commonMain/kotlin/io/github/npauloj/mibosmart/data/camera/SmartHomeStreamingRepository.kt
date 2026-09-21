package io.github.npauloj.mibosmart.data.camera

import io.github.npauloj.mibosmart.data.local.CapabilityCache
import io.github.npauloj.mibosmart.data.remote.CameraFunctionsDto
import io.github.npauloj.mibosmart.data.remote.CameraRequests
import io.github.npauloj.mibosmart.data.remote.SmartHomeApi
import io.github.npauloj.mibosmart.data.remote.StreamSessionDto
import io.github.npauloj.mibosmart.domain.camera.StreamSession
import io.github.npauloj.mibosmart.domain.camera.StreamingRepository
import io.github.npauloj.mibosmart.domain.device.DeviceId
import io.github.npauloj.mibosmart.domain.error.SmartHomeException
import io.github.npauloj.mibosmart.domain.session.SessionStore
import io.github.npauloj.mibosmart.domain.session.Token
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * The three streaming calls against the partner API (SPEC V1, V2, V8).
 *
 * The session is read here rather than passed in from above, exactly as the lock repository does it:
 * which credential a request carries is a transport concern (ADR-004).
 */
internal class SmartHomeStreamingRepository(
    private val api: SmartHomeApi,
    private val sessionStore: SessionStore,
    private val json: Json,
    private val capabilities: CapabilityCache,
) : StreamingRepository {

    /**
     * SPEC V1: the cache is consulted first, so the second visit to a camera costs nothing.
     *
     * A camera that answers `funcoes` without `RTSV` is cached too — "no" costs the same request as
     * "yes", and re-asking it on every tap is the waste ADR-006 exists to stop.
     */
    override suspend fun announcesLiveVideo(camera: DeviceId): Boolean {
        capabilities.read(camera.value)?.let { return it.announcesLiveVideo() }
        val payload = api.readDeviceFunctions(token(), CameraRequests.functions(camera))
        val functions = decode(CameraFunctionsDto.serializer(), payload).functions
        capabilities.write(camera.value, functions)
        return functions.announcesLiveVideo()
    }

    override suspend fun openSession(camera: DeviceId): StreamSession = try {
        decode(
            StreamSessionDto.serializer(),
            api.createVideoStream(token(), CameraRequests.createStream(camera)),
        ).toSession()
    } catch (named: SmartHomeException.ApiError) {
        // HTTP 402 is already classified by the envelope reader. The contract also allows the partner
        // to answer 200 with the outcome in the body (`docs/api-contract.md` §1, §8 open question 5),
        // and quota is the one failure whose meaning a screen cannot guess: no retry, no fallback.
        if (named.serverMessage.announcesExhaustedQuota()) throw SmartHomeException.QuotaExceeded()
        throw named
    }

    override suspend fun closeSession(sessionId: String) {
        api.endStreamSession(token(), CameraRequests.endSession(sessionId))
    }

    /**
     * The session's credential — a video screen reached without one is, to everything above, the same
     * thing as a refused token: there is nothing to retry but a new one (SPEC S6).
     */
    private suspend fun token(): Token =
        sessionStore.read()?.token ?: throw SmartHomeException.TokenRejected()

    private fun StreamSessionDto.toSession(): StreamSession =
        StreamSession(id = sessionId, url = url, monitorUrl = monitorUrl, quotaGb = quotaGb)

    /** The partner's `data` payload as [T], or [SmartHomeException.UnexpectedResponse] (SPEC E3). */
    private fun <T> decode(strategy: DeserializationStrategy<T>, payload: JsonElement): T = try {
        json.decodeFromJsonElement(strategy, payload)
    } catch (malformed: SerializationException) {
        throw SmartHomeException.UnexpectedResponse("the streaming payload is not the documented shape", malformed)
    }
}

/**
 * `RTSV` is the real-time-streaming family (`RTSV1`, `RTSV2`, …) inside the comma-separated capability
 * string (`docs/api-contract.md` §3). The prefix is matched rather than one exact code because the
 * account's own cameras already announce two of them.
 */
private fun String.announcesLiveVideo(): Boolean =
    splitToSequence(',').any { it.trim().startsWith(LIVE_STREAM_PREFIX, ignoreCase = true) }

/**
 * `[ASSUMED]` — the documented sentence is "Quota de streaming insuficiente" (§6), and the platform
 * writes the same word both ways elsewhere. Narrow on purpose: everything it does not match stays a
 * plain [SmartHomeException.ApiError], which is the safe side of the guess.
 */
private fun String.announcesExhaustedQuota(): Boolean =
    QUOTA_WORDS.any { contains(it, ignoreCase = true) }

private const val LIVE_STREAM_PREFIX = "RTSV"
private val QUOTA_WORDS = listOf("quota", "cota")
