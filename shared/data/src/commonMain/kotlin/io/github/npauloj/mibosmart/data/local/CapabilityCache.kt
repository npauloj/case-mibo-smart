package io.github.npauloj.mibosmart.data.local

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * What a device answered to `funcoes`, kept so the account pays for that answer once (ADR-006, SPEC V1).
 *
 * It is an interface because the store behind it changes and the rule does not: today the answer lives
 * in memory, and the durable `capabilities` table ADR-006 describes arrives with the SQLDelight schema
 * of **D-01b**, which this slice does not depend on. Swapping the implementation then changes nothing
 * above this line.
 */
internal interface CapabilityCache {

    /** What the device announced, or null when it was never asked. */
    suspend fun read(device: String): String?

    suspend fun write(device: String, functions: String)
}

/**
 * The in-process cache: one `funcoes` call per camera per process, which is what makes repeated visits
 * to the same camera free (SPEC V1).
 *
 * The mutex is not decoration — two taps on two cameras read and write this map from different
 * coroutines, and a torn map would cost a request, not a crash.
 */
internal class InMemoryCapabilityCache : CapabilityCache {

    private val mutex = Mutex()
    private val functionsByDevice = mutableMapOf<String, String>()

    override suspend fun read(device: String): String? = mutex.withLock { functionsByDevice[device] }

    override suspend fun write(device: String, functions: String) {
        mutex.withLock { functionsByDevice[device] = functions }
    }
}
