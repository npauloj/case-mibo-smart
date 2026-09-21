# ADR-011. The PR budget counts executable lines, not diff lines

Status: Accepted (2026-09-21)

## Context

`docs/PROCESS.md` §2 asks two things of every slice at once:

1. it is **vertical** — it crosses `domain → data → app → tela` and delivers something observable;
2. it fits a **reviewable PR, ≤ ~400 linhas de mudança**.

In this repository those two rules cannot both hold, and the evidence is not theoretical:

| Slice | Budget in the ticket | Delivered | Executable code |
|---|---|---|---|
| S-01a (#26) | ~400 total | **1440** insertions / 38 files | ~348 production + ~451 test |
| S-01c (#29) | ~250 total | **449** insertions / 14 files | ~110 production + ~140 test |

Both overran; in both the independent review confirmed that the *executable* code was inside budget and
found **no speculative generality** — exactly four error subtypes in S-01a, DTO fields carrying only what
the call needs. What overflowed was everything a vertical slice in this project is *required* to carry:

- **KDoc** on every declaration — the density the existing files set, and the thing a reviewer of a
  four-module KMP codebase actually reads to navigate it;
- **`import` lines**, multiplied by the module count (S-01a: 128 lines of imports alone);
- **user-facing strings in two locales**, mandated by SPEC E6 (pt-BR default + en fallback);
- **previews**, mandated by architecture rule 11 (ADR-009) — a `*Content` composable without one fails
  the build, and previews are this case's only visual evidence, as screenshot tests are out of scope.

None of those are the thing the ~400-line convention protects against. That number comes from review
research on **code**: the point past which a reviewer stops finding defects. A hundred lines of `import`
and pt/en `<string>` entries do not consume review attention the way a hundred lines of branching logic
do — but they do consume the budget, and the result was five of six wave-2 tickets rejected at the
pre-dispatch gate for size while their content was sound.

Left unchanged, the rule punishes the slices that obey the repository's other rules hardest.

## Decision Drivers

- Keep the protection the number was bought for: a diff a human can actually review in one sitting.
- Stop rejecting tickets for carrying the KDoc, locales and previews this repo mandates elsewhere.
- Keep the measure mechanical, so the gate and the worker compute the same number.
- Do not weaken it into a rubber stamp.

## Considered Options

1. **Keep ~400 raw diff lines and split slices further.** Faithful to the text. Turns 5 slices into ~10,
   doubling the number of PRs and of human merge gates inside a three-day case, and pushes toward
   horizontal splits — the "foundation ticket" the `ticket-contract` forbids. The gate itself warned of
   this when it rejected S-01a's proposed split: *"Do NOT split transport from the UI."*
2. **Raise the ceiling to ~800 raw diff lines.** One number, no new concept. But 800 lines of executable
   code is genuinely unreviewable, so the ceiling would stop protecting anything.
3. **Count executable lines.** Keep ~400, change what is counted.

## Decision

Option 3. The PR budget is **~400 executable lines**, defined mechanically as added lines minus:

- comment and KDoc lines,
- `import` lines and the `package` line,
- blank lines,
- resource files (`composeResources/**/strings.xml`), `.sq` schema files and build/catalog files,
- `@Preview` functions and `PreviewParameterProvider` bodies.

Tests **count** — a slice is not allowed to buy room by shipping fewer of them.

Consequences for the artefacts:

- Every ticket's **`Size:`** line states both numbers: the executable budget and the expected raw diff,
  plus the hard "stop and return blocked past N" threshold, expressed in **executable** lines.
- `docs/PROCESS.md` §2 and §6 carry the same definition, so the gate, the worker and the human reviewer
  compute one number.
- The PR body reports both counts under Evidência, so the reviewer sees what was measured. `gh pr view`
  still shows the raw number, and that is fine — it is no longer the gate.

## Consequences

- (+) A vertical slice can satisfy rule 1 and rule 2 at the same time. The gate stops rejecting sound
  tickets, and the rejections that remain are about content — the ones that matter, such as L-01's
  untested write to a physical lock's security posture or V-01's dependency in the wrong module.
- (+) The measure cannot be gamed in the direction that hurts: stripping KDoc, locales or previews to fit
  is now pointless, and dropping tests is explicitly excluded.
- (−) Two numbers instead of one, and a definition to apply consistently. Mitigated by keeping the list
  mechanical and short.
- (−) A raw diff of ~1000 lines still *looks* alarming in the GitHub UI. The PR body must state both
  counts, or the reviewer is surprised.
- (−) This ADR changes a rule under which two PRs (#26, #29) already merged or opened as overruns. They
  are **not** retroactively compliant: both are recorded in `AI-LOG.md` as overruns of the rule that was
  in force when they shipped, and that record stands.

## Confirmation

- `docs/PROCESS.md` §2 states the same definition; a future change to one without the other is a
  documentation defect the next reviewer should catch.
- Every ticket emitted from `docs/specs/issues.md` carries a `Size:` line in executable lines with an
  explicit stop threshold; the pre-dispatch gate rejects a slice that has none.
- Each slice PR reports executable and raw counts under Evidência. A PR whose executable count exceeds
  its ticket's stop threshold is a merge-gate decision, not an automatic pass.
