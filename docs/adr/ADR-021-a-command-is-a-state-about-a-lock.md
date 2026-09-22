# ADR-021. A lock command is a state *about* a lock, and its failure notice has a lifetime

Status: Accepted (2026-09-21) — refines [ADR-003](ADR-003-presentation-single-state.md)

## Context

SPEC §4 gives the lock a state machine — `Locked` · `Unlocked` · `CommandSent` · `Confirmed` ·
`CommandFailed` · `CommandExpired` · `Offline` · `RemoteOpenDisabled` — and L-01a implemented the
reading half of it by making `Offline` and `RemoteOpenDisabled` *readings* of one `LockUiState.Ready`
rather than alternatives to it. Its KDoc committed the rest: the command states would arrive "as new
subtypes, never as a refactor of these".

L-02 has to land four of them at once, and two questions have no obvious answer:

1. **Where do the command states live?** A field on `Ready` (`writeInFlight`, the pattern the volume
   and remote-open writes already use) is the smallest diff, but SPEC L5 requires restoring "the
   previous state" after a failed command, and a `Ready` that is simultaneously the lock *and* the
   lock-as-it-was has nowhere to keep the second value.
2. **What is "for the duration of a retry snackbar" (SPEC L5)?** The lock screen owns no `Scaffold`
   and no `SnackbarHost`; every other failure on it is rendered inline beside the control that
   failed. Taken literally, the criterion asks for a component this screen does not have.

## Decision Drivers

- The screen may never settle on a door state that no read reported. Everything else is secondary.
- SPEC L5's restoration has to be provable on a virtual clock, not asserted by eye.
- Nothing may leave the user stuck: a command that cannot be confirmed still has to leave the screen
  with something to do (SPEC U6).
- One pattern, not two: the volume write's `writeInFlight`/`writeFailure` shape stays as it is.

## Considered Options

1. **Fields on `Ready`** — `command: LockCommand?`, `commandFailure`, `unconfirmed`. Three more
   nullable fields whose legal combinations are not expressible, and no place for the pre-command
   readings. Rejected.
2. **Flat sibling subtypes** of `LockUiState`, each repeating `lock`, `volume`, `lastSeen`,
   `areWritesEnabled`. Rejected: the composable would duplicate `LockReadings` per branch, and every
   field added to `Ready` afterwards would have to be added four more times.
3. **A `Commanding` sub-interface carrying the `Ready` it is happening to.**

## Decision

Option 3, plus a lifetime for the notice.

- `LockUiState.Commanding` has `before: Ready` and `command: LockCommand`, and `CommandSent`,
  `CommandExpired` and `CommandFailed` implement it. `deviceName` and `lastSeen` delegate to
  `before`, so the readings keep rendering through the whole command — a command in flight is not a
  reason to stop showing the door. `Confirmed` gets no subtype: SPEC §4 calls it transient, and the
  transition that produces it lives in `:shared:domain` as `LockCommandOutcome.of`.
- **`CommandFailed` is a state with a lifetime**, not a host: `LockViewModel` publishes it, waits
  Material's short-snackbar duration and restores `before` — but only if the state on screen is still
  the notice it put there. That identity check is what keeps a late restore from undoing a command
  the user has since sent. A `SnackbarHost` would have made the same behaviour untestable on the
  ViewModel, which is where the rule belongs.
- **An unconfirmed command keeps the lock commandable.** `CommandSent` is the only phase that refuses
  a new command (SPEC L6); `CommandExpired` and `CommandFailed` hand back `before`, and a "Verificar"
  that disagrees *updates* `before` with what it read. A screen that could only ever offer one more
  read would strand the user on a door that quietly never moved.

## Consequences

- (+) The restoration of SPEC L5 is a value, not a remembered one: `before` is on the state that
  replaces the lock, so nothing has to be stashed in the ViewModel.
- (+) `LockScreenContent` has one extra `when` branch and reuses `LockReadings(state.before, …)`;
  no rendering is duplicated per phase.
- (−) `Ready.canCommand` and the composable's `enabled` say the same thing twice — deliberately: the
  disabled button is the affordance, `commandableLock()` is the guarantee, and a door is worth
  guarding twice.
- (−) The notice's four seconds are a constant in the ViewModel. If the app later grows a real
  snackbar host, this is the code that moves, and `networkFailureShowsCommandFailedThenRestores`
  is the test that has to keep passing across the move.

## Confirmation

- `ToggleLockTest.networkFailureShowsCommandFailedThenRestores` asserts the notice **and** the
  restoration, in that order, on the virtual clock.
- `ToggleLockTest.ignoresDoubleTapWhileCommandSent` — the one phase that refuses a second command.
- `ToggleLockTest.disagreementBecomesCommandExpiredNoPolling` — ten virtual minutes after an
  unconfirmed command, the request count has not moved; then "Verificar" moves it by exactly one.
- `LockScreen_CommandSent`, `_CommandExpired`, `_CommandFailed` and their `_Dark` variants render the
  three phases over the same readings.
