# ADR-020. The session carries its own lifetime, and the vault entry carries it too

Status: Accepted (2026-09-21) — extends ADR-008's storage contract

## Context

Until S-03 the app had exactly one way to start a session: the user pastes a token, the partner accepts
it, and `AuthenticateToken` writes `(token, issuedAt)` to the vault. Nothing on the wire said how long
that token would last, so `Session` read a constant — `LIFETIME = 2.hours` — and SPEC S7 carried an
`[ASSUMED]` marker for it.

S-03 adds a second way. `POST /autenticacao/renovar-token/v1` answers with
`{"token": "<new>", "tempoExpiracao": 7199}` (probed 2026-09-21, `docs/api-contract.md` §2), and
`tempoExpiracao` is the new session's lifetime **in seconds**. The whole point of SPEC S10's third
acceptance criterion is that the app stops estimating a deadline once the server has stated one.

That leaves a choice about where the stated deadline lives. Three options were on the table:

1. **Keep the constant and bend `issuedAt`** — store `issuedAt = now + tempoExpiracao - LIFETIME`, so
   the existing arithmetic happens to produce the right countdown.
2. **Store the deadline as a second vault entry**, leaving `(token, issuedAt)` untouched.
3. **Give `Session` a `lifetime` and widen the one vault entry** to carry it.

## Decision

Option 3.

- `Session` becomes `(token, issuedAt, lifetime)`, with `lifetime` defaulting to `LIFETIME` — the 2 h a
  **pasted** token is still assumed to last, because that path genuinely has no value to read.
  `remainingLife` and `remainingUntilWarning` are computed from `lifetime`, not from the constant.
- `WARN_MARGIN` (10 min) replaces `WARN_AFTER` as the primitive: the warning is a distance from the
  **deadline**, not an age. `WARN_AFTER` stays as the derived boundary SPEC S7 states in words.
- `SessionStore.write` takes `lifetime`, defaulted to `Session.LIFETIME`, so the pasted-token call site
  is unchanged and the renewal passes what the server gave.
- The vault entry's encoding widens from `<epochMillis>:<token>` to
  `<epochMillis>:<lifetimeSeconds>:<token>`, still **one** entry.

Option 1 was rejected because it is a lie in storage: it writes an `issuedAt` the session never had, so
every later reader — a log line, a debug screen, the next slice — is misled by data that is only
correct when read through one particular subtraction. Option 2 was rejected because ADR-008 requires the
session to be stored atomically, and two entries are the one shape that can half-fail.

## Consequences

- (+) SPEC S7's `[ASSUMED]` narrows to the pasted-token path alone, and a renewed session's countdown is
  the partner's number rather than this app's arithmetic.
- (+) A renewal with a short `tempoExpiracao` still warns ten minutes before it ends, because the
  margin is measured back from the deadline.
- (+) One vault entry, so the hand-verified Keystore and Keychain actuals (ADR-008 §Confirmation,
  ADR-013) are untouched and need no new device round.
- (−) An entry written by a build from before this ADR (`<millis>:<token>`) no longer decodes, and is
  treated as "no session" — one more paste, on one build, for anyone who had the app installed. The same
  fail-safe a bare token already got (ADR-010), for the same reason: a session whose deadline the app
  cannot read is one it cannot reason about.
- (−) `SessionStore.write` now has a defaulted parameter, so every implementation must declare it.
  Three do: `VaultSessionStore`, `InMemorySessionStore`, and the domain test's `RecordingSessionStore`.

## Confirmation

- `VaultSessionStoreTest.roundTripsTheServerSuppliedLifetime` writes a deliberately non-default
  lifetime (900 s) and reads it back; `.anEntryWithoutALifetimeIsTreatedAsNoSession` pins the fail-safe
  for the old encoding, and `.tokenContainingTheSeparatorSurvives` proves the widened encoding still
  makes no assumption about the token's alphabet.
- `RenewTokenTest.usesServerSuppliedDeadline` (`:shared:app`) stages a 15 min `tempoExpiracao` — a value
  no local two-hour count could produce — and asserts the stored session's `remainingLife`;
  `.aShortRenewedSessionStillWarnsBeforeItEnds` asserts the margin moved with the deadline.
- `RenewTokenTest.readsTheServerSuppliedLifetime` (`:shared:data`) proves `tempoExpiracao` is read off
  the wire as seconds.
- `SessionStateTest` and `AccountViewModelTest` still assert the default-lifetime boundary through
  `Session.WARN_AFTER`, which is how a regression in the derived constant would show.
