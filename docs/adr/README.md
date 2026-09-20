# Architecture Decision Records

Living log. One decision per file, Nygard + MADR shape, each with a **Confirmation** section that says
how compliance is checked. New decisions made during implementation are appended with `/adr`.

| ADR | Title | Status |
|---|---|---|
| [ADR-001](ADR-001-module-structure.md) | Module structure: pure domain, one data module, one shared app module, thin platform apps | Accepted |
| [ADR-002](ADR-002-errors-as-values.md) | Errors are values; each use case defines its own result type | Accepted |
| [ADR-003](ADR-003-presentation-single-state.md) | MVVM with a single screen state; intents are suspend functions, not a second stream | Accepted |
| [ADR-004](ADR-004-partner-agnostic-domain.md) | Partner-agnostic domain; the partner contract lives only in `:shared:data` | Accepted |
| [ADR-005](ADR-005-live-video-native-players.md) | Live video via `expect/actual` native players (fMP4), WebView on `monitor_url` as fallback | Accepted |
| [ADR-006](ADR-006-local-persistence-and-request-budget.md) | Minimal local persistence and a request-budget discipline (cache, no polling) | Accepted |
| [ADR-007](ADR-007-java-interop-module.md) | Honest Java interop: one small Java module consumed from Kotlin | Proposed |
| [ADR-008](ADR-008-token-security.md) | Token security: native secure storage, redacted logs, nothing versioned | Accepted |
| [ADR-009](ADR-009-architecture-tests.md) | Architecture tests with Konture in `:konture-test`, first job of CI; Konsist as plan B | Accepted |

Each of ADR-001..008 ends with a **Guardrail** section naming the architecture-test rule (numbered 1–11
in ADR-009) that makes the decision executable; ADR-006 is covered by the Kover gate instead.

Research that informed these decisions (all from the preparation phase, 2026-09-19/20):
- Petros Efthymiou, *Clean Mobile Architecture* (2022) — cited by page.
- KMP state-of-the-art survey (Sept 2026): Kotlin 2.4.x, Compose Multiplatform 1.11.x, Ktor 3.5.x,
  Koin 4.2.x vs Metro 1.0, SQLDelight 2.3 vs Room 2.8 (stable KMP) vs Room 3.0 (alpha).
- Reference repositories: `openMF/kmp-project-template`, `joreilly/PeopleInSpace`,
  `VasilyRylov/kmp-architecture-samples`, `open-ani/animeko`.
- The API contract as observed: [`../api-contract.md`](../api-contract.md).
- Architecture-test tooling survey (2026-09-20): Konture 0.8.4 vs Konsist 0.17.3 vs ArchUnit vs detekt;
  Bao Le's three-part series (ProAndroidDev, July 2026); Ford/Parsons/Kua fitness functions.
- SDD for mobile/KMP survey (2026-09-20): DroidKaigi 2026 "Making UI specifications visible" (previews
  and screenshots as UI spec), Android AGENTS.md conventions, KMP testing guidance 2025
  (`state.value` over Turbine for `StateFlow`), SpecBench 2026 (test-oracle tampering).
