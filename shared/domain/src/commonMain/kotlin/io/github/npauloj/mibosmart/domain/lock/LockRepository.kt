package io.github.npauloj.mibosmart.domain.lock

/**
 * The partner-side half of a lock: the domain states what it wants to know, `:shared:data` knows the
 * wire and the session it is read with (ADR-004).
 *
 * The three reads are separate functions on purpose. They are three independent partner calls
 * (`docs/api-contract.md` §5) and the use case issues them in parallel (SPEC L1); a single
 * `read(address)` would hide that from the one place that has to be careful about it — every call
 * spends one request of the account's budget (ADR-006).
 *
 * Failures are typed exceptions from `domain.error` rather than return values (ADR-002).
 */
interface LockRepository {

    /** Whether the door is open (`status-abertura`). */
    suspend fun readOpenState(address: LockAddress): Boolean

    /** Whether the lock accepts commands from the app (`status-abrir-remoto`, SPEC L2). */
    suspend fun readRemoteOpenEnabled(address: LockAddress): Boolean

    /** The level the lock announces itself at (`volume`, SPEC L8). */
    suspend fun readVolume(address: LockAddress): VolumeLevel
}
