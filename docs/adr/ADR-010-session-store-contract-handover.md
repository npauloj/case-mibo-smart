# ADR-010. `SessionStore` lands narrow in S-01a and widens to ADR-008's `SecureTokenStore` in S-01b

Status: Accepted (2026-09-21) — **amended 2026-09-21: the widening moved from S-01b to S-02a**

> This ADR assigned both halves to S-01b: swap the in-memory store for the vault **and** widen the
> interface to `write(token, issuedAt)` / `clear()`. Splitting S-01b during wave 2 separated them,
> and PR #37 shipped only the vault — correctly, since neither `issuedAt` nor `clear()` had a caller
> in it, and a member without a caller is the speculative generality this ADR exists to avoid.
> The widening now lands where the callers are: **S-02a** adds `issuedAt` (it computes expiry) and
> **S-02b** adds `clear()` (it has the logout). The decision below is unchanged; only the slice
> names are.

## Context

ADR-008 specifies the session's storage contract as
`SecureTokenStore { read(); write(token, issuedAt); clear() }`, backed by the Keystore / Keychain vault
in `:shared:data` `platform.vault`.

S-01a is the slice that first needs *somewhere* to put an accepted token, and its ticket puts the vault,
process-death survival, the expiry guard and logout out of scope — they are S-01b's and S-02's. None of
the three members ADR-008 adds beyond `read`/`write(token)` has a caller in this slice: `issuedAt` exists
for the expiry warning and the rotation-safety rule of SPEC S6/S7, `clear()` for the logout of S8.
The ticket is explicit that nothing may be added unless a test it names proves it, so shipping the wide
interface with two unused members and an unused parameter would be speculative generality.

## Decision

S-01a declares the narrow contract in `:shared:domain`:

```kotlin
interface SessionStore {
    suspend fun read(): Token?
    suspend fun write(token: Token)
}
```

- The functions suspend from the start, even though `InMemorySessionStore` never blocks, because the
  vault's reads and writes are blocking platform I/O — widening a non-suspend function later would
  change every call site.
- S-01b renames and widens it to ADR-008's `SecureTokenStore` (`write(token, issuedAt)`, `clear()`) and
  swaps `InMemorySessionStore` for the `platform.vault` actuals behind it. The rotation-safety rule of
  ADR-008 arrives with `issuedAt`, in the slice that has a renewal to protect.
- Until then the routing state shares the store's lifetime: it dies with the process (see
  `AppViewModel`), so the app never shows a session the store cannot back.

## Consequences

- (+) S-01a stays inside its ticket: no member without a caller, no parameter without a test.
- (+) The seam ADR-008 asks for exists from the first slice — `:shared:app` already talks to an
  interface, so S-01b is an implementation swap plus two added members, not a refactor of the use case.
- (−) One rename (`SessionStore` → `SecureTokenStore`) lands in S-01b's diff, touching the use case,
  the Koin module and the fakes. Cheap, mechanical, and visible in one PR.
- (−) Between S-01a and S-01b the token does not survive process death. Accepted and stated in the
  S-01a ticket's "Rollout / kill switch".

## Confirmation

- `AuthenticateToken` depends only on the `SessionStore` interface; `AuthenticateTokenTest` proves the
  write happens after the partner accepted the token and not before (`validTokenIsStoredAndSucceeds`,
  `rejectedTokenIsNotStored`).
- `AppViewModelTest` pins the flag's default to "no session" so a new process always starts on the
  token screen.
- S-01b's PR is the check on the other half: it must widen this interface rather than add a second one.
