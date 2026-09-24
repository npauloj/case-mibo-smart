package io.github.npauloj.mibosmart.domain.lock

/**
 * The partner-side half of a lock: the domain states what it wants to know, `:shared:data`
 * knows the wire and the session it is read with (ADR-004).
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
     * @param entries how many the partner is asked for.
     * @return the entries as the partner ordered them; nothing here promises newest first.
     */
    suspend fun readOpeningHistory(address: LockAddress, entries: Int): List<OpeningEvent>

    /** Sets the level the lock announces itself at (`mudar-volume`, SPEC L7). */
    suspend fun changeVolume(address: LockAddress, volume: VolumeLevel)

    /** Asks the door to open or close (`controle-fechadura`, SPEC L3). */
    suspend fun command(address: LockAddress, command: LockCommand)

    /** Grants the app permission to command this lock (`habilitar-abrir-remoto`, SPEC L2). */
    suspend fun enableRemoteOpen(address: LockAddress)
}
