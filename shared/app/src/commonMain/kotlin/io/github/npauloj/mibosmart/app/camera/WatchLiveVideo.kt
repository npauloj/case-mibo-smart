package io.github.npauloj.mibosmart.app.camera

import io.github.npauloj.mibosmart.domain.camera.StreamError
import io.github.npauloj.mibosmart.domain.camera.StreamState
import io.github.npauloj.mibosmart.domain.camera.StreamStep
import io.github.npauloj.mibosmart.domain.camera.StreamingRepository
import io.github.npauloj.mibosmart.domain.device.Device
import io.github.npauloj.mibosmart.domain.error.SmartHomeException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.jvm.JvmInline
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** The live-video kill switch: `local.properties` → `BuildConfig` → here. */
@JvmInline
value class LiveVideoSwitch(val isOn: Boolean)

/** What opening a camera means (SPEC V1, V2, V6, V7). */
class WatchLiveVideo(
    private val streaming: StreamingRepository,
    private val liveVideo: LiveVideoSwitch,
) {

    suspend operator fun invoke(camera: Device, publish: (StreamState) -> Unit) {
        if (!liveVideo.isOn) return publish(StreamState.NoLiveCapability)
        if (!camera.isOnline) return publish(StreamState.CameraOffline)
        try {
            publish(StreamState.Creating(StreamStep.CheckingCapability))
            if (!streaming.announcesLiveVideo(camera.id)) return publish(StreamState.NoLiveCapability)
            publish(StreamState.Creating(StreamStep.CreatingSession))
            withContext(NonCancellable) { publish(StreamState.Live(streaming.openSession(camera.id))) }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Throwable) {
            publish(failure.toState())
        }
    }
}

private fun Throwable.toState(): StreamState = when (this) {
    is SmartHomeException -> toState()
    else -> StreamState.Failed(StreamError.Failed)
}

/**
 * No `else` on purpose: [SmartHomeException] is sealed, so a category added by a later slice
 * fails to compile here until someone decides what the video screen does with it (SPEC E2).
 */
private fun SmartHomeException.toState(): StreamState = when (this) {
    is SmartHomeException.QuotaExceeded -> StreamState.QuotaExceeded
    is SmartHomeException.DeviceNotFound -> StreamState.CameraOffline
    is SmartHomeException.TokenRejected -> StreamState.Failed(StreamError.TokenRejected)
    is SmartHomeException.TokenExpired -> StreamState.Failed(StreamError.TokenExpired)
    is SmartHomeException.Offline -> StreamState.Failed(StreamError.Offline)
    is SmartHomeException.UnexpectedResponse -> StreamState.Failed(StreamError.UnexpectedResponse)
    is SmartHomeException.ApiError -> StreamState.Failed(StreamError.Failed)
    is SmartHomeException.Forbidden -> StreamState.Failed(StreamError.Failed)
}
