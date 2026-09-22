package io.github.npauloj.mibosmart.domain.lock

/**
 * The two things the app may ask a door to do (SPEC L3).
 *
 * The wire says `aberto: true|false` and the domain says [Open] / [Close]: the same fact, named so
 * that a caller cannot pass the wrong boolean by accident (`docs/api-contract.md` §5, ADR-004).
 *
 * @property opensTheDoor what the lock must report on `status-abertura` for this command to count as
 *   confirmed — which is the whole of [LockCommandOutcome]'s rule.
 */
enum class LockCommand(val opensTheDoor: Boolean) {

    Open(opensTheDoor = true),

    Close(opensTheDoor = false),
    ;

    companion object {

        /** The command that moves [lock] away from where it is — the one the single control offers. */
        fun toggling(lock: LockState): LockCommand = if (lock.isOpen) Close else Open
    }
}

/**
 * What became of a command once the app looked (SPEC §4, L3, L4).
 *
 * The screen's `CommandSent`, `CommandFailed` and `CommandExpired` are phases of *waiting* — they
 * need a clock, a network and somewhere to draw, so they live with the screen. What lives here is the
 * one transition that needs none of those: **the API acknowledges the command, the device confirms
 * it**, and the only evidence of the second is a fresh `status-abertura` that agrees with what was
 * asked. Keeping that rule in `:shared:domain` is what stops a screen from ever settling on a state
 * the hardware never reported.
 *
 * `Confirmed` is transient in SPEC §4 and transient here too: there is no state to sit in, only a
 * [Confirmed.lock] to show.
 */
sealed interface LockCommandOutcome {

    /** The lock agreed: [lock] is the door as it reads **after** the command (SPEC L3). */
    data class Confirmed(val lock: LockState) : LockCommandOutcome

    /** The read disagreed, or none arrived in time — the app does not know, and says so (SPEC L4). */
    data object Expired : LockCommandOutcome

    companion object {

        /**
         * SPEC L3 / L4 in one expression.
         *
         * @param commanded the lock as it was read before the command; only [LockState.isOpen] can
         *   have moved, so the volume and the precondition survive a confirmation untouched.
         * @param confirmation what `status-abertura` answered afterwards, or `null` when nothing
         *   answered inside the confirmation window. A timeout and a disagreement end in the same
         *   place on purpose: both mean the device did not confirm, and neither may show the user a
         *   door that moved.
         */
        fun of(
            command: LockCommand,
            commanded: LockState,
            confirmation: Boolean?,
        ): LockCommandOutcome = when (confirmation) {
            command.opensTheDoor -> Confirmed(commanded.copy(isOpen = command.opensTheDoor))
            else -> Expired
        }
    }
}
