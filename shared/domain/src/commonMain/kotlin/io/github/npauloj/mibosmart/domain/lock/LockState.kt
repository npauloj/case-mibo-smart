package io.github.npauloj.mibosmart.domain.lock

/**
 * A lock as its three reads describe it at one moment in time (SPEC L1).
 * @property isOpen whether the door is open right now (`status-abertura`).
 * @property isRemoteOpenEnabled whether the lock accepts commands from the app at all; it is a
 * precondition the user has to grant on the device (`status-abrir-remoto`, SPEC L2).
 * @property volume the level the lock announces itself at (`volume`) — **null when the lock did
 * not report it**.
 */
data class LockState(
    val isOpen: Boolean,
    val isRemoteOpenEnabled: Boolean,
    val volume: VolumeLevel?,
)
