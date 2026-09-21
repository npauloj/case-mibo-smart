# ADR-015. Slices that share a UI surface stack; everything else branches from `main`

Status: Accepted (2026-09-21) — amends `docs/PROCESS.md` §3

## Context

`docs/PROCESS.md` §3 says, flatly: *"Nunca empilhar sobre trabalho não mesclado."* That rule bought
something real. Every wave cut from an updated `origin/main` means CI tests the tree that will actually
deploy, and the human merge gate stays free: PRs can be reviewed and merged in any order, because none
of them presumes another.

Wave 2 showed where the rule stops paying. Four slices ran in parallel, and three of them came back
with conflicts against `main` — D-01a twice, because L-01a merged in between. The conflicts were not
about disagreement; they were about **adjacency**. Two slices had appended a binding to the same Koin
module, extended the same `strings.xml`, and rewritten the same `SignedIn()` composable.

ADR-014 removed the structural half of that (per-feature Koin modules). What it cannot remove is
genuine surface sharing: **L-01b, L-02 and L-03 all extend `LockScreen` and `LockViewModel`.** They are
three slices of one screen. Dispatching them in parallel from `main` guarantees three conflicts in the
same two files, each resolved by a human reading someone else's half-finished screen.

Resolving those conflicts is not free, and it is not safe either. Wave 2 produced two resolutions that
were wrong on the first pass: one merged two test bodies into a single function (caught by the
compiler) and one declared a Koin binding twice (**not** caught by anything — Koin overrides silently).

## Decision Drivers

- Stop paying for conflicts that exist only because two slices touch the same screen.
- Keep the merge gate's freedom where it matters: independent slices must stay independently mergeable.
- Do not weaken what the CI green light means without saying so.

## Considered Options

1. **Keep §3 as written and resolve the conflicts.** Known cost, and wave 3 has two such groups (the
   lock chain, and video). It also scales the wrong way: the more parallel the wave, the worse it gets.
2. **Sequence instead of stacking** — never dispatch two slices of the same screen in one round. Keeps
   every rule intact and costs a full round-trip through the merge gate per slice, which on a
   three-day case is the expensive resource.
3. **Stack the slices that share a surface**, leaving everything else branching from `main`.

## Decision

Option 3, scoped narrowly.

- **A slice stacks on another only when they edit the same screen or the same platform surface.** The
  lock chain is the first case: `L-01b` from `main`, `L-02` from `slice/l-01b`, `L-03` from
  `slice/l-02`. Video is the same shape and follows when `V-01a` merges (`V-02` on `slice/v-01b`).
- **A stacked edge is written into `Depends on:` like any other**, with the reason stated in the
  ticket. The graph stays explicit data — the `ticket-contract` requires it, and a reader must be able
  to tell a *logical* dependency ("needs the vault") from a *surface* one ("edits the same screen").
- **Slices in different features never stack.** S-02a, D-01b and L-01b touch different screens; they
  branch from `main` and stay independently mergeable.
- The stack is registered with GitHub (`POST /repos/{owner}/{repo}/stacks`, header
  `X-GitHub-Api-Version: 2026-03-10`), as wave 0 already did for `chore/kmp-skeleton` → `arch/konture`.
  Merging the bottom PR rebases the next one onto `main` automatically.
- **Merge order is bottom-up, and the human gate does not move.** Nothing here merges anything.

## Consequences

- (+) Three slices of one screen produce zero conflicts with each other: each starts from the finished
  work below it.
- (+) A reviewer of `L-02` reads a diff against `L-01b`, not against a `main` that lacks half the
  screen. The diff is the change, not the change plus the drift.
- (−) **Review is serialised inside a stack.** `L-02` cannot merge before `L-01b`. If `L-01b` is held
  back on merit — as `D-01a` was, for three hours, over its size — everything above it waits on a
  decision that is not about it. This is the real price, and it is why the rule is scoped to shared
  surfaces instead of applied to whole waves.
- (−) **CI on a stacked PR tests the stack, not `main`.** `L-02`'s green means "L-01b + L-02 work
  together", which is weaker than what a green on `main` means. The PR body must say which base it was
  tested against; a reviewer who reads "green" as "green on `main`" is being misled.
- (−) A change forced into the bottom PR during review invalidates the ones above it, which must be
  rebased. GitHub does the mechanical part; the ones above still need re-reading.
- (−) One more concept in the process, for a case that is three days long.

## Confirmation

- `docs/PROCESS.md` §3 carries the amended rule, so a worker reaching for it finds the exception
  beside the rule rather than in an ADR it may not read.
- Each stacked ticket's `Depends on:` names the slice below it **and** says the edge is a shared
  surface, not a logical dependency. A stacked ticket without that sentence is a ticket the gate
  should send back.
- Each stacked PR body states its base branch and that its CI ran against the stack.
- The rule is observable in the negative too: S-02a, D-01b and L-01b are dispatched in the same round
  from `main`, because they share no surface. A future wave that stacks independent slices is doing it
  wrong.
