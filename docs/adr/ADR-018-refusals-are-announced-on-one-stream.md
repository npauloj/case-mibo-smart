# ADR-018. A refused request is announced on one stream, from the transport, carrying its token

Status: Accepted (2026-09-21)

## Context

SPEC **S6** requires that when any use case receives a 401 or 403 **for a request sent with the
currently stored token**, the app clears the session and routes back to the token screen — and that a
refusal carrying a token that has since been replaced is dropped. It states the mechanism in one
sentence: *"Every request carries the token it was sent with in its failure, so the guard compares
identities, not timestamps."*

The sentence says what must travel. It does not say how, and the codebase makes the choice
non-obvious:

- Repositories in `:shared:data` read the credential from `SessionStore` themselves
  (`SmartHomeDeviceRepository`, `SmartHomeLockRepository`, `SmartHomeStreamingRepository`), precisely
  so the token never climbs into a ViewModel's state (ADR-008). The layers above therefore do **not**
  know which token any request carried.
- Each use case already converts the typed failure into its own sealed result (ADR-002), and those
  results deliberately carry no credential — `DeviceListResult.TokenRejected` is a state, not an
  incident report.
- The refusal can arrive from any endpoint, including endpoints that do not exist yet.

## Decision Drivers

- A guard with a hole is not a guard: the next slice that adds a partner call must not have to
  remember to wire it.
- The token must not gain a second route upwards. ADR-008 keeps it in `:shared:data`, and the guard
  needs identity, not the value.
- `:shared:domain` must stay free of Ktor, Koin and anything that knows what a screen is (ADR-001,
  rule 1).

## Considered Options

1. **Add the token to the error taxonomy** — a `sentWith` field on `TokenRejected` / `TokenExpired`.
   Rejected: the S-02b ticket puts the taxonomy out of scope, and every `when` over
   `SmartHomeException` (five of them) would start carrying a credential it has no use for.
2. **Give every use case a guard call.** Rejected: five call sites today, and the sixth is whatever
   the next slice adds. The failure mode is silence.
3. **Announce the refusal from the one place every request passes through** — `SmartHomeApi.post`,
   which is the only code that holds both the token it sent and the failure that came back.

## Decision

Option 3.

- `:shared:domain` declares `RefusedRequests`, a stream of `TokenRefusal(sentWith, reason)`, and
  `SmartHomeException.asTokenRefusal(sentWith)`, which is where the 401/403 rule of ADR-012 lives —
  including the two statuses that must **not** end a session (`Forbidden`, and everything that is not
  an authentication failure).
- `:shared:data` implements it as `SessionRefusals`, a `MutableSharedFlow` with **no replay**: a
  refusal is an event, and replaying one would send a user who has already pasted a new token back to
  the token screen for a request that failed before it existed.
- `SmartHomeApi.post` reports; `SessionGuard` (domain) decides; `AppViewModel` collects for the whole
  life of the app, because the screen that sent the refused request may already be gone.
- The reported failure keeps travelling up as the exception it was. The stream is an announcement, not
  a replacement for the call's own result — the device list still renders its own error state.

The `RefusedRequests` interface carries both `refusals` and `report`, which is unusual for a domain
contract. It is the same shape as `SessionStore` (read and write on one interface) and it is what
keeps `:shared:data`'s transport from importing `:shared:data`'s session feature.

## Consequences

- (+) Any endpoint added later is guarded the day it is written, with nothing to remember.
- (−) One Koin binding must be a `single`: two instances would be a guard that hears nothing. That is
  a wiring mistake no compiler catches, so `AppModulesTest` asserts the identity of the instance.
- (−) The refusal is fire-and-forget. If nothing is subscribed it is dropped, and the session is
  cleared one request later instead. Accepted: the collector starts with the app's first frame, and
  suspending inside the HTTP call to deliver an event would be worse.

## Confirmation

- `SessionRefusalsTest` drives the real `SmartHomeApi` against a `MockEngine`: a 401 and a 403 are
  announced with the token the call carried, a gateway 403 is not announced at all.
- `SessionGuardTest.rejectionOfRotatedTokenDoesNotClearVault` — the case the whole design exists for.
- `SessionExpiryRoutingTest` — a reported refusal becomes a route and an empty vault.
- `AppModulesTest.theSessionGuardAndItsRefusalStreamResolveFromTheRealGraph` — one stream, not two.
