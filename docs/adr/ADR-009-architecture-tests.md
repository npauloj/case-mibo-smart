# ADR-009. Architecture tests with Konture in a dedicated `:konture-test` module, first job of CI

Status: Accepted (2026-09-20)

## Context

The evaluating team stated two goals in the interview: keep the architecture intact over time, and reach
high test coverage (80%). ADR-001..008 fix the structure — dependencies point inward, the domain knows
no framework, the partner contract lives only in `:shared:data`, features are isolated packages inside
`:shared:app`, `expect/actual` stays inside packages named `platform`. None of that is enforced by the compiler:
Gradle accepts a new `implementation(project(...))` edge, and inside one module any package can import
any other. The rules only survive as long as reviewers remember them — and an AI-assisted build makes
"the agent added a dependency so the file would compile" a documented failure mode (Bao Le, *Kotlin
Architecture Tests: What They Are and Why They Matter*, ProAndroidDev, 2026-07-15; SpecBench 2026 on
test-oracle tampering).

Architecture tests turn those decisions into *fitness functions* (Ford, Parsons, Kua, *Building
Evolutionary Architectures*): objective checks that protect a structural characteristic over time and
fail the build with a module, file and line.

Tooling surveyed (2026-09-20):

- **Konture 0.8.4** (github.com/baole/konture, Apache-2.0, created 2026-07-10, 34★, one maintainer,
  6 releases in August 2026). Two-view model: a Gradle plugin captures the real build topology
  (modules, source sets, declared dependencies) into `layout_v2.json`; the test library parses Kotlin
  source with PSI and evaluates rules as ordinary JUnit tests (`docs/how_it_works.md`). First-class
  KMP DSL: `sourceSet("commonMain") { mustBePlatformIndependent() }` (`docs/recipes/source-sets.md`),
  `layered {}` (`recipes/layer-isolation.md`), `slices {}` and `assertNoCycles()`
  (`recipes/slices-and-modularization.md`), `files { notCall(...) }`, baselines with ratchet mode.
  Declares AGP 9.x support and Configuration Cache / Project Isolation compatibility.
- **Konsist** (LemonAppDev): the established alternative, ~1.7k★, Kotlin Foundation grant 2024 — but
  the last release (0.17.3) is from December 2024 and `main` has a single commit in 2026. Source-only
  view (a folder is not a Gradle module).
- **ArchUnit**: JVM bytecode; cannot see iOS/Native targets and trips on Kotlin constructs.
- **detekt**: the right tool for control-flow rules (`println`, swallowed `CancellationException`),
  not for module graphs.
- **Gradle `api`/`implementation`**: free first barrier, but cannot stop `devices/` from importing
  `lock/` inside the same module.

## Decision Drivers

- The rules must fail the build, not a checklist.
- Cheap to run on every push (JVM only, no emulator, no simulator).
- Small enough to migrate to another tool in an afternoon if the chosen one is abandoned.
- Must understand KMP source sets and the Gradle graph, not only packages.

## Considered Options

1. Convention + code review only.
2. Konsist in a dedicated JVM test module.
3. Konture in a dedicated JVM test module, Konsist as the fallback.
4. ArchUnit.

## Decision

Option 3.

- A Kotlin/JVM module `:konture-test` (JUnit 5, `testImplementation("io.github.baole:konture:0.8.4")`,
  plugin `io.github.baole.konture` applied in `settings.gradle.kts`). It does not depend on the
  production modules; the plugin publishes the layout artifact to it.
- `./gradlew :konture-test:test` is the **first job of CI** (stage "verify", before unit tests and
  before any Android/iOS job) — it is the cheapest gate and fails fastest.
- The suite is **one rule per ADR decision**, eleven rules, numbered and referenced from each ADR's
  "Guardrail" section: 1 domain purity · 2 inward dependency direction + no cycles · 3 feature slices
  isolated · 4 `commonMain` platform-independent · 5 `*Repository` in domain are interfaces ·
  6 `*ViewModel` only in `:shared:app`, exposing `StateFlow` · 7 `@Serializable`/DTOs only in
  `:shared:data` · 8 `expect/actual` only in packages named `platform` (`data.platform.*` for the token
  vault, `app.<feature>.platform` for the video surface — ADR-001/005/008) · 9 no `println` · 10 (detekt)
  `SuspendFunSwallowedCancellation` · 11 every stateless `*Content` composable has a preview.
- Tool versions are pinned in `gradle/libs.versions.toml` from wave 0 (Konture 0.8.4, Kover 0.9.9,
  detekt `dev.detekt` 2.0.0-alpha.6 — the 2.x line is the one built against Kotlin 2.4 / AGP 9); the
  plugins are applied by the PR `arch/konture`. **Until that PR merges the CI step prints a visible
  warning instead of silently skipping**; the PR removes the warning branch so the gate can never be
  "off" again without a diff in `ci.yml`.
- **Every rule must have been seen failing once**: before a rule is merged, a deliberate violation is
  introduced, the failure output is captured in the PR description, and the violation reverted. The
  suite keeps one permanent negative test (a deliberately wrong module rule asserted to throw
  `AssertionError`), as the Konture showcase does.
- No baseline: the project is greenfield, the suite is born green. Ratchet mode is irrelevant until a
  baseline exists.
- Rules 9 and 10 belong to detekt (`ForbiddenMethodCall`, `SuspendFunSwallowedCancellation`) and run in
  the same CI stage; architecture tests are not a second linter.
- Konsist is the named plan B: the eleven rules are small, declarative and portable; if Konture stops
  tracking Kotlin/AGP, porting them is an afternoon, recorded as a new ADR.

## Consequences

- (+) ADR-001..008 become executable; a reviewer (human or agent) gets the same failure with module,
  file and line.
- (+) Directly answers the team's "keep the architecture" goal with something demonstrable in the
  20-minute walkthrough (show a rule, show it failing, show it green).
- (+) Coverage and structure are separate gates and are presented as such: Kover measures execution,
  Konture measures shape.
- (−) Maturity risk: Konture is two months old with a single maintainer and a 0.x API. Contained by
  the small, portable rule set and the named fallback.
- (−) Source-level dependency resolution is heuristic (documented "99%+"); ambiguous simple names
  across packages could misattribute an edge — rules use explicit packages to avoid it.
- (−) One more Gradle module and one more plugin in `settings.gradle.kts`.
- Deliberately not done: rules about formatting, naming beyond `Repository`/`ViewModel`/`UseCase`, or
  anything detekt/ktlint already covers.

## Confirmation

- `./gradlew :konture-test:test` passes on `main` and runs first in `.github/workflows/ci.yml`,
  **unconditionally** once `konture-test/` exists (no `if:` guard left in the workflow).
- A 2-hour spike validated the plugin against the AGP 9 `androidLibrary {}` modules before any rule was
  promised (open until done — see AI-LOG).
- Each ADR-001..008 has a "Guardrail" section naming its rule(s); each rule's test name matches.
- The PR that introduces each rule contains the captured failure output of the intentional violation.
- Konture and its plugin share one version in `gradle/libs.versions.toml`; the schema-version check in
  `layout_v2.json` fails loudly on a mismatch.

## Sources

- github.com/baole/konture — README, `docs/how_it_works.md`, `docs/installation.md`, `docs/usage.md`,
  `docs/recipes/source-sets.md`, `docs/recipes/layer-isolation.md`,
  `docs/recipes/slices-and-modularization.md`, `docs/baseline.md` (read 2026-09-20).
- Bao Le, *Kotlin Architecture Tests: What They Are and Why They Matter — Part 1/3* (ProAndroidDev,
  2026-07-15); *Why Konture Exists — Part 2/3*; *A Practical Guide — Part 3/3* (mirrored in the
  repository under `docs/articles/`).
- Neal Ford, Rebecca Parsons, Patrick Kua — *Building Evolutionary Architectures* (O'Reilly): fitness
  functions.
- LemonAppDev/konsist — releases and project status (read 2026-09-20).
- SpecBench: *Measuring Reward Hacking in Long-Horizon Coding Agents* (arXiv 2605.21384, 2026).
