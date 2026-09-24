# ADR-027. A filter chip the app can answer itself costs no request

Status: Accepted (2026-09-24) — amends SPEC D4

## Context

`origem` is a server-side filter. Since D-02 the list reloaded from page 1 on every chip, so tapping
"Vinculados" and then "Compartilhados" spent two requests to be handed back subsets of rows the app
already had in memory.

The numbers are what make this worth an ADR rather than a micro-optimisation:

- `PAGE_SIZE` is **20**. The test account has **17 devices**. One page is the whole inventory, and
  `hasMore` is already `false` after the first load.
- Every `Device` carries its own `origin` (`DeviceOrigin.Linked` / `Shared`), read from the same
  payload. Nothing has to be fetched to know which chip a device belongs under.
- The account's request allowance **ran out on 2026-09-24**, the day of the delivery: the API answers
  `401 "Não autorizado, verifique os seus limites disponíveis"` on every endpoint. Two chips is two of
  the roughly fourteen requests a full demonstration costs.

So the app was paying for an answer it already had, on an account where requests are the scarce
resource the whole design is organised around (ADR-006).

## Decision Drivers

- A chip changes what is **shown**. That it also chose a request was an implementation detail leaking
  into the interaction.
- Filtering a **partial** list would silently hide devices: rows past the end of the loaded pages
  would simply not be there, and the user cannot tell an empty filter from a truncated one.
- The remembered-chip behaviour of SPEC D4 must survive, and it is written by the load — so skipping
  the load means writing it explicitly.
- No new state may be introduced that can disagree with what is on screen.

## Considered Options

1. **Always fetch with `origem: todos` and filter locally, never send a narrow `origem`.** Simplest,
   and strictly the fewest requests for this account. Rejected: on an account large enough to paginate,
   it pages through every device to show a handful, which is more requests, not fewer. It optimises for
   the test account rather than for the contract.
2. **Cache one page per origin and serve chips from whichever is warm.** Rejected: three cached lists
   that can disagree about the same device, for a gain that only exists when the list is incomplete —
   which is the case where they would disagree most.
3. **Answer locally only when the loaded set is the complete `todos` set; otherwise fetch.** Chosen.

## Decision

**Option 3.** `DeviceListViewModel.selectFilter` asks whether the rows in hand can describe the chip,
and only goes to the partner when they cannot.

They can when **all** of these hold:

- the loaded rows were fetched under `OriginFilter.All` — the one superset of every chip;
- `hasMore` is `false` — the same flag that already stops the endless scroll, so "complete" has one
  definition in the app and not two;
- there is something loaded, and no load is in flight (a result landing afterwards would overwrite the
  answer).

`OriginFilter.accepts(DeviceOrigin)` lives in `:shared:domain`, because it **is** the meaning of the
filter — the same question `origem` answers on the wire, asked locally.

A new private `loadedOrigin` records what the rows were fetched under, set from the query rather than
from the chip: after a local answer the two deliberately differ, and it is the fetch that decides what
was fetched.

The chip is persisted inside `selectFilter` when no load will run, so SPEC D4's "remembered across
launches" holds on both paths.

## Consequences

- (+) On this account every chip is free. A full demonstration drops from ~14 requests to ~12.
- (+) The interaction stops depending on the network: chips respond instantly and cannot fail.
- (+) The rule degrades honestly. A paginated account keeps the old behaviour rather than showing a
  filtered prefix of the truth.
- (−) `loadedOrigin` and `state.filter` can now differ, and reading either as the other is a bug the
  types do not prevent. Both carry KDoc saying which question they answer.
- (−) One more branch in a ViewModel that already has several. The alternative was a branch in the
  repository, which would have put a UI concern under the transport.

## Confirmation

- `DeviceListViewModelTest.aCompleteListAnswersTheChipWithoutAskingThePartner` — **verified failing**
  against the unfixed code: forcing the guard to `false` makes it, and only it, fail (20 tests, 1
  failed).
- `DeviceListViewModelTest.anIncompleteListStillAsksThePartnerWhenTheChipChanges` — passes before and
  after, on purpose: it guards the over-correction, not the correction.
- `OriginFilterTest.mapsToWireValues` unchanged: the wire values still mean what they meant.
