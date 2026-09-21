# ADR-012. Authentication failures are signalled by HTTP status, not by message text

Status: Accepted (2026-09-21) — supersedes the token-rejection rule of ADR-002

## Context

ADR-002, SPEC E1/S3/S6 and `docs/api-contract.md` §1 were all built on one observation from the
discovery phase:

> **Always `200`, even on errors.** The outcome lives inside the body.
> There is no dedicated 401/403. The only signal that a token was rejected is the generic
> "Erro desconhecido" on an otherwise well-formed request.

From that, ADR-002 derived a business rule: *a flat-shape `status: "erro"` whose `msg` starts with
"Erro desconhecido" on a valid request means the token was rejected.* `EnvelopeReader`, the typed
error taxonomy and every `MockEngine` contract test were written to it, and all of them pass.

On 2026-09-21 the app was run against the real API from a physical device for the first time. It never
showed "Token inválido ou expirado". Three direct probes of
`POST /produtos/listar-dispositivos/v1`, plus one with a real expired token, gave:

| Case | HTTP | `Content-Type` | Body |
|---|---|---|---|
| No `Authorization` header | **401** | `text/plain` | `"Token não está presente na requisição"` |
| Malformed token | **401** | `text/plain` | `"Token não está presente na requisição"` |
| Well-formed, unknown token | **401** | `text/plain` | `"Não autorizado"` |
| **Expired token** | **403** | `text/plain` | `{"status":"erro","msg":"Token expirado, por favor gere um novo token"}` |

Every documented premise fails:

1. **The status is not always 200.** Authentication failures answer 401 and 403.
2. **401 and 403 both exist**, and they carry different meanings: 401 = the token is absent or not
   recognised; 403 = the token is recognised and expired.
3. **"Erro desconhecido" never appears.** The rule at the heart of ADR-002 cannot fire.
4. **The body is not always the documented envelope.** For 401 it is a bare **JSON string**, not an
   object, served as `text/plain` — so `EnvelopeReader` fails to deserialise it and the user sees
   "Resposta inesperada" instead of a token error.
5. **The contract's `[ASSUMED: expired identical to invalid]` is wrong — in the app's favour.** The
   platform distinguishes them and hands back a user-ready sentence for the expired case.

A second, independent error surfaced from the same probe. `docs/guides/token.md` §2 stated the token
is `Ot_` + 32 **hexadecimal** characters. A real token's body is 32 **alphanumeric** characters and
carries letters beyond `a`–`f`. Two things were built on the wrong claim and merged:

- **SPEC S1.2 / `TokenFormat`** (PR #29) rejected a token the platform accepts — "Validar" stayed
  disabled forever, with no API call and no way for the user to understand why.
- **The CI secret scan** (`Ot_[0-9a-f]{20,}`, in `.github/workflows/ci.yml` and ADR-008) could not
  match a real token, so the gate that is supposed to stop a leaked credential would have passed one.

Nothing in the test suite could have caught any of this: every test feeds `MockEngine` with bodies
transcribed from the same documentation. The suite proves fidelity to the contract; it cannot prove
the contract describes the API.

## Decision Drivers

- RF04 asks for errors that name their cause. Today the two most likely failures — invalid and expired
  token — surface as "Resposta inesperada" and a generic failure.
- The signal should be the most reliable one the transport offers, not prose that can be reworded.
- Only one real token and one expired token have been observed. Be precise where measured, permissive
  where not.

## Considered Options

1. **Keep ADR-002's message-text rule and add the status codes beside it.** Cheap, but leaves a dead
   branch that the real API never takes and that a reader will believe is live.
2. **Read the status first, fall back to the envelope.** The status is the documented HTTP contract;
   the body shape varies (string for 401, object for 403).
3. **Parse the message text for "expirado" / "Não autorizado".** Rejected: it is the failure mode this
   ADR exists to correct, one reword away from breaking, and locale-dependent.

## Decision

Option 2. Authentication outcome is decided by **HTTP status**, before any body parsing:

| Status | Domain error | User-facing message |
|---|---|---|
| `401` | `TokenRejected` (not recognised) | "Token inválido ou expirado" (SPEC S3) |
| `403` | `TokenExpired` | the server's `msg` when the body parses, else "Sua sessão expirou (tokens valem 2 h)" (SPEC S6) |
| `2xx` with `status != "sucesso"` | the existing business-error mapping (ADR-002) | unchanged |

- `TokenExpired` joins the sealed error hierarchy as a **new subtype**, which is what ADR-002's
  "errors are values" shape was designed to absorb — no refactor of existing call sites.
- **The body is read only after the status has been classified**, and a failure to deserialise it
  never downgrades a 401/403 into "Resposta inesperada". A bare JSON string is an accepted shape.
- The `"Erro desconhecido"` rule of ADR-002 is **removed**, not kept as a fallback. It never fires
  against the real API, and a rule nobody exercises is a rule nobody maintains.
- Token format is `Ot_` + 32 **alphanumeric** characters (`TokenFormat.BODY_CLASS`). Both cases are
  accepted: one real token has been observed, and locking a user out of a valid token is strictly
  worse than spending one request to hear the API's verdict — which is now unambiguous.
- The CI secret scan uses the **same character class**. A scan narrower than the format it guards is
  not a gate.

## Consequences

- (+) RF04 is actually satisfied for the two failures a user will really hit, with the platform's own
  wording for the expired case.
- (+) S6 can compare the rejected request's token identity against a *real* signal instead of a
  message prefix.
- (−) `EnvelopeReader`, the error taxonomy, the `MockEngine` fixtures and their tests all change. That
  is the cost of having written them against documentation instead of the wire.
- (−) Three merged PRs (#26, #29) carry the superseded behaviour. The correction slice is the fix;
  the history stays as it is, and `AI-LOG.md` records why.
- (−) Only one expired and one absent token were observed. A 403 whose body is *not* the documented
  envelope is handled (fallback message), but the shape is not confirmed beyond one sample.

## Confirmation

- A `MockEngine` test per row of the decision table, asserting the domain error **and** the string the
  user sees: `EnvelopeReaderTest.unauthorizedIsTokenRejected`,
  `.forbiddenIsTokenExpiredWithServerMessage`, `.forbiddenWithUnparseableBodyStillExpires`,
  `.bareJsonStringIsNotUnexpectedResponse`.
- `TokenFormatTest.acceptsLettersBeyondHexadecimal` — the regression that started this ADR; it fails
  against the pre-ADR `TokenFormat`.
- The CI secret scan is verified against a synthetic token whose body uses letters beyond `a`–`f`.
- **Manual, on a device, before the case is delivered** — the only check that would have caught this:
  paste an expired token and read the message. Recorded in the PR body, per ADR-008 §Confirmation's
  precedent of naming a manual step when no automated one can exist.
