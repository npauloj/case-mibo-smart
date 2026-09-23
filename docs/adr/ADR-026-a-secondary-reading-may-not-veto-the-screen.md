# ADR-026. A secondary reading may not veto the lock screen

Status: Accepted (2026-09-23) — reverses a claim made in `LoadLock`'s own KDoc since L-01

## Context

Opening the lock screen issues the three reads of SPEC L1 together, inside a `coroutineScope`. The
KDoc said, in as many words:

> A failure in any of the three cancels the other two — `coroutineScope` does that — which is the
> right call for a screen that has nothing to show without all three values.

That reasoning was never measured. Measured on 2026-09-23, against the six locks in the test account,
by calling the partner directly:

| Read | Result |
|---|---|
| `fechaduras/status-abertura/v1` | `200` on **6 of 6** |
| `fechaduras/status-abrir-remoto/v1` | `200` on **6 of 6** |
| `fechaduras/volume/v1` | `500 "Erro desconhecido"` on **5 of 6** — only `IOT-MFR1001-IB` answers (`volume: 1`) |

So on five of six locks the screen threw away two correct answers — including whether the door is
open, which is the reason the screen exists — because a secondary control could not be filled in. The
user asked whether the door was open, the app knew, and it said "Resposta inesperada".

This is not an edge case in that account; it is the common case.

Two things about the mechanism are worth writing down, because both are easy to get wrong:

- **A child that throws inside a `coroutineScope` cancels its siblings the moment it fails**, not when
  someone `await`s it. A guard wrapped around the `await` would have looked correct, compiled, read
  well in review, and changed nothing.
- `runCatching` swallows `CancellationException`. Using it here would turn "the user left the screen"
  into "the lock has no volume", and leave a coroutine writing state nobody reads.

## Decision Drivers

- The screen's job is to say what is known about a door. Losing that to a volume selector inverts the
  priority.
- Not every read is equal, and pretending they are is what produced this. `isOpen` and
  `isRemoteOpenEnabled` are load-bearing — SPEC L2 requires the screen to state both at once, and
  without them there is nothing to draw. The volume is a control beside them.
- A missing reading must not be shown as a wrong one. A selector with nothing selected, and no
  explanation, is worse than a selector that says why it is out.
- The correction must not overshoot into "never fail".

## Considered Options

1. **Keep the current behaviour and treat the partner's `500` as a transient fault to retry.**
   Rejected: ADR-006 forbids blind retries, the account pays per request, and the `500` is not
   transient — it reproduced on every attempt, on five locks, across a whole session.
2. **Default a missing volume to `Medium`.** Rejected: there is no honest default. A selection on a
   control that reflects hardware nobody has heard from is the app inventing a fact about a building.
3. **`supervisorScope` so no failure cancels a sibling.** Rejected as a blunter version of option 4: it
   would also stop a failed `status-abertura` from failing the screen, and then the screen has to
   invent an "unknown" for the door itself — a real new UI state, unjustified by any measurement,
   since that read answered `200` on all six locks.
4. **Guard the volume read alone, inside its own `async`.** Chosen.

## Decision

**Option 4.** `LockState.volume` becomes `VolumeLevel?`, and `LoadLock` reads it through
`readVolumeOrNull`, which catches inside the `async` — not around the `await` — and rethrows
`CancellationException` explicitly rather than reaching for `runCatching`.

The other two reads keep `coroutineScope`'s behaviour: either fails the screen, as before.

On the screen, a null volume disables the chips and renders a `StateNotice` with
`StateTone.Waiting` — amber, not red. The door's own state is on the frame above it and read fine;
painting this red would say the screen is broken when one secondary reading is missing. Writing is
refused while the level is unknown: `mudar-volume` without knowing the current level is the app
deciding for hardware it has not heard from.

The nullability also forced a distinction that had been implicit in the test double.
`LockState.volume` being null is what the **screen** knows; a repository never returns "unknown" — it
answers or it throws. `FakeLockRepository` therefore gains a `volumeFailure` parameter instead of
being handed a null-volume state, and fails loudly (`checkNotNull`) if a test tries the latter.

## Consequences

- (+) Five of the six locks in the test account go from an unusable screen to a working one.
- (+) The frame is now in the preview set (`LockScreen_VolumeUnknown`) and therefore in the golden set,
  so a regression is a picture in a PR rather than a report from a demo.
- (−) `VolumeLevel?` propagates: every caller has to say what it does with "unknown". That is the
  point, but it is 3 call sites of friction and will be more as the screen grows.
- (−) The screen can now be partially populated, which is a category of state the other screens do not
  have. If a second reading ever becomes optional, this stops being a special case and wants a shape
  of its own rather than a second nullable field.

## Confirmation

- `aVolumeThatRefusesDoesNotTakeTheDoorWithIt` — **verified failing against the unfixed code**:
  restoring the direct `readVolume` call makes it, and only it, fail (6 tests, 1 failed).
- `aDoorStateThatRefusesStillFailsTheScreen` — passes both before and after, on purpose: it guards the
  over-correction, not the correction.
- `./gradlew :konture-test:test :shared:domain:testAndroidHostTest :shared:data:testAndroidHostTest
  :shared:app:testAndroidHostTest :androidApp:assembleDebug :androidApp:lintDebug` — green.
