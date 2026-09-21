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

/**
 * The live-video kill switch: `local.properties` → `BuildConfig` → here.
 *
 * It exists so the app can be run on the shared account without spending a byte of streaming quota.
 * Off means the camera screen goes straight to [StreamState.NoLiveCapability] and **no session is ever
 * created** — which is why it is checked before anything else this class does.
 */
@JvmInline
value class LiveVideoSwitch(val isOn: Boolean)

/**
 * What opening a camera means (SPEC V1, V2, V6, V7).
 *
 * It **publishes** its states through [publish] instead of returning one, unlike the app's other use
 * cases, and the reason is SPEC V2: the stream url expires 15 seconds after the partner mints it, so
 * the session has to reach the player in the same coroutine that created it, before anything else can
 * suspend. A returned value would arrive one resumption later, and the steps of V3 would have nowhere
 * to come from.
 *
 * Costs at most two partner requests — `funcoes` (cached, so usually zero) and `criar-fluxo-video` —
 * and never retries by itself (ADR-006).
 */
class WatchLiveVideo(
    private val streaming: StreamingRepository,
    private val liveVideo: LiveVideoSwitch,
) {

    suspend operator fun invoke(camera: Device, publish: (StreamState) -> Unit) {
        if (!liveVideo.isOn) return publish(StreamState.NoLiveCapability)
        // SPEC V7: an offline camera is known to be offline from the row the screen was opened on.
        // Asking the partner anyway would spend a request to be told what the list already said.
        if (!camera.isOnline) return publish(StreamState.CameraOffline)
        try {
            publish(StreamState.Creating(StreamStep.CheckingCapability))
            if (!streaming.announcesLiveVideo(camera.id)) return publish(StreamState.NoLiveCapability)
            publish(StreamState.Creating(StreamStep.CreatingSession))
            // Creating and publishing are one step on purpose (SPEC V2, V8). `NonCancellable` here is
            // not about finishing work the user abandoned: it is about never opening a session whose
            // id nobody learned. A session the app cannot name is a session it cannot close, and it
            // would keep billing the account until the partner's own cap ends it.
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
    // Not a partner outcome but a bug of ours; the screen still has to render something.
    else -> StreamState.Failed(StreamError.Failed)
}

/**
 * No `else` on purpose: [SmartHomeException] is sealed, so a category added by a later slice fails to
 * compile here until someone decides what the video screen does with it (SPEC E2).
 */
private fun SmartHomeException.toState(): StreamState = when (this) {
    is SmartHomeException.QuotaExceeded -> StreamState.QuotaExceeded
    // The partner does not know this camera any more — for the user that is the same thing the list
    // already says about an unreachable device, and it is not worth a second vocabulary (SPEC V7).
    is SmartHomeException.DeviceNotFound -> StreamState.CameraOffline
    is SmartHomeException.TokenRejected -> StreamState.Failed(StreamError.TokenRejected)
    is SmartHomeException.TokenExpired -> StreamState.Failed(StreamError.TokenExpired)
    is SmartHomeException.Offline -> StreamState.Failed(StreamError.Offline)
    is SmartHomeException.UnexpectedResponse -> StreamState.Failed(StreamError.UnexpectedResponse)
    is SmartHomeException.ApiError -> StreamState.Failed(StreamError.Failed)
}
