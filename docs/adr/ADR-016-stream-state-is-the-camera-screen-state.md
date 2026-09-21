# ADR-016. `StreamState` is the camera screen's state, and it is a domain type

Status: Accepted (2026-09-21) · Refines ADR-003 for the camera screen (V-01a)

## Context

[ADR-003](ADR-003-presentation-single-state.md) sketched the camera screen before any of it existed:

```
CameraUiState.stream: StreamState = Creating | Live(url) | Reconnecting(attempt) | Expired
                                  | QuotaExceeded | Offline | Failed(reason)
```

Implementing V-01a produced three differences from that sketch, and `CLAUDE.md` says a deviation from
an ADR is recorded as a new ADR rather than argued in a code comment.

## Decision Drivers

- One screen, one exhaustive `when`, and a compiler error when a later slice adds a state (ADR-002).
- SPEC V2: the session and its url reach the player in the same non-suspending step.
- SPEC V8: the screen has a state that means "nothing is running", or teardown has nowhere to land.

## Decision

**1. There is no `CameraUiState` wrapper. `StreamState` is the screen's state.**
The only field the wrapper would have added is the camera's name, and the name already arrives at the
screen as part of the destination (`LiveVideoScreen(camera: Device, …)`), exactly as `LockDestination`
carries the lock's. A one-field wrapper would have duplicated it and made every preview construct two
objects to show one.

**2. `StreamState` lives in `:shared:domain`, package `camera`, not beside the ViewModel.**
It is the *stream's* state, not the screen's decoration: V-01b renders the same set on iOS, V-02 adds
`Reconnecting` to it, and nothing in it mentions Compose. This is also where the V-01a ticket places
it. The per-use-case sealed results of ADR-002 (`LoadLockResult`, `DeviceListResult`) stay where they
are — they are answers to one call, while this is a value two platforms render.

**3. Names that changed from the sketch, and why:**

| Sketch | Shipped | Reason |
|---|---|---|
| `Creating` | `Creating(step: StreamStep)` | SPEC V3 requires the wait to be shown **in words**, and there are three distinct waits (`CheckingCapability`, `CreatingSession`, `Connecting`). A stateless `Creating` could only be rendered as a spinner, which V3 forbids. |
| `Live(url)` | `Live(session, firstFrame)` | The ViewModel must keep the whole [`StreamSession`] to call `encerrar-sessao` (V8), and the screen keeps the "Conectando…" overlay until the player reports a frame. |
| `Offline` | `CameraOffline` | `StreamError.Offline` already means "the call never reached the partner". Two things called `Offline` on one screen, one about the device and one about the network, is the ambiguity SPEC V7 and SPEC S4 exist to keep apart. |
| — | `Idle` | Teardown has to leave the screen in a state that holds **no** session (V8). Without it, `stop()` would have to leave `Live` on screen with a url nobody is playing. |
| — | `NoLiveCapability` | SPEC V1, and the state the kill switch produces. |

**4. The use case publishes instead of returning.** `WatchLiveVideo(camera, publish)` reports each
state through a non-suspending lambda rather than returning one result. SPEC V2 is the reason: the
stream url expires 15 s after the partner mints it, so the session has to reach the state — and the
player — in the same coroutine step that created it. A returned value arrives one resumption later,
and the intermediate steps of V3 would have nowhere to come from.

**5. The camera ViewModel's intents are plain functions, not `suspend` ones.** That is what ADR-003
§Decision actually prescribes ("actions are functions on the ViewModel … each launching in
`viewModelScope`"); the extra `suspend` twins on `LockViewModel` exist so a test can await them. Here
the launched job is the thing `stop()` must be able to **cancel**, so the ViewModel keeps it and the
tests drive it with `advanceUntilIdle()` — which is what the SPEC's own V3 test prescribes.

## Consequences

- (+) One type, one `when`, rendered by two platforms; V-02 adds `Reconnecting` in one place.
- (+) `Idle` makes "no session is open" representable, which is what the teardown asserts against.
- (−) `StreamState` in the domain means a UI-shaped type lives there. It is accepted because nothing
  in it is UI: no Compose, no resource, no formatting — the screen maps each case to a string.
- (−) A screen whose state carries no camera name cannot be rendered from the state alone; previews
  pass the name explicitly, which is one extra argument.

## Confirmation

- `LiveVideoViewModelTest.stateSequenceOnHappyPath` asserts the exact sequence
  `Creating(CheckingCapability)` → `Creating(CreatingSession)` → `Live(firstFrame = false)` →
  `Live(firstFrame = true)`.
- `WatchLiveVideoTest.playerPreparedImmediately` asserts, in virtual time, that the state carrying the
  url is published at the same instant the partner returned the session — point 4, proven.
- Every case of `StreamState` has a named preview in `LiveVideoScreenPreviews.kt` plus its dark
  variant; a case added without one is visible immediately in the `AllStates` preview.
