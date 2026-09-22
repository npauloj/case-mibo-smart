# ADR-022. The opening history is a tab of the lock screen with a ViewModel of its own

Status: Accepted (2026-09-21) — refines [ADR-003](ADR-003-presentation-single-state.md) and
[ADR-006](ADR-006-local-persistence-and-request-budget.md)

## Context

SPEC L9/L10 put the opening history behind a "tab" of the lock screen and price it at exactly one
`historico-abertura` per entry into that tab, with none on the way back. SPEC §4 also asks for four
visual states of its own (loading · list · empty · error) and names `OpeningHistoryContent` as the
composable the previews are taken from.

Two decisions follow from that, and neither is obvious from the ticket:

1. **Where does the tab live?** The lock screen already renders `LockScreenContent` as one column
   with the back button and the device name in it, and L-01a/L-02 pinned its state machine
   (ADR-021). The history has nothing to do with that state machine.
2. **One ViewModel or two?** A single `LockViewModel` holding both would be one state, which is what
   ADR-003 asks for per *screen* — but the two halves are paid for separately, load separately and
   fail separately.

## Decision Drivers

- The tab may not cost a request to come back to (SPEC L9, ADR-006). Whatever holds that guard has
  to outlive leaving the composition.
- L-03's non-goals forbid any change to L-01's or L-02's states; `LockUiState` is off-limits.
- Every state of the history needs a preview, so its content has to be a pure function of its own
  state, with no `LockUiState` in the signature.
- P-01 lands friendly labels on these rows next, against `OpeningHistoryContent`.

## Considered Options

1. **One `LockViewModel`, history as fields on `LockUiState`.** Rejected twice over: it rewrites the
   state L-02 just settled, and it makes "the history failed but the lock is fine" — the common
   case, since they are different endpoints — inexpressible without a second error field beside the
   first.
2. **A separate destination reached by a button.** Rejected: SPEC L9 says tab, and a destination
   would also lose the point of the guard — the natural way back from a destination rebuilds it.
3. **A tab in `LockScreen`, over two ViewModels.**

## Decision

Option 3.

- `LockScreen` hosts a `PrimaryTabRow` and switches between `LockScreenContent` and
  `OpeningHistoryScreen`. The tab row is the only thing added to the lock half: `LockScreenContent`
  keeps its signature, its state and all twelve of its previews untouched.
- `OpeningHistoryViewModel` owns `OpeningHistoryUiState` and the once-per-lock guard. It is a
  ViewModel and not remembered state precisely because of the guard: the store owner outlives the
  tab switch, so returning to the history finds the address unchanged and asks the partner nothing.
- The empty history is `Entries(emptyList())` rather than a subtype. "This door has not been opened"
  is an answer the partner gave, not a different shape of screen (SPEC L10).
- **The two halves share their vocabulary, not their state.** `LockError` and its user-facing
  sentences, and `LastSeen` and its buckets, were made `internal` instead of being copied: a history
  read fails in exactly the five ways a lock read does, and "há 5 min" rounds exactly the way
  "última atualização há 5 min" does. Two copies of either would drift on the first edit.

## Consequences

- (+) The request discipline is a property of a ViewModel and is unit-tested as one
  (`OpeningHistoryTest.oneRequestPerTabEntry`), not a property of a navigation graph.
- (+) The history's four states preview on their own, and P-01 has the `OpeningHistoryContent`
  seam the SPEC promised it.
- (+) A failing history leaves the lock tab fully usable, and the reverse.
- (−) The back button is drawn by both tabs' contents, so the shell is stated twice. It is two lines
  and it keeps both contents previewable without a host; a `Scaffold` for the lock destination is
  the refactor that removes it, and it belongs to whichever slice gives this screen a real top bar.
- (−) `LastSeen.Never` is unreachable from a history row — an entry that exists happened at a time —
  so the history's rendering groups it with the sub-minute case rather than inventing a sentence for
  a value it can never receive.

## Confirmation

- `OpeningHistoryTest.oneRequestPerTabEntry` — two entries into the tab, one `historico-abertura`,
  asked for `quantidade` 50 exactly once.
- `OpeningHistoryTest.aFailedReadIsNamedAndRetryable` — the history fails with a named cause, and
  only the retry tap asks again.
- `OpeningHistory_Loading`, `_List`, `_Empty`, `_Error` and their `_Dark` variants render the four
  states from `OpeningHistoryContent` alone, with no ViewModel.
- The twelve `LockScreen_*` previews and `LockViewModelTest`/`ToggleLockTest` still pass unchanged,
  which is what "no change to L-01's or L-02's states" means in practice.
