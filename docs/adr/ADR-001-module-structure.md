# ADR-001. Module structure: pure domain, one data module, one shared app module, thin platform apps

Status: Accepted (2026-09-20)

## Context

The case is evaluated on "separação entre UI, domínio e dados; modularização; baixo acoplamento;
suporte a múltiplos parceiros; escalabilidade" (20% of the score) and on the ★★★ item "Kotlin/KMP".
The delivery window is ~3.5 days for one developer, so every extra Gradle module is paid for in build
time and ceremony. Two respected reference layouts pull in opposite directions:

- `joreilly/PeopleInSpace` — a single `:common` module with soft package boundaries.
- `openMF/kmp-project-template` — Clean Architecture with `core/domain`, `core/data`, `core/network`,
  `feature/*` as separate Gradle modules.
- `VasilyRylov/kmp-architecture-samples` — one module per feature *and* per layer (overkill here).

Efthymiou (*Clean Mobile Architecture*, p. 244) argues that "an app developed by a couple of engineers
does not benefit from borders" and recommends soft (package) boundaries for small teams; p. 190:
"don't use a cannon to kill a mosquito".

## Decision Drivers

- The rubric explicitly names modularization and multi-partner support — a soft-boundary single
  module makes that argument harder to demonstrate in a 20-minute walkthrough.
- Build time and context size for an AI-assisted, time-boxed build.
- iOS must consume the same shared code (proof that "KMP" is real, not a label).

## Considered Options

1. Single `:shared` module, package boundaries only (PeopleInSpace style).
2. Middle ground: `:shared:domain` (no dependencies) ← `:shared:data` ← `:shared:app` (use cases,
   ViewModels, Compose UI by feature package), plus `:androidApp`, `iosApp`, and a small Java module.
3. Full Clean Architecture per feature (`feature/x/{domain,data,ui}` modules).

## Decision

Option 2. Three shared Gradle modules with dependencies pointing inward:

```
:shared:domain   models, repository contracts, domain errors, result types — depends on nothing
:shared:data     partner (Open Casa Inteligente) implementation: Ktor client, DTO→domain mapping, envelope parsing,
                 local cache (SQLDelight); expect/actual in `data.platform.*`
                 (token vault: Keystore in androidMain, Keychain in iosMain)   — depends on :domain
:shared:app      use cases, ViewModels, Compose Multiplatform UI (package per feature), Koin
                 modules aggregated by composition; expect/actual in `app.<feature>.platform`
                 (live video surface: Media3 in androidMain, WKWebView in iosMain) — depends on :domain, :data
:androidApp      Activity, Application (Koin start), manifest — no `actual`, no logic
iosApp           SwiftUI host, Koin start — Swift only; a Swift target can never host a Kotlin `actual`
:legacy-catalog  small Java module (ADR-007), consumed by :shared:data on the JVM/Android side
```

`:shared:domain` being dependency-free is the concrete answer to "suporte a múltiplos parceiros": a
second partner is a second data module implementing the same contracts. Feature packages inside
`:shared:app` keep the option of splitting into feature modules later without changing the layering.

**Where `expect/actual` lives — and why not in the platform apps.** An `expect` and its `actual` must
sit in the same Gradle module (different source sets of it); `:androidApp` and `iosApp` are consumers of
`:shared:app`, so they cannot provide actuals for it, and `iosApp` is Swift. Therefore every platform
bridge lives inside the shared module that owns the abstraction, in a package named `platform`:
`data.platform.vault` for the token store (ADR-008) and `app.camera.platform` for the video surface
(ADR-005). Architecture rule 8 (ADR-009) enforces exactly this set of packages. *(Corrected 2026-09-20
after the three-specialist review: the first draft of this table put the actuals in the platform apps,
which does not compile.)*

## Consequences

- (+) Layering is enforced by Gradle, not by convention — the compiler rejects UI→Ktor imports.
- (+) Each module has its own Koin module; the app aggregates them (`dataModule` ← `appModule`).
- (−) Three modules to keep compiling on Android and iOS; more `build.gradle.kts` to maintain.
- (−) Slightly more cross-module boilerplate (interfaces in domain, implementations in data).
- Deliberately not done: per-feature modules, Decompose/FSM navigation, BFF/Delegate pattern
  (`jdagnogo/kmp-architecture-skill`) — they solve problems this case does not have.

## Confirmation

- `:shared:domain/build.gradle.kts` declares no `implementation(...)` on Ktor, SQLDelight, Koin or
  Compose; `./gradlew :shared:domain:dependencies --configuration commonMainImplementation` lists only
  kotlin stdlib, coroutines and serialization.
- `:shared:app` never imports `io.ktor.*` — checked by a grep in CI (`rg "import io.ktor" shared/app`
  must return nothing).
- The iOS app compiles against the `Shared` framework produced from `:shared:app`.

## Guardrail

Architecture tests (ADR-009) make this decision executable:
- Guardrail: rule 1 — `domain must not depend on frameworks, persistence or the outer modules` in `:konture-test`
- Guardrail: rule 2 — `dependencies only point inward` + `module graph must not contain cycles` in `:konture-test`
- Guardrail: rule 3 — `feature packages inside shared-app stay isolated` in `:konture-test`
