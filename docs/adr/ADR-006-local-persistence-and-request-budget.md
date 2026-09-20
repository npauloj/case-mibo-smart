# ADR-006. Minimal local persistence and a request-budget discipline (cache, no polling)

Status: Accepted (2026-09-20)

## Context

"Persistência local" is a named sub-criterion of the technical-knowledge axis, but the case's data
is small. The decisive constraint is the account quota: `/streaming/cota-disponivel/v1` reported a
budget of **~300 requests** for the whole case (value read on 2026-09-20, `docs/api-contract.md` §1.3) — discovery already spent
~17. A naive app (refresh on every screen entry, poll lock status, call `funcoes` per device per
list) would exhaust it before the interview.

Persistence options (KMP survey, Sept 2026): SQLDelight 2.3 (mature, SQL-first) and Room 2.8.x
(stable on KMP, used in production by `open-ani/animeko`); Room 3.0 is a rewrite still in alpha.
`joreilly/PeopleInSpace` and `openMF/kmp-project-template` both use SQLDelight.

## Decision Drivers

- Stay far below the request budget through demo day.
- Cold start and offline degradation for the device list.
- Show local persistence competence without inventing data to store.

## Considered Options

1. No persistence (in-memory only).
2. SQLDelight for a device cache + key-value preferences; token in native secure storage (ADR-008).
3. Room 2.8.x for the same.

## Decision

Option 2 — SQLDelight — plus explicit request-budget rules enforced in use cases:

What is persisted:
- Device list cache (`device` table): last successful page set with `fetchedAt`; shown immediately on
  cold start and when offline (marked "última atualização há …").
- Per-device capability cache (`capabilities` table): result of `funcoes` for cameras, so the check is
  paid once per device.
- Preferences (`preference` key/value): selected origin filter, page size.
- The token itself is **not** in SQLDelight (ADR-008).

Request-budget rules:
- Device list is fetched on explicit user action (pull-to-refresh / "Atualizar") and on first launch,
  never on every navigation.
- Classification by `modelo` first; `funcoes` only for cameras, cached.
- Lock status is read once when the lock screen opens and after a command; no periodic polling.
- Retries are bounded and only for network errors (ADR-002); never retry a `TokenRejected`.
- A debug-only counter of requests made this session is shown in the account screen.

## Consequences

- (+) Demo-safe consumption; realistic offline behaviour to show the panel.
- (+) SQLDelight generates typed queries at build time, works on Android and iOS drivers.
- (−) Cache invalidation logic to write and test; data may be stale between refreshes (by design,
  surfaced to the user with a timestamp).
- Room 2.8.x would have been equally defensible; SQLDelight chosen for SQL transparency and no KSP in
  the shared module.

## Confirmation

- Use-case tests with a fake repository count calls: opening the list twice without refresh triggers
  one network call; opening a camera twice triggers one `funcoes` call.
- A `.sq` schema exists under `:shared:data` with `device`, `capabilities`, `preference` tables and
  migrations.
- Manual: airplane mode after one successful load still shows the list with the staleness label.

## Guardrail

- Not an architecture-test rule: persistence and use-case logic are covered by unit tests whose
  coverage is **measured, not gated** — `./gradlew koverXmlReport koverHtmlReport` (Kover 0.9.9,
  pinned in `gradle/libs.versions.toml`, applied in the PR `arch/konture`) on `:shared:domain` and the
  use cases / ViewModels of `:shared:app`; Compose UI, `*.platform` actuals and generated code are
  excluded. The report is uploaded as a CI artifact and its number goes into `docs/PRODUCT.md` §9.
  *Decided 2026-09-20 (three-specialist review): no `koverVerify minBound(80)` gate in a 3-day build —
  a threshold that must be hit invites test-oracle tampering (AI-LOG). The team's 80 % goal is
  reported honestly, with the per-module breakdown, instead of enforced.*
- The "no polling / no call on a timer" rule (E5) has no static form; it stays a code-review item
  backed by request-counter assertions in every use-case test.
