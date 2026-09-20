# androidApp/ — Android entry point

Thin host for `:shared:app`: `MainActivity` (edge-to-edge, `setContent { App() }`), `MiboSmartApplication`
(`initKoin { androidContext(...) }`), manifest, launcher icons. Platform pieces that need an Android
runtime are `actual` implementations in the shared modules, never here: Media3/ExoPlayer for the fMP4
live stream is the `androidMain` actual of `LiveVideoPlayer` in `:shared:app` (package `camera.platform`,
ADR-005); the Keystore-backed token vault is the `androidMain` actual in `:shared:data` (package
`platform.vault`, ADR-008). This module cannot provide actuals for modules it depends on.

## Rules

- No business logic, no networking, no Compose screens here; if it is not Android plumbing it belongs in `shared/`.
- Keep `INTERNET` the only permission unless a feature proves it needs more.
- Release build ships with R8 (`isMinifyEnabled = true`, `isShrinkResources = true`,
  `proguard-android-optimize.txt` + `proguard-rules.pro`); CI builds `assembleRelease` on every PR so keep-rule
  problems surface early. Baseline on 2026-09-20: debug APK 16.0 MB → release 0.9 MB (previews and
  `ui-tooling` stripped). Verify with APK Analyzer whenever a platform dependency is added.
- `allowBackup="false"` and a `network_security_config` without cleartext are deliberate (ADR-008):
  the encrypted token must never travel in cloud backups and API 24–27 would otherwise allow plain HTTP.
- Never commit `local.properties`, keystores or credentials.

## Build & run

- `./gradlew :androidApp:assembleDebug` (debug APK in `androidApp/build/outputs/apk/debug/`)
- `./gradlew :androidApp:assembleRelease` before the final delivery; smoke-test the release build on a device.
- `./gradlew :androidApp:lintDebug`
