# ADR-019. Stacking is the default for the remaining slices

Status: Accepted (2026-09-21) — supersedes the scope of [ADR-015](ADR-015-stack-slices-that-share-a-ui-surface.md)

## Context

ADR-015 allowed stacking as a narrow exception: only for slices editing the same screen or platform
surface. Everything else branched from `main`, so the merge gate could take PRs in any order and CI
could test the tree that deploys.

That scope was drawn from one wave of evidence. Nine waves later the evidence says something else:
**this session has merged `main` into a slice branch ten times.** Not once for a disagreement — every
one was an append to the same handful of files:

- `AI-LOG.md` — every slice adds a row, because `CLAUDE.md` requires it.
- `strings.xml` (pt + en) — every slice with a screen adds strings, because SPEC E6 requires it.
- `App.kt` and `AppPreviews.kt` — every slice that adds a destination edits the same `when` inside
  `SignedIn()`, because navigation is an `if/else` over remembered state rather than a nav graph.

ADR-014 removed the DI half of this by giving each feature its own Koin module. The three above cannot
be removed the same way inside this case: the AI log is a single document on purpose (it is written for
the evaluation panel), the two `strings.xml` are what Compose resources are, and replacing the
navigation with a real graph is a refactor that would itself collide with every in-flight slice.

The cost is not only time. Two of those ten resolutions were **wrong on the first pass**: one merged
two test bodies into a single function, caught by the compiler; the other declared a Koin binding
twice, which **nothing** catches — Koin overrides silently. A mechanical "keep both sides" is safe in a
Markdown table and unsafe in code, and the person doing it is reading someone else's half-finished
screen.

There is a subtler cost this ADR is being written partly to record. A conflicting PR has no CI at all:
GitHub cannot build the merge ref, so a `pull_request`-triggered workflow never starts and the PR
reports "no checks reported" — which reads like *pending* and is really *never asked*. Conflict does
not just delay the merge; it silences the quality signal while looking like patience.

Five slices remain: `L-02`, `L-03`, `V-02`, `S-03`, `P-01`. Three of them would dispatch in parallel
and all three touch `App.kt`.

## Decision Drivers

- Stop paying, three more times, for a conflict class that is fully predictable.
- Keep the human merge gate: nothing here merges anything.
- Be honest that the alternative has a price, and say what it is.

## Considered Options

1. **Keep ADR-015's narrow scope.** It is already being stretched: "shares a screen" now describes
   nearly every remaining slice, so the exception has quietly become the rule without anyone writing
   it down. That is the worst of both — the cost of stacking with none of the clarity.
2. **Refactor navigation into per-feature destinations first**, the way ADR-014 did for DI. The right
   fix for a longer project. Here it is a refactor of the exact file every in-flight slice is editing,
   so it collides with the work it exists to protect, and it buys nothing for `AI-LOG.md` or
   `strings.xml`.
3. **Stack by default for the remaining slices.**

## Decision

Option 3, for the rest of this case.

- **Every remaining slice branches from the slice below it**, not from `main`. The order follows the
  dependency graph, with ties broken by risk: `S-03` → `V-02` → `L-02` → `L-03` → `P-01`.
- **One slice is dispatched at a time.** The stack is the sequence; there is no parallel set left worth
  the conflicts it would cause.
- Each stacked PR states its base branch and that **its CI ran against the stack, not against `main`**.
  A reviewer who reads "green" as "green on `main`" is being misled, and the PR body must not allow it.
- Merge order is **bottom-up**, and the human gate does not move. Nothing here merges anything.
- The stack is registered with GitHub (`POST /repos/{owner}/{repo}/stacks`), so merging the bottom PR
  rebases the next one automatically.
- **ADR-015's rule is not deleted, it is widened.** If this project continued past the case, the right
  move is option 2 — per-feature navigation destinations — and then stacking returns to being the
  narrow exception ADR-015 described.

## Consequences

- (+) The conflict class ends. A slice starts from the finished work below it, so `App.kt`,
  `strings.xml` and `AI-LOG.md` are already whatever the previous slice made them.
- (+) A reviewer reads a diff of the change, not the change plus the drift.
- (+) No more "no checks reported" PRs: nothing conflicts, so CI always runs.
- (−) **Review serialises completely.** `L-03` cannot merge before `L-02`. Holding one PR on merit —
  as happened with `D-01a`, for three hours, over its size — stalls everything above it. This is the
  real price and it is why ADR-015 refused to pay it wave-wide; with five slices left and three of
  them already chained by dependency, the bill is now small.
- (−) **A green check means less.** It certifies the stack, not `main`. The final PR is the only one
  whose CI is equivalent to a `main` build.
- (−) A change forced into a lower PR during review invalidates the ones above. GitHub rebases them;
  a human still has to re-read them.
- (−) Wall-clock goes up: five slices in sequence instead of three in parallel then two. At roughly
  50 minutes each that is about four hours, against a Thursday deadline — affordable, and it was
  measured before choosing.

## Confirmation

- `docs/PROCESS.md` §3 carries the widened rule, so a worker finds it beside the branch convention.
- Every remaining ticket's `Depends on:` names the slice below it in the stack and says whether the
  edge is logical or ordering-only, as ADR-015 already requires.
- Every stacked PR body states its base and what its CI actually proved.
- The negative check: if a future wave dispatches two slices from `main` in parallel and they conflict
  in `App.kt`, this ADR was ignored.
