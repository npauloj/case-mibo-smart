package io.github.npauloj.mibosmart.data.local

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * What a device answered to `funcoes`, kept so the account pays for that answer once (ADR-006,
 * SPEC V1).
 */
internal interface CapabilityCache {

    /** What the device announced, or null when it was never asked. */
    suspend fun read(device: String): String?

    suspend fun write(device: String, functions: String)
}

/**
 * The in-process cache: one `funcoes` call per camera per process, which is what makes repeated
 * visits to the same camera free (SPEC V1).
 */
internal class InMemoryCapabilityCache : CapabilityCache {

    private val mutex = Mutex()
    private val functionsByDevice = mutableMapOf<String, String>()

    override suspend fun read(device: String): String? = mutex.withLock { functionsByDevice[device] }

    override suspend fun write(device: String, functions: String) {
        mutex.withLock { functionsByDevice[device] = functions }
    }
}
