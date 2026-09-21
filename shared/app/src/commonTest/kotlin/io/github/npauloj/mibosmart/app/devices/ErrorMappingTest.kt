package io.github.npauloj.mibosmart.app.devices

import io.github.npauloj.mibosmart.domain.error.SmartHomeException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest

/**
 * SPEC **E2**: every failure the taxonomy can currently raise reaches the screen as a category of its
 * own, and none of them collapses into the catch-all.
 *
 * Exhaustiveness itself is enforced by the compiler — `ListDevices` maps the sealed taxonomy with a
 * `when` that has no `else`, so a subtype added by a later slice does not build until it has a
 * category. What this test adds is that the categories are the *right* ones and that none of them
 * silently became [DeviceListResult.Failed].
 */
class ErrorMappingTest {

    @Test
    fun exhaustive() = runTest {
        val expected = mapOf<SmartHomeException, DeviceListResult>(
            SmartHomeException.TokenRejected() to DeviceListResult.TokenRejected,
            SmartHomeException.TokenExpired("Token expirado, por favor gere um novo token") to
                DeviceListResult.TokenExpired("Token expirado, por favor gere um novo token"),
            SmartHomeException.DeviceNotFound() to DeviceListResult.DeviceNotFound,
            SmartHomeException.Offline(cause = null) to DeviceListResult.Offline,
            SmartHomeException.UnexpectedResponse("not an envelope") to DeviceListResult.UnexpectedResponse,
            // The only category that may carry the server's words — and it keeps them in the
            // exception, never in the result the screen renders (SPEC U6).
            SmartHomeException.ApiError("Erro desconhecido") to DeviceListResult.Failed,
        )

        expected.forEach { (failure, result) ->
            val listDevices = ListDevices(FakeDeviceRepository { throw failure })

            assertEquals(result, listDevices(), "mapping ${failure::class.simpleName}")
        }
    }

    /** Anything outside the taxonomy — a bug, not a partner outcome — is still a rendered state. */
    @Test
    fun anUnknownFailureIsStillAState() = runTest {
        val listDevices = ListDevices(FakeDeviceRepository { throw IllegalStateException("bug") })

        assertEquals(DeviceListResult.Failed, listDevices())
    }

    /** SPEC E4 / ADR-002: a cancelled load is not an error the screen has to explain. */
    @Test
    fun cancellationIsNotMapped() = runTest {
        val listDevices = ListDevices(FakeDeviceRepository { throw CancellationException("gone") })

        assertFailsWith<CancellationException> { listDevices() }
    }
}
