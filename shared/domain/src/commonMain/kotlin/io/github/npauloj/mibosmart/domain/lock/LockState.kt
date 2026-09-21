package io.github.npauloj.mibosmart.domain.lock

/**
 * A lock as its three reads describe it at one moment in time (SPEC L1).
 *
 * The three values are read together and only make sense together: a lock that is closed but has
 * remote opening disabled cannot be commanded, and the screen has to say both things at once
 * (SPEC L2). The command states of SPEC §4 — `CommandSent`, `Confirmed`, `CommandExpired` — are not
 * here because nothing in this slice can produce them: L-02 adds them with the commands themselves.
 *
 * @property isOpen whether the door is open right now (`status-abertura`).
 * @property isRemoteOpenEnabled whether the lock accepts commands from the app at all; it is a
 *   precondition the user has to grant on the device (`status-abrir-remoto`, SPEC L2).
 * @property volume the level the lock announces itself at (`volume`).
 */
data class LockState(
    val isOpen: Boolean,
    val isRemoteOpenEnabled: Boolean,
    val volume: VolumeLevel,
)
