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

    /**
     * The door's recent openings (`historico-abertura`, SPEC L9).
     *
     * @param entries how many the partner is asked for. The endpoint is not paginated, so the count
     *   is the whole size of the answer and the only lever there is — which is why it is a parameter
     *   rather than a constant here: how much history is worth one request is a decision for the use
     *   case that spends it (ADR-006), not for the wire.
     * @return the entries as the partner ordered them; nothing here promises newest first.
     */
    suspend fun readOpeningHistory(address: LockAddress, entries: Int): List<OpeningEvent>

    /** Sets the level the lock announces itself at (`mudar-volume`, SPEC L7). */
    suspend fun changeVolume(address: LockAddress, volume: VolumeLevel)

    /**
     * Asks the door to open or close (`controle-fechadura`, SPEC L3).
     *
     * Returning normally means **the partner accepted the command**, not that the door moved: the API
     * acknowledges the request and the hardware answers later, if at all. The only evidence that the
     * lock obeyed is a fresh [readOpenState] that agrees, which is why this returns nothing — there
     * is nothing here worth believing.
     */
    suspend fun command(address: LockAddress, command: LockCommand)

    /**
     * Grants the app permission to command this lock (`habilitar-abrir-remoto`, SPEC L2).
     *
     * **There is no parameter, and that is the contract.** The wire call takes `habilitar: true|false`
     * and the app only ever sends `true`: disabling remote opening from a phone would take a door's
     * safety net away with no way to notice, so the ability to ask for it does not exist in the
     * domain. A later slice that genuinely needs the other direction adds its own function and has to
     * justify it here.
     */
    suspend fun enableRemoteOpen(address: LockAddress)
}
