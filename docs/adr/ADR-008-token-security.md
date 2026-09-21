# ADR-008. Token security: native secure storage, redacted logs, nothing versioned

Status: Accepted (2026-09-20)

## Context

The token is a bearer credential that opens a physical lock. The brief forbids committing credentials
and scores "segurança" in the technical axis. The token lives up to 2 hours and can be renewed
(`renovarToken`). The docs page lists tokens of other people in the shared GDI account, so the app must
treat every token as sensitive, never echo it, and never persist it in plain preferences.

KMP survey (Sept 2026): multiplatform key-value libraries (`multiplatform-settings`, Kissme,
KSecureStorage) are fine for preferences but give less control over `kSecAttrAccessible*` / Keystore
parameters than direct platform APIs; for a credential, direct interop is the stronger choice.

## Decision Drivers

- No credential in git, logs, screenshots, crash reports or the UI.
- Survive process death without re-typing within the 2-hour window; disappear on logout.
- Expiry handled gracefully during the live demo.

## Considered Options

1. `EncryptedSharedPreferences` / `UserDefaults` via a KMP settings library.
2. Direct Android Keystore (AES/GCM key) + iOS Keychain (`kSecClassGenericPassword`,
   `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`) behind a `SecureTokenStore` domain contract.
3. Keep the token in memory only.

## Decision

Option 2.

- `:shared:domain`: `SecureTokenStore { read(); write(token, issuedAt); clear() }` and a
  `Session(tokenSuffix, expiresAt)` value exposed to the UI — the full token never reaches a screen.
- Both actuals live in `:shared:data`, package `platform.vault` (`expect class SecureTokenVault` in
  `commonMain`, actuals in `androidMain` / `iosMain`); `:androidApp` and `iosApp` only supply the
  Android `Context` through Koin. This is the only place besides `app.camera.platform` where
  `expect/actual` is allowed (ADR-001, rule 8).
- Android actual: AES/GCM key generated in `AndroidKeyStore` (`setUserAuthenticationRequired(false)`,
  no StrongBox requirement), ciphertext + IV in a private `SharedPreferences` file; `allowBackup=false`
  in the manifest so the file never reaches a cloud backup.
- iOS actual: Keychain item (`kSecClassGenericPassword`) with
  `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly` (excluded from iCloud/backup), via cinterop with
  `Security.framework` in Kotlin/Native — no Swift code involved.
- **Rotation safety.** The vault stores `(token, issuedAt)` atomically and every request records
  which token it was sent with. `SessionGuard` clears the vault on `TokenRejected` **only if** the
  rejected request's token is still the stored one; a rejection of a token that has since been
  replaced (renewal, S10) is dropped. Without this, an in-flight request with the old token could wipe
  the freshly renewed one (SPEC S6).
- Ktor logging installed with a header sanitizer: `Authorization` is replaced by `Bearer ***` in all
  logs; request bodies containing `token` are redacted.
- Session policy: store `issuedAt`; warn at 1h50; on `TokenRejected` clear the session and route to
  the token screen with a specific message (RF04). Renewal via `renovarToken` is offered if the
  endpoint proves usable (`docs/api-contract.md` open question 3).
- Repository hygiene: `.gitignore` covers `local.properties`, `*.keystore`, `.env*`; CI greps the
  tree for the token prefix pattern `Ot_[0-9A-Za-z]{20,}` and fails the build on a match. The class
  is alphanumeric, not hexadecimal: a real token carries letters beyond `a`-`f`, so the original
  `[0-9a-f]` could not match one and the gate would have passed a leaked credential (ADR-012).

## Consequences

- (+) Credential handled the way a production app would; a concrete, demonstrable security story.
- (−) Two platform implementations with cinterop on iOS; more code than a settings library.
- (−) A user who reinstalls the app must paste the token again (by design).

## Confirmation

- CI step: `rg -n "Ot_[0-9A-Za-z]{20,}"` over the repository returns nothing. Verified against a
  synthetic token whose body uses letters beyond `a`-`f` — the class the old pattern missed.
- Unit test on the Ktor logger sanitizer: a request with an `Authorization` header logs `Bearer ***`.
- Manual: after force-stopping the app the session survives; after logout the Keystore/Keychain entry
  is gone (`read()` returns null in a debug screen).

## Guardrail

- Guardrail: rule 9 — `production code never prints` (`Konture.files { should().notCall("kotlin.io.println") }`) in `:konture-test`
- Header redaction is enforced by Ktor itself — `install(Logging) { sanitizeHeader { it == HttpHeaders.Authorization }; level = LogLevel.HEADERS }` —
  and verified by a `MockEngine` contract test that captures the logger output and asserts `Bearer ***`
  (`LogSanitizerTest.authorizationHeaderRedacted`, SPEC S9).
