# shared/ — Kotlin Multiplatform modules

Three Gradle modules; dependencies only point inward:

- `domain/` — pure Kotlin. Models, repository contracts (interfaces), typed errors, use-case result types.
  Allowed deps: `kotlinx-coroutines`, `kotlinx-serialization`, `kotlinx-datetime`. Nothing from Ktor, Koin,
  SQLDelight, Android or Compose (architecture rule 1).
- `data/` — the partner (Open Casa Inteligente) implementation: Ktor client, DTOs with `@SerialName`, mappers DTO → domain,
  SQLDelight cache, and the token vault `expect/actual` under `platform/vault/` (Keystore / Keychain,
  ADR-008). Depends on `domain` via `api(...)`; Ktor/SQLDelight stay `implementation` so the UI never
  sees them.
- `app/` — use cases, ViewModels and Compose UI, packages by feature: `session/`, `devices/`, `camera/`,
  `lock/`, plus `ui/` (theme, components, previews fixtures) and `di/`. Feature packages must not import
  each other (rule 3). The only `expect/actual` here is the video surface
  `camera/platform/LiveVideoPlayer` (Media3 in `androidMain`, WKWebView in `iosMain`, ADR-005).
  Builds the iOS `Shared` framework.
- **DI: one Koin module per feature** (ADR-014). A feature declares `internal val <feature>AppModule` in
  `app/<feature>/<Feature>AppModule.kt` and `internal val <feature>DataModule` in
  `data/<feature>/<Feature>DataModule.kt`, beside the code it wires. `di/AppModules.kt` and
  `di/DataModule.kt` keep only what belongs to **no** feature (the HTTP client, the JSON, the API, the
  routing ViewModel) and list the rest in their `featureModules` list — one entry per line,
  alphabetical. A slice adding a feature creates its files and appends one sorted line; it does not add
  bindings to the aggregate's body.
- `expect/actual` is allowed **only** in packages named `platform` (rule 8): `data.platform.*` and
  `app.<feature>.platform`. Never in `:androidApp` / `iosApp` — they depend on these modules and cannot
  provide actuals for them.

## Conventions

- Screen = `XScreen` (ViewModel + `collectAsState`) → `XScreenContent(state, on…)` stateless →
  `XScreenPreviews.kt` with a `PreviewParameterProvider` covering every state, `@PreviewLightDark`, names as
  metadata (`LockScreen_Locked`, `LockScreen_Locked_Dark`). Use `androidx.compose.ui.tooling.preview.Preview`.
- One `StateFlow<State>` per screen; user intents are `suspend fun` on the ViewModel; hardware states are
  sealed types (`LockUiState.CommandSent / Confirmed / CommandExpired / Offline / RemoteOpenDisabled`).
- Errors are values: `data` throws typed `SmartHomeApiException`s; each use case returns its own sealed result.
  Every `catch (Throwable)` rethrows `CancellationException`.
- Request budget: the test account has ~300 requests. Cache the device list, classify devices by `modelo`
  before calling `funcoes`, never poll, retry only on network failure.
- `LocalInspectionMode` around the video player and any `rememberLauncherForActivityResult`.

## Build & test

- `./gradlew :shared:domain:testAndroidHostTest :shared:data:testAndroidHostTest :shared:app:testAndroidHostTest`
- iOS (CI or Mac only): `./gradlew :shared:app:iosSimulatorArm64Test :shared:app:linkDebugFrameworkIosSimulatorArm64`
- Tests in `commonTest` use only multiplatform libraries (`kotlin.test`, `kotlinx-coroutines-test`, Mokkery,
  Ktor `MockEngine`); no JUnit imports, no MockK, no `Thread.sleep`, no `runBlocking`.
