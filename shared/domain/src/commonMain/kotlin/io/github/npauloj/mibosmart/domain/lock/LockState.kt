package io.github.npauloj.mibosmart.domain.lock

/**
 * A lock as its three reads describe it at one moment in time (SPEC L1).
 *
 * Two of the three only make sense together: a lock that is closed but has remote opening disabled
 * cannot be commanded, and the screen has to say both things at once (SPEC L2). The volume is not one
 * of those two — it is a reading beside them, and it is allowed to be missing. The command states of SPEC §4 — `CommandSent`, `Confirmed`, `CommandExpired` — are not
 * here because nothing in this slice can produce them: L-02 adds them with the commands themselves.
 *
 * @property isOpen whether the door is open right now (`status-abertura`).
 * @property isRemoteOpenEnabled whether the lock accepts commands from the app at all; it is a
 *   precondition the user has to grant on the device (`status-abrir-remoto`, SPEC L2).
 * @property volume the level the lock announces itself at (`volume`) — **null when the lock did not
 *   report it**. Measured 2026-09-23: `fechaduras/volume/v1` answers `500 "Erro desconhecido"` on five
 *   of the six locks in the test account and `200` on the sixth. Nullable rather than defaulted,
 *   because there is no honest default: guessing `Medium` would put a selection on a control that
 *   reflects hardware nobody has heard from (ADR-026).
 */
data class LockState(
    val isOpen: Boolean,
    val isRemoteOpenEnabled: Boolean,
    val volume: VolumeLevel?,
)
