# ADR-003. MVVM with a single screen state; intents are suspend functions, not a second stream

Status: Accepted (2026-09-20)

## Context

Every action has to give feedback; loading, success, empty list and error have to be four distinct
states rather than one screen with a spinner on it; and the lifecycle and concurrency underneath have
to be right. The app talks to hardware:
a lock command can be sent and not confirmed, a camera can be offline, a token can expire mid-session.

Efthymiou (p. 200) calls MVVM "the go-to pattern of the industry", recommends an MVI-like single
`State` for complex screens, but warns (p. 206–207) that fully bidirectional streams (a second
`Intent` stream from UI to domain) are boilerplate without real benefit — simple suspend calls
suffice. For Android he recommends "Kotlin Flow & StateFlow combined with Kotlin Coroutines" (p. 215).
Immutability in reactive state is "not even a choice" (p. 130–132).

## Decision Drivers

- Every screen must expose loading / success / empty / error explicitly.
- Hardware states must be modelled, not inferred from booleans.
- ViewModels must survive configuration changes and cancel work on close.

## Considered Options

1. Classic MVVM with several `LiveData`/`StateFlow`s per screen (loading, data, error).
2. Full MVI with `Intent` stream, reducer and side-effect channel.
3. MVVM with one immutable `data class`/`sealed` state per screen exposed as `StateFlow`, and user
   actions as plain `suspend`/`fun` calls on the ViewModel.

## Decision

Option 3, using `androidx.lifecycle.ViewModel` from the multiplatform lifecycle artifact and
`koin-compose-viewmodel` for injection.

- One `StateFlow<XxxUiState>` per screen; state classes are immutable; updates via `update {}`.
- Hardware states are first-class:
  - `LockUiState.lock: LockState = Locked | Unlocked | CommandSent(sentAt) | Confirmed | CommandFailed(reason) | CommandExpired | RemoteOpenDisabled | Offline(lastKnown, lastSeen)`
    (vocabulary aligned with AWS IoT commands / Home Assistant MQTT Lock / Seam — see SPEC §4)
  - `CameraUiState.stream: StreamState = Creating | Live(url) | Reconnecting(attempt) | Expired | QuotaExceeded | Offline | Failed(reason)`
  - `SessionState = NoToken | Valid(expiresAt) | Expired`
- Actions are functions on the ViewModel (`onRefresh()`, `onToggleLock()`, `onVolume(level)`), each
  launching in `viewModelScope`; no `Intent` sealed class, no reducer.
- Navigation via Navigation Compose (multiplatform) with a real back stack.

## Consequences

- (+) Screens are a pure function of one state; Compose previews and tests are trivial.
- (+) `viewModelScope` cancels in-flight calls on close (structured concurrency) — important for the
  streaming session teardown (ADR-005).
- (−) A ViewModel that extends `androidx.lifecycle.ViewModel` technically belongs to the framework
  layer, not pure presentation (Efthymiou, p. 244). Accepted trade-off; the use cases remain
  framework-free.
- (−) Screens with many actions get long ViewModels; mitigated by delegating to use cases.

## Confirmation

- Each ViewModel has a test with `runTest` + `StandardTestDispatcher`, reading `state.value` after
  `advanceUntilIdle()`, covering loading → success, loading → empty, loading → error, and cancellation.
  Turbine is used only for one-shot event flows (`SharedFlow`: navigation, snackbar), never for the
  screen `StateFlow` (KMP testing guidance, 2025).
- Lint/grep: no `MutableStateFlow` exposed publicly; no `var` inside UI state classes.
- Every screen is split into `<Screen>` (ViewModel wrapper) and a stateless `<Screen>Content(state, on…)`
  with a `PreviewParameterProvider` covering all states (SPEC "Visual acceptance").

## Guardrail

- Guardrail: rule 6 — `viewmodels live in shared-app and expose StateFlow` in `:konture-test`
- Guardrail: rule 11 — `every stateless Content composable is previewed in its file` in `:konture-test`
