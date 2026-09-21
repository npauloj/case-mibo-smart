package io.github.npauloj.mibosmart.app.camera

import io.github.npauloj.mibosmart.domain.camera.StreamState
import io.github.npauloj.mibosmart.domain.device.Device
import io.github.npauloj.mibosmart.domain.device.DeviceStatus
import io.github.npauloj.mibosmart.domain.error.SmartHomeException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest

/**
 * SPEC V1, V2, V6, V7 — the decisions the use case makes before, and instead of, spending quota.
 *
 * Nothing here reaches the network: the partner is a fake, and the wire-level half of the same
 * criteria (the exact `criar-fluxo-video` body, the `funcoes` cache) is `:shared:data`'s
 * `WatchLiveVideoTest`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WatchLiveVideoTest {

    /** SPEC V1: no `RTSV`, no session — the camera is asked, and then left alone. */
    @Test
    fun noRtsvNoSession() = runTest {
        val partner = FakeStreamingRepository(announcesLiveVideo = false)

        val states = collectFrom(partner)

        assertEquals(StreamState.NoLiveCapability, states.last())
        assertEquals(1, partner.capabilityChecks)
        assertTrue(partner.opened.isEmpty(), "a camera that does not announce video was streamed anyway")
    }

    /**
     * SPEC V7: the device list already said the camera is offline, so nothing is asked of the partner.
     *
     * Not even `funcoes`: confirming what the list just reported would spend a request to learn
     * nothing (ADR-006).
     */
    @Test
    fun offlineCameraNoSession() = runTest {
        val partner = FakeStreamingRepository()

        val states = collectFrom(partner, camera = CameraSamples.camera(DeviceStatus.Offline))

        assertEquals(listOf(StreamState.CameraOffline), states)
        assertEquals(0, partner.capabilityChecks)
        assertTrue(partner.opened.isEmpty())
    }

    /**
     * SPEC V2: the url reaches the player in the same coroutine that created it, before anything else
     * suspends — the stream expires 15 seconds after the partner mints it.
     *
     * The proof is virtual time: the fake stamps the moment the session exists, the caller stamps the
     * moment it was handed one. Any suspension in between — a second call, a delay, a dispatch — would
     * push the two apart.
     */
    @Test
    fun playerPreparedImmediately() = runTest {
        val openedAt = mutableListOf<Long>()
        val preparedAt = mutableListOf<Long>()
        val partner = FakeStreamingRepository(
            onOpen = {
                delay(SLOW_PARTNER_MILLIS)
                openedAt += currentTime
            },
        )

        WatchLiveVideo(partner, LiveVideoSwitch(isOn = true))(CameraSamples.camera()) { state ->
            if (state is StreamState.Live) preparedAt += currentTime
        }

        assertEquals(listOf(SLOW_PARTNER_MILLIS), openedAt, "the fake partner never answered")
        assertEquals(openedAt, preparedAt, "the player was handed the url after something else suspended")
    }

    /** SPEC V6: quota exhaustion is its own state, and it is the one with no action attached. */
    @Test
    fun quotaExceededState() = runTest {
        val partner = FakeStreamingRepository(failure = { SmartHomeException.QuotaExceeded() })

        assertEquals(StreamState.QuotaExceeded, collectFrom(partner).last())
    }

    /**
     * The kill switch of this slice's rollout plan: off, the app can be run on the shared account
     * without ever opening a session — which is what makes the demo safe to rehearse.
     */
    @Test
    fun killSwitchOffOpensNoSession() = runTest {
        val partner = FakeStreamingRepository()

        val states = collectFrom(partner, liveVideo = LiveVideoSwitch(isOn = false))

        assertEquals(listOf(StreamState.NoLiveCapability), states)
        assertEquals(0, partner.capabilityChecks, "the switch is off and the partner was still called")
        assertTrue(partner.opened.isEmpty())
    }

    private suspend fun collectFrom(
        partner: FakeStreamingRepository,
        camera: Device = CameraSamples.camera(),
        liveVideo: LiveVideoSwitch = LiveVideoSwitch(isOn = true),
    ): List<StreamState> = buildList {
        WatchLiveVideo(partner, liveVideo)(camera) { add(it) }
    }

    private companion object {
        const val SLOW_PARTNER_MILLIS = 800L
    }
}
