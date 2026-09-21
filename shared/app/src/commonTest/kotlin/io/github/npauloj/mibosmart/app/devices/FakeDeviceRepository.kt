package io.github.npauloj.mibosmart.app.devices

import io.github.npauloj.mibosmart.domain.device.Device
import io.github.npauloj.mibosmart.domain.device.DeviceRepository

/**
 * A partner that answers whatever the test wants, and counts how often it was asked — the account
 * pays per request (ADR-006), so "how many calls" is part of the behaviour under test.
 */
internal class FakeDeviceRepository(private val answer: suspend () -> List<Device> = { emptyList() }) :
    DeviceRepository {

    var calls: Int = 0
        private set

    override suspend fun firstPage(): List<Device> {
        calls++
        return answer()
    }
}
