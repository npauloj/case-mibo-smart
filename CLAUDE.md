# Project: case-mibo-smart

Kotlin Multiplatform (Android + iOS) app for a smart-home technical case on the partner's "Open Casa Inteligente" platform:
enter an access token, list the account's devices (paged, filtered by origin), watch a camera's
live video, and manage a smart lock (open/close/status/volume/opening history).

## How to work here

This project uses the **interpret → references → execute** pipeline. For any non-trivial
task:

1. `/interpret <task>` — produces the Problem Brief (edits nothing)
2. `/references` — gathers best practices for the research agenda
3. `/execute` — implements and verifies

Or, automatically: ask for the `plan-research-execute` workflow.

Spec-first chain for features: `docs/specs/SPEC.md` → `/to-issues --publish` → `/orchestrate` → `/review`.
Every structural decision has an ADR in `docs/adr/`; AI-assisted work is logged in `AI-LOG.md`.

Artifacts live in `.claude/` (versioned, shared with the team) and override the global
ones of the same name.

## Stack / conventions

- Kotlin 2.4 / Compose Multiplatform 1.11 / Ktor 3.5 / Koin 4.2 / SQLDelight 2.3 / coroutines + Flow.
- Modules: `:shared:domain` (pure, no deps) ← `:shared:data` (Ktor client, mappers, persistence)
  ← `:shared:app` (use cases, ViewModels, Compose UI; builds the iOS `Shared` framework)
  ← `:androidApp` / `iosApp`. Dependencies only point inward (see ADR-001).
- Package root: `io.github.npauloj.mibosmart`. Identifiers in English; API field names live only in
  `@SerialName` inside `:shared:data`.
- Errors are values: `:shared:data` maps transport/API failures to typed domain errors; each use case
  returns its own sealed result (ADR-002). Every `catch (Throwable)` rethrows `CancellationException`.
- UI: one `StateFlow<State>` per screen, intents as `suspend fun` on the ViewModel (ADR-003).
- The partner API host is configurable and never versioned (`local.properties` → `BuildConfig`); HTTP
  status is always 200 and errors come in the body (see `docs/api-contract.md`). The account has a
  **request budget** — no polling, cache the list.
- Build: `./gradlew :androidApp:assembleDebug`. Tests: `./gradlew :shared:domain:testAndroidHostTest
  :shared:data:testAndroidHostTest :shared:app:testAndroidHostTest`. iOS: open `iosApp/` in Xcode.
- Never commit tokens or credentials (case rule). Test devices are listed in `docs/api-contract.md`.

## Do not

- Do not test the screen `StateFlow` with Turbine — read `state.value` after `advanceUntilIdle()` on a
  `StandardTestDispatcher`. Turbine is only for one-shot event flows (`SharedFlow`: navigation, snackbar).
- Do not add a Gradle dependency to make something compile; a new dependency needs an ADR line first.
- Do not weaken, skip or delete a failing test to get green — fix the code, or change the rule with an ADR.
  Any such proposal must be recorded in `AI-LOG.md` under "Tentativa de enfraquecer verificação".
- Do not touch `shared/` without running `./gradlew :konture-test:test` (architecture tests) first — once
  the `:konture-test` module exists (wave 0, PR `arch/konture`); the CI step is conditional until then.
- Do not "fix it in code" when the implementation diverges from `docs/specs/SPEC.md` or the ticket: change
  the SPEC/ticket first (with an ADR when structural), then the code.
- Do not explain trial-and-error history in code comments; what was learned goes into a short ADR that the
  code references. ADRs are the agent's memory between sessions.
- Screenshot tests (Roborazzi + ComposablePreviewScanner) are **planned, not implemented yet** — previews
  are the visual evidence for now. When they land: goldens are recorded and verified in CI only (Linux
  runner), never committed from a local machine.
- Do not import `android.*`, `androidx.*`, `platform.*` or `java.*` in `commonMain`; platform code goes to
  `expect/actual` under `:shared:data/platform`.
- Do not log the token, ever — `Authorization` is sanitized in the Ktor `Logging` plugin; use `LogLevel.HEADERS`, never `ALL`.
- Do not write the partner's hosts, portal URL, Swagger path or the test account's device serials /
  `idProduto` into any versioned file. Hosts come from `local.properties` (`smarthome.apiHost`,
  `smarthome.portalHost`, see `local.properties.example`) and reach the app through `BuildConfig`; docs use
  `<API_HOST>` / `<PORTAL_HOST>` / `<lock-ns>` placeholders; the unsanitised contract lives in `docs/private/`
  (git-ignored).

Per-module notes live in `shared/CLAUDE.md`, `androidApp/CLAUDE.md` and `iosApp/CLAUDE.md`.

## Commits, issues and pull requests

The full process is in `docs/PROCESS.md`. The short version:

- Conventional Commits in English: `type(scope): subject` with types `feat fix test refactor docs chore ci build arch`
  and scopes `domain data app session devices camera lock android ios ci docs adr spec`. AI attribution
  trailer is `Assisted-by: Claude <model>` (not `Co-Authored-By`). Structural refactors are separate
  commits from behaviour changes. Never commit without being asked; never with the global work e-mail.
- One Issue per slice (`<ID>: <title>`, ticket-contract body, labels `type:slice` `rf:RF0x` `area:*`,
  milestone `Wave N`; `agent-ready` is set by the gate), one branch `slice/<id>` from `origin/main`, one
  session per slice, one PR (≤ ~400 lines) whose body is in Portuguese and follows
  `.github/PULL_REQUEST_TEMPLATE.md`, containing `Closes #n`. Squash-merge by a human only.
- The canonical issues file is `docs/specs/issues.md` (pass it as `issuesPath` to the orchestrator).

## What runs where

- Locally (seconds, JVM): unit tests, Ktor `MockEngine` contract tests, architecture tests (screenshot
  tests once they exist).
- CI Android job (minutes): debug APK, lint.
- CI iOS job (macOS, expensive — 10× minutes on a private repo): iOS simulator tests, framework link,
  `xcodebuild` — runs only on pushes to `main` or by hand (`workflow_dispatch`), never on PR updates.

## Definition of done

- Acceptance criteria of the slice in `docs/specs/SPEC.md` are met and referenced in the PR.
- Unit tests for business rules pass on Android host tests; UI states (loading/success/empty/error) exist.
- `./gradlew :androidApp:assembleDebug` and the CI workflow are green.
- Any deviation from an ADR is recorded as a new ADR; AI mistakes/corrections go to `AI-LOG.md`.
