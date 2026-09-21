# ADR-017. The PR ceiling bounds production code; a ticket's estimate is not a stop instruction

Status: Accepted (2026-09-21) — amends [ADR-011](ADR-011-pr-budget-measured-in-executable-lines.md)

## Context

ADR-011 set the PR budget at ~400 **executable** lines, tests included, and every slice ticket since
has carried a line like *"stop and return blocked past ~360 executable"*, described in the ticket as
an instruction rather than advice.

Four consecutive slices — S-01a, L-01a, D-01a, V-01a — blew that threshold and shipped anyway. None
of the four workers was careless about it: each measured, declared both counts in the PR body, and
logged the overrun in `AI-LOG.md`. The V-01a worker argued the position explicitly, and correctly:
ADR-011 §Confirmation says *"a PR whose executable count exceeds its ticket's stop threshold is a
merge-gate decision, not an automatic pass"*, which is not the same thing as "stop".

So the process said two incompatible things, and the workers picked the one written in an ADR over
the one written in a ticket. **A rule that four agents in a row decline to follow is not being
violated; it is being disproved.**

Two facts make this measurable rather than a matter of taste.

**First, the numbers.** `tools/executable-lines.py` — written for this ADR, and matching the workers'
hand counts to within a few lines — over every merged slice:

| Slice | Executable production | Executable test | Ticket estimate | Actual ÷ estimate |
|---|---|---|---|---|
| S-01b (vault) | 122 | 44 | 180 | 0.9× |
| S-01c (mask + format) | 100 | 138 | 250 | 1.0× |
| S-01a (token screen + transport) | 308 | 301 | 400 | 1.5× |
| L-01a (lock reads) | 379 | 312 | 300 | 2.3× |
| D-01a (device list) | 430 | 384 | 340 | 2.4× |
| V-01a (live video) | 455 | 397 | 360 | 2.4× |

Read down the production column: **a vertical slice that delivers a screen costs 380–455 executable
production lines in this codebase, every time.** The consistency is the finding. The estimates were
not noisy — they were wrong in one direction, by about half, and only for that kind of slice. Narrow
slices (S-01b, S-01c) were estimated accurately.

**Second, what the ceiling is for.** The ~400 figure comes from review research on *code*: the point
past which a reviewer stops finding defects. Tests are read differently — arrange, act, assert,
repeated — and their volume tracks the number of acceptance criteria, not the difficulty of the
change. Counting them inside a 400-line ceiling means a slice with thorough tests is penalised
exactly like a slice with sprawling logic, and it makes a screen slice a violation **by construction**:
400 production + 350 test cannot fit under 400.

## Decision Drivers

- Stop shipping a rule that the work has disproved four times.
- Keep a real bound on what a reviewer has to hold in their head.
- Never create an incentive to write fewer tests.
- Make the number mechanical, so the worker's count and the reviewer's count are the same count.

## Considered Options

1. **Enforce the stop literally** — a worker that passes the threshold returns `blocked`. It asks for
   the one action whose cost exceeds the violation: discarding finished, tested, passing code, usually
   discovered only at the end. It is also unenforceable mid-flight, since the size is not known until
   the slice is written.
2. **Halve every slice.** Would fit the ceiling, at the cost of doubling the number of PRs and human
   merge gates. The gate already refused this shape once, rejecting a proposed split of S-01a with
   *"Do NOT split transport from the UI"* — past a point, splitting a screen produces the foundation
   tickets the `ticket-contract` forbids.
3. **Bound production, require and report tests, and calibrate the estimates from the measured data.**

## Decision

Option 3.

- **The ceiling is ~400 executable *production* lines.** Tests are still counted and still reported —
  both numbers go in the PR body — but they are **not capped**. They cannot be dropped to buy room
  either: every test is named by an acceptance criterion in the ticket, so the criteria bound them,
  not a budget.
- **A ticket's `Size:` line is an estimate, not a stop instruction.** The wording "stop and return
  blocked past N" is removed from every ticket.
- **The block condition is about *origin*, not size.** A worker returns `blocked` when the extra work
  comes from **scope the ticket did not name** — that is knowable while writing, and stopping is
  cheap. When the overrun comes from the ticket's *own* named scope, the ticket was mis-estimated:
  ship it, declare both numbers, and record it as a **ticket defect**, not a worker defect. That is
  precisely what the four workers did, and this ADR makes them right.
- **Estimates start from the measured data**, not from instinct:

  | Slice shape | Executable production | Executable test |
  |---|---|---|
  | Feature screen (list, lock, camera, session) | ≈ 400 | ≈ 350 |
  | Narrow slice (a store, a mask, one endpoint) | ≈ 120 | ≈ 100 |

  A ticket whose honest estimate exceeds ~400 production is **too big, and the gate should say so
  before dispatch** — that is where splitting is cheap, and it is the only place a size rule can be
  enforced without throwing away work.
- **The number is produced by `tools/executable-lines.py <base>..<head>`**, not by hand. Its output
  goes in the PR body.

## Consequences

- (+) The process says one thing. A worker no longer has to choose which rule to disobey.
- (−) A slice can now legitimately land at ~800 total added executable lines. That is a real diff to
  read, and the mitigation is only partial: roughly half of it is test code, which reads faster.
- (+) The incentive is right: nothing is gained by writing fewer tests, and the block condition now
  fires on the thing that actually deserves it — an agent widening its own scope.
- (+) Size moves upstream, to ticket authoring, where a split costs nothing.
- (−) The four overruns already recorded in `AI-LOG.md` were logged under the old rule. They stay:
  they are the evidence this ADR is built on, and rewriting them would erase the reason.
- (−) One more script to keep working. It is 60 lines and has no dependencies.

## Confirmation

- `docs/PROCESS.md` §2 and §6 carry the production-only ceiling and the origin-based block condition.
- Every ticket's `Size:` line gives a production estimate and a test estimate from the calibration
  table, and no ticket says "stop and return blocked past N lines" any more.
- Every slice PR body contains the script's output. A PR that quotes a hand count instead is a PR
  whose number nobody can reproduce.
- The measurement is re-run at the end of the case: if feature-screen slices keep landing near 400
  production, the calibration holds; if they drift, this table is the thing to update, not the
  tickets one at a time.
