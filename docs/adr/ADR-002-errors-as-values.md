# ADR-002. Errors are values; each use case defines its own result type

Status: Accepted (2026-09-20) — **the token-rejection rule below is superseded by
[ADR-012](ADR-012-auth-failures-are-http-status-not-message-text.md) (2026-09-21)**

> The shape of this ADR stands: errors are values, each use case owns its result type, and the
> taxonomy is a sealed hierarchy new subtypes can join. What does **not** stand is the specific signal
> it picked for a rejected token — `status: "erro"` with `msg` starting "Erro desconhecido". The real
> API answers `401`/`403` and never sends that message; the rule could not fire. ADR-012 replaces it
> with the HTTP status and adds `TokenExpired`. Read the two together.

## Context

RF04 requires friendly handling of "token inválido, expirado, falha de rede, lista vazia", and
"tratamento de erros" is part of the 25% code-quality axis. The observed contract makes this harder
than usual (see `docs/api-contract.md` §1):

- HTTP status is always 200; the outcome is inside the body.
- Two envelope shapes: `{statusCode, body:{status, data|msg}}` and flat `{status, data|msg}`.
- A rejected token comes back as `{"status":"erro","msg":"Erro desconhecido, por favor tente novamente mais tarde"}`
  — the same text a genuine server error would use.
- Sub-device addressing mistakes surface as `statusCode: 404 "Dispositivo não encontrado"`.

Efthymiou, p. 223: "if a required field is missing or comes in an unexpected format, we must return
an error object or throw an Exception. Usually, I prefer having wrappers around errors, like Kotlin
Result … Throwing exceptions may lead to undesired crashes." And the data-source interface must be
shaped by what the use case needs (p. 181–191, Dependency Inversion).

## Decision Drivers

- The UI must render loading, success, empty and error as four distinct states, without a global
  catch-all.
- Cancellation must never be swallowed (structured concurrency).
- The "token rejected" interpretation is a business rule, not a transport fact.

## Considered Options

1. Throw platform/Ktor exceptions and catch them in ViewModels.
2. One global `sealed class AppError` returned everywhere.
3. `:shared:data` throws a small sealed hierarchy of typed exceptions; **each use case** catches and
   maps them into its own sealed result (`Success / Empty / TokenRejected / Offline / Failure`).

## Decision

Option 3.

- `:shared:data` owns an `EnvelopeReader` that accepts both shapes and raises typed exceptions:
  `MissingToken`, `TokenRejected` (flat `erro` + "Erro desconhecido" on a request the client knows is
  well-formed), `DeviceNotFound` (404), `QuotaExceeded` (402), `OperationRejected(msg)`,
  `UnexpectedResponse`, `Network` (IO/timeout).
- `:shared:domain` declares the result types per intent, e.g.
  `DeviceListResult = Loaded(devices, hasMore) | Empty | TokenRejected | Offline | Failure(cause)`,
  `LockCommandResult = Applied(state) | RemoteOpenDisabled | Offline | TokenRejected | Failure`.
- Every `catch (t: Throwable)` in use cases rethrows `CancellationException` first.
- The decision "Erro desconhecido ⇒ token rejected" is written once, in the use-case layer helper
  `Throwable.rejectsToken()`, and unit-tested.

## Consequences

- (+) Screens exhaustively `when` over the cases that exist for them; the compiler flags a missing
  branch.
- (+) Transport quirks stay in `:shared:data`; the domain never sees `statusCode`.
- (−) More types to write than a single `Result<T>`; mitigated by keeping result types next to the
  use case that produces them.
- Risk: if the platform changes the generic message, token rejection degrades to `Failure` — still a
  friendly error, just less specific.

## Confirmation

- Unit tests in `:shared:data` for `EnvelopeReader`: both shapes, `sucesso`/`erro`, 404, missing
  `data`, malformed JSON.
- Unit tests in `:shared:app` proving each use case maps `TokenRejected`→`TokenRejected`,
  `Network`→`Offline`, empty page→`Empty`, other→`Failure(cause)`; and that
  `CancellationException` propagates (test with `runTest` + `cancel()`).
- Code review check: no `catch (e: Exception)` without a preceding `CancellationException` rethrow.

## Guardrail

- Guardrail: rule 5 — `repositories declared in domain must be interfaces` in `:konture-test`
- Guardrail: rule 10 — detekt `SuspendFunSwallowedCancellation` (control-flow rule; out of reach of
  architecture tests, so it lives in `detekt.yml` and runs in the same CI stage)
