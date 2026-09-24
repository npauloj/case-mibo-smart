# ADR-028. Lock writes are on by default

Status: Accepted (2026-09-24) — amends the L-01b ticket's "Rollout / kill switch"

## Context

L-01b shipped `smarthome.lockWritesEnabled` defaulting to `false`, the opposite of the live-video
switch: opening, closing, changing the volume and enabling remote opening reach a physical door on an
account several people share, so a build only wrote to the lock when someone opted in.

In practice every build that was not hand-configured — CI's APK, a fresh clone, and the iOS app, whose
flag is written in `iOSApp.swift` — opened the lock screen on "Esta versão do aplicativo não envia
comandos para a fechadura". The lock commands are the feature the case evaluates, and the default hid
them from anyone who installed the app.

## Decision

The default becomes `true` on both platforms: `androidApp/build.gradle.kts` falls back to `"true"`,
`iOSApp.swift` passes `lockWritesEnabled: true`, and `local.properties.example` documents `true`.

The switch itself stays. `smarthome.lockWritesEnabled=false` still builds an app that sends no write
request at all, and `WritesDisabled` keeps its tests and its preview.

## Consequences

- Anyone who installs a build — including an APK handed to the panel — can command the test lock.
- Every command spends the account's request allowance (ADR-006).
- Turning writes off is now the opt-in, done in `local.properties` on Android and in `iOSApp.swift` on
  iOS.

## Confirmation

The generated `BuildConfig.SMARTHOME_LOCK_WRITES_ENABLED` is `true` when `local.properties` has no
`smarthome.lockWritesEnabled` key.
