# ADR-014. One Koin module per feature, beside the code it wires

Status: Accepted (2026-09-21)

## Context

`:shared:data/di/DataModule.kt` and `:shared:app/di/AppModules.kt` each held every binding in their
module. That made both files something this process cannot afford: **a line-level rendezvous point for
slices that are supposed to be independent.**

Wave 2 dispatched four slices in parallel. Two of them — D-01a and L-01a — came back with the same
conflict against `main`, in the same file, for the same reason: each had appended a binding to
`DataModule.kt` while S-01b had changed the line immediately above it (swapping `InMemorySessionStore`
for `VaultSessionStore`). Both PRs went `CONFLICTING/DIRTY` and both needed a manual resolution whose
answer was always "keep everything".

That is not a hard conflict. It is a *pointless* one, and it scales with the thing this process is
built on: wave 3 has seven slices at the same depth, every one of which adds at least one binding.

`docs/PROCESS.md` §2 says slices in a wave are independent and can be implemented in parallel. A file
every slice must edit is a standing exception to that claim.

The same file is also a small architectural smell. Architecture rule 3 (ADR-009) forbids feature
packages from importing each other, yet `DataModule.kt` imported `session`, `device` and `lock`
implementations into one place — the one spot where every feature meets.

## Decision Drivers

- Remove the *content* conflict: two slices adding bindings should not touch the same lines.
- Keep the composition root readable — a reviewer must still see what the graph contains.
- No new dependency, no code generation, no reflection (`CLAUDE.md`: a dependency needs an ADR first,
  and Koin's annotation processor would add KSP to the shared module).

## Considered Options

1. **Leave it and resolve the conflict each wave.** ~30 s per slice, every wave, forever, and each
   resolution is a chance to drop a binding by accident. It also leaves the rule-3 smell.
2. **Koin Annotations (KSP).** Generates the module graph; no shared file at all. Adds KSP to
   `:shared:data` and `:shared:app`, a new dependency and a new build step, to solve a merge problem.
   Rejected as disproportionate.
3. **One module per feature, aggregated by a list.** Each feature owns a `<feature>DataModule.kt` /
   `<feature>AppModule.kt` next to its code; the aggregate file lists them.

## Decision

Option 3.

- A feature's bindings live in **its own package**, in a file named `<feature>DataModule.kt`
  (`:shared:data`) or `<feature>AppModule.kt` (`:shared:app`), declaring an `internal val`.
- `di/DataModule.kt` keeps only what **every** feature shares — the HTTP client, the JSON, the envelope
  reader, the API — and `di/AppModules.kt` keeps only what belongs to no feature, today `AppViewModel`,
  which exists to route *between* them.
- Both aggregates end with a `private val featureModules: List<Module>`, **one entry per line,
  alphabetical**, passed to `includes(...)`.
- A slice that adds a feature creates its file and appends one line to that list.

**What this does and does not buy.** It removes the conflict that actually hurt: two slices editing
adjacent binding lines, interleaved with someone else's unrelated change. What remains is a one-line
append to a sorted list — git may still flag it when two slices append at once, but the resolution is
mechanically "keep both lines, keep them sorted", with no code to read. Claiming it reaches zero
conflicts would be wrong, and the ticket that says so would be lying to the next worker.

## Consequences

- (+) Feature slices stop meeting in a shared file's body. Adding a feature is a new file plus one
  sorted line.
- (+) Bindings sit next to the code they wire, so a reader finds them where they are looking, and
  `DataModule.kt` no longer imports every feature's implementation.
- (+) No dependency, no KSP, no build-time cost. The refactor is 4 files and no behaviour change.
- (−) Two files per feature instead of one line in a shared file — more files, each trivial.
- (−) The aggregate list is manual: a feature module that exists but is never listed is silently
  absent from the graph. `AppModulesTest` resolves the real graph and is the guard; a feature whose
  ViewModel is not resolvable fails there.
- (−) Landing this requires the two open slice PRs to merge first, or it conflicts with them in the
  exact file it exists to stop conflicting on.

## Confirmation

- `AppModulesTest` starts the real Koin graph and resolves the entry points; it is the test that
  catches an unlisted module. Every feature slice extends it with its own entry point.
- Review check, mechanical: a slice that adds a binding to `di/DataModule.kt` or `di/AppModules.kt`
  outside the `featureModules` list is doing it wrong, unless what it adds genuinely belongs to no
  feature.
- `shared/CLAUDE.md` states the convention, so it reaches a worker without reading this ADR.
