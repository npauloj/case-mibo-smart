package io.github.npauloj.mibosmart.app.devices

import io.github.npauloj.mibosmart.domain.error.SmartHomeException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest

/**
 * SPEC **E2**: every failure the taxonomy can currently raise reaches the screen as a category
 * of its own, and none of them collapses into the catch-all.
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
            SmartHomeException.QuotaExceeded() to DeviceListResult.Failed,
            SmartHomeException.ApiError("Erro desconhecido") to DeviceListResult.Failed,
        )

        expected.forEach { (failure, result) ->
            val listDevices = listDevices(FakeDeviceRepository { throw failure })

            assertEquals(result, listDevices.firstPage(), "mapping ${failure::class.simpleName}")
        }
    }

    /** Anything outside the taxonomy — a bug, not a partner outcome — is still a rendered state. */
    @Test
    fun anUnknownFailureIsStillAState() = runTest {
        val listDevices = listDevices(FakeDeviceRepository { throw IllegalStateException("bug") })

        assertEquals(DeviceListResult.Failed, listDevices.firstPage())
    }

    /** SPEC E4 / ADR-002: a cancelled load is not an error the screen has to explain. */
    @Test
    fun cancellationIsNotMapped() = runTest {
        val listDevices = listDevices(FakeDeviceRepository { throw CancellationException("gone") })

        assertFailsWith<CancellationException> { listDevices.firstPage() }
    }
}
