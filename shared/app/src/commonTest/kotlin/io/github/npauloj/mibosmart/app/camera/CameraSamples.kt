package io.github.npauloj.mibosmart.app.camera

import io.github.npauloj.mibosmart.domain.camera.StreamSession
import io.github.npauloj.mibosmart.domain.camera.StreamingRepository
import io.github.npauloj.mibosmart.domain.device.Device
import io.github.npauloj.mibosmart.domain.device.DeviceId
import io.github.npauloj.mibosmart.domain.device.DeviceKind
import io.github.npauloj.mibosmart.domain.device.DeviceOrigin
import io.github.npauloj.mibosmart.domain.device.DeviceStatus

/** Placeholders only — no serial of the test account enters a versioned file (ADR-008). */
internal object CameraSamples {

    val SESSION = StreamSession(
        id = "sessao-1",
        url = "https://portal.example.invalid/stream/x",
        monitorUrl = "https://portal.example.invalid/monitor_stream.html?session_id=sessao-1",
        quotaGb = 0.5,
    )

    fun camera(status: DeviceStatus = DeviceStatus.Online): Device = Device(
        id = DeviceId("PLACEHOLDER-CAM-NS"),
        name = "iM7-FC",
        model = "iM7-FC",
        status = status,
        lastSeen = null,
        origin = DeviceOrigin.Linked,
        kind = DeviceKind.Camera,
        parent = null,
        // Blank, as the partner sends it for this camera family — streaming addresses the plain `ns`.
        productId = "",
    )
}

/**
 * A partner that answers on command, and counts what it was asked for.
 *
 * The two hooks are what makes the timing assertions of SPEC V2 and V8 possible: a test can hold the
 * capability check or the session creation open and drive the screen through each step, or let a
 * creation land after the screen that asked for it is already gone.
 */
internal class FakeStreamingRepository(
    private val announcesLiveVideo: Boolean = true,
    private val session: StreamSession = CameraSamples.SESSION,
    private val failure: (() -> Throwable)? = null,
    private val onCapability: suspend () -> Unit = {},
    private val onOpen: suspend () -> Unit = {},
) : StreamingRepository {

    var capabilityChecks: Int = 0
        private set

    val opened: MutableList<DeviceId> = mutableListOf()
    val closed: MutableList<String> = mutableListOf()

    override suspend fun announcesLiveVideo(camera: DeviceId): Boolean {
        capabilityChecks++
        onCapability()
        return announcesLiveVideo
    }

    override suspend fun openSession(camera: DeviceId): StreamSession {
        onOpen()
        failure?.let { throw it() }
        opened += camera
        return session
    }

    override suspend fun closeSession(sessionId: String) {
        closed += sessionId
    }
}
