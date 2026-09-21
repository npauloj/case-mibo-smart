# Case Mibo Smart — product spec (test-first)

Status: v0.1, 2026-09-20. Feeds `/to-issues docs/specs/SPEC.md --publish`.
Source of truth for behaviour; architecture lives in `docs/adr/`, the wire contract in
`docs/api-contract.md`. Acceptance criteria use **EARS** where ambiguity is dangerous
(`WHEN <trigger> [WHILE <state>], THE SYSTEM SHALL <response>` / `IF <precondition>, THE SYSTEM SHALL …`)
and **Given/When/Then** elsewhere. Every criterion names the test that proves it. Markers
`[ASSUMED]` / `[NEEDS CLARIFICATION]` are carried verbatim from the contract.

## 0. Overview

**Product:** a small Kotlin Multiplatform app (Android primary, iOS as proof of sharing) that lets the
holder of an Open Casa Inteligente temporary token see their devices, watch a camera live and operate a
lock. **User:** the account owner or integrator evaluating the platform. **Not a goal:** account
creation, token generation (done on the web portal), lamps, sensors, recordings, lock passwords.

Mapping to the brief: RF01 token screen · RF02 device list · RF03 live video · RF04 friendly errors ·
RF05 lock open/close/status · RF06 lock volume · RF07 origin filter · RF08 pagination · RF09 opening history.

Global UI states every screen must render: **loading**, **success**, **empty** (where a list exists),
**error** with a retry affordance where retry is meaningful. Requests are a scarce resource
(~300 for the whole case, value read on 2026-09-20 — `docs/api-contract.md` §1.3): no screen may call the API on every
recomposition or on a timer.

Screen state naming used below (ADR-003): `SessionState = NoToken | Valid(expiresAt) | Expired`;
`LockState = Locked | Unlocked | CommandSent | Confirmed | CommandFailed | CommandExpired | RemoteOpenDisabled | Offline`;
`StreamState = Creating | Live | Reconnecting(n) | Expired | QuotaExceeded | Offline | Failed`.

**Where each criterion is verified.** Locally on the JVM, in seconds: unit tests, Ktor `MockEngine`
contract tests and architecture tests (`:konture-test`); screens are proven by their previews
(screenshot tests rendered from the previews are planned, not part of the case).
Only in CI on macOS (minutes): everything iOS — simulator unit tests, framework link, `xcodebuild`.
A criterion marked `Applies to: androidMain and iosMain` has an `expect/actual` pair and is only
fully proven once the macOS job is green.

**Visual acceptance.** Every screen exposes a stateless `<Screen>Content(state, on…)` composable with
a `PreviewParameterProvider` enumerating its states; preview names are metadata, `<Screen>_<State>`
with a `_Dark` suffix for the dark theme (reviewers — and a future screenshot pipeline — parse them).
Architecture-test rule 11 (ADR-009) fails the build when a `*Content` composable has no preview.

**Product principle — quiet and direct.** Real-user research on the partner's official app
(`docs/research/user-feedback.md`, 270 recent reviews) shows the two biggest frustrations are a live
stream stuck at "97 %" with no named error, and promotional noise between the user and the camera.
Therefore: no splash beyond the system one, no interstitial, banner or survey; every waiting state has a
bounded duration and ends in a named state; every screen shows how fresh its data is. The concrete
criteria are **U1–U8** in §8 and are referenced from the feature sections they refine.

---

## 1. Token & session (RF01, RF04)

**Goal:** get a valid token into the app, keep it safely for its lifetime, and make token failures
understandable. **Out of scope:** generating tokens, GDI login, multi-account.

### Acceptance criteria

- **S1** WHEN the app starts WITHOUT a stored token, THE SYSTEM SHALL show the token entry screen with
  a masked input, a paste action and a "Validar" button disabled until the input is non-blank.
  _(test: `TokenEntryViewModelTest.emptyInputKeepsSubmitDisabled`; masked input proven by preview `TokenScreen_Typing` — no Compose UI test runner in scope)_
- **S1.1 — the mask must still let the user check what they pasted.** WHILE the token field holds text,
  THE SYSTEM SHALL render the public `Ot_` prefix and the **last 4 characters** in clear and mask
  everything between them, and SHALL show a character counter against the expected length. THE SYSTEM
  SHALL NOT offer any control that reveals the whole token. _(test:
  `TokenMaskTest.showsPrefixAndLastFourOnly`, `TokenMaskTest.shortInputIsFullyMasked`; preview
  `TokenScreen_Typing`)_
  Rationale: a paste action the user cannot verify is a paste they have to trust — the failure mode is a
  truncated clipboard that costs one request from the budget to discover. Revealing at most the last 4
  characters is the most S9 permits, so this needs no exception to it.
- **S1.2 — reject a malformed token before spending a request.** WHEN the input does not match
  `Ot_` followed by exactly 32 **alphanumeric** characters (after trimming surrounding whitespace),
  THE SYSTEM SHALL keep "Validar" disabled and, once the user has typed or pasted something, SHALL
  show "Token incompleto ou em formato inválido" in the field; THE SYSTEM SHALL NOT call the API.
  _(test: `TokenFormatTest.acceptsTheDocumentedFormat`, `.rejectsTruncatedPaste`,
  `.rejectsNonHexCharacters`, `.trimsSurroundingWhitespace`,
  `TokenEntryViewModelTest.malformedTokenCostsNoRequest` — call counter stays at 0; preview
  `TokenScreen_InvalidFormat`)_
  Format per `docs/guides/token.md` §2. The account's request budget is ~300 for the whole case
  (ADR-006), so a typo must not cost one.
- **S2** WHEN the user submits a token, THE SYSTEM SHALL validate it with one
  `listar-dispositivos` call (`tamanhoPagina: 1, pagina: 1`) and, on success, persist it in secure
  storage and navigate to the device list. _(test: `AuthenticateTokenTest.validTokenIsStoredAndSucceeds` — fake repository counts exactly one call)_
  Applies to: androidMain and iosMain (`SecureTokenStore` actuals — Keystore / Keychain, ADR-008).
- **S3** IF the validation call answers **HTTP 401**, THE SYSTEM SHALL keep the user on the token screen
  with the message "Token inválido ou expirado" and SHALL NOT store the token.
  _(test: `AuthenticateTokenTest.rejectedTokenIsNotStored`, `EnvelopeReaderTest.unauthorizedIsTokenRejected`)_
- **S3.1** IF the call answers **HTTP 403**, THE SYSTEM SHALL keep the user on the token screen with the
  server's own message when the body parses (`"Token expirado, por favor gere um novo token"`) and with
  "Sua sessão expirou (tokens valem 2 h)" when it does not, and SHALL NOT store the token.
  _(test: `EnvelopeReaderTest.forbiddenIsTokenExpiredWithServerMessage`,
  `EnvelopeReaderTest.forbiddenWithUnparseableBodyStillExpires`)_
  The platform distinguishes an unknown token from an expired one — verified 2026-09-21, ADR-012. The
  old rule (`msg` starting "Erro desconhecido") never fired against the real API and is gone.
- **S4** IF the validation call fails for network reasons, THE SYSTEM SHALL show "Sem conexão" with a
  retry action and SHALL NOT discard the typed token. _(test: `AuthenticateTokenTest.networkFailureKeepsInput`)_
- **S5** WHEN the app starts WITH a stored token, THE SYSTEM SHALL open the device list directly using
  the cached list if present (no validation call). _(test: `SessionStartTest.storedTokenSkipsEntry`)_
- **S6** WHILE a session is valid, WHEN any use case receives **a 401 or a 403** (token-rejected or
  token-expired) **for a request that was sent with the currently stored token**, THE SYSTEM SHALL clear
  the stored token and route to the token screen — with the server's message on a 403 and with
  "Sua sessão expirou (tokens valem 2 h)" otherwise; IF the rejected request was sent with a token
  that has since been replaced (renewal, S10), THE SYSTEM SHALL drop the rejection and keep the stored
  token. Every request carries the token it was sent with in its failure, so the guard compares
  identities, not timestamps. _(test: `SessionGuardTest.rejectionClearsAndRoutes`,
  `SessionGuardTest.rejectionOfRotatedTokenDoesNotClearVault` — renewal completes while a request with
  the old token is in flight; the vault still holds the new token)_
- **S7** WHEN the session is older than 1 h 50 min, THE SYSTEM SHALL show a non-blocking banner
  "Token expira em breve" on the device list. _(test: `SessionStateTest.warnsBeforeExpiry` with a fake clock)_ `[ASSUMED: expiry counted from first successful validation]`
- **S8** Given a stored session, when the user taps "Sair" in the account screen, then the token is
  removed from secure storage and the app returns to the token screen.
  _(test: `LogoutTest.clearsSecureStore`)_
  Applies to: androidMain and iosMain (`SecureTokenStore` actuals — Keystore / Keychain, ADR-008).
- **S9** THE SYSTEM SHALL never display more than the last 4 characters of the token and never write
  it to logs. This binds the **entry field** as much as the account screen: S1.1's mask is the only
  place the token is partially shown, and no reveal control exists anywhere in the app.
  _(test: `LogSanitizerTest.authorizationHeaderRedacted`, `AccountViewModelTest.exposesSuffixOnly`,
  `TokenMaskTest.showsPrefixAndLastFourOnly`)_
- **S10** `[ASSUMED: renovarToken is POST /autenticacao/renovar-token/v1 on the api host with body {token} and returns the new token in data — per Swagger and the docs' "Saiba mais"; verified with one real call at the start of wave 3, never earlier (it rotates the working token)]`
  WHEN the session is about to expire, THE SYSTEM SHALL offer "Renovar" and, on success, replace the stored
  token without leaving the current screen; IF renewal fails, THE SYSTEM SHALL keep the current token and
  show the error state of S6. _(test: `RenewTokenTest.replacesStoredToken`, `RenewTokenTest.failureKeepsCurrentToken`)_
  Wave 3; first in the cut list after the Java module.

### Screen states
- Token screen: idle · typing (masked with visible `Ot_` prefix, last 4 and counter — S1.1) ·
  invalid-format (S1.2, "Validar" disabled, no API call) · validating (button spinner, input locked) ·
  error (inline message).
- Account screen (no network): suffix, "expira em …", "Sair", debug request counter (ADR-006).

### Visual acceptance (previews)
`TokenScreenContent` / `AccountScreenContent`, each with a `PreviewParameterProvider`; rule 11 guarantees the preview exists.
- `TokenScreen_Idle`, `TokenScreen_Typing` (S1.1 mask + counter), `TokenScreen_InvalidFormat` (S1.2),
  `TokenScreen_Validating`, `TokenScreen_Error` (S1, S3, S4) — plus `_Dark` variants.
- `AccountScreen_Valid`, `AccountScreen_ExpiringSoon` (S7 banner), `AccountScreen_Expired` — plus `_Dark` variants.

---

## 2. Device list, origin filter, pagination (RF02, RF07, RF08, RF04)

**Goal:** show every device linked to or shared with the account, filterable by origin, paged as the
API requires, with each device classified so the user knows what they can open.
**Out of scope:** renaming, firmware update, battery, per-device settings.

### Acceptance criteria

- **D1** WHEN the device list opens for the first time in a session, THE SYSTEM SHALL request page 1
  with `tamanhoPagina: 20` and `origem` = the selected filter (default `todos`).
  _(test: `ListDevicesTest.firstPageUsesDefaults` — asserts exact request via MockEngine)_
- **D2** WHEN a page returns exactly `tamanhoPagina` items, THE SYSTEM SHALL offer/perform loading of the
  next page on scroll; WHEN a page returns fewer items or an empty array, THE SYSTEM SHALL mark the list
  as complete and make no further page requests. _(test: `PaginationTest.fullPageHasMore`, `PaginationTest.shortPageEndsList`, `PaginationTest.emptyPageEndsListWithoutExtraCall`)_
- **D3** IF page 1 returns an empty array, THE SYSTEM SHALL show the empty state "Nenhum dispositivo
  para este filtro" with the filter chips still available. _(test: `ListDevicesTest.emptyFirstPageIsEmptyState`)_
- **D4** Given the filter chips "Todos / Vinculados / Compartilhados", when the user selects one, then
  the list reloads from page 1 with `origem` = `todos|vinculados|compartilhados` and the selection is
  remembered across launches. _(test: `OriginFilterTest.mapsToWireValues`, `PreferencesTest.filterPersists`)_
- **D5** THE SYSTEM SHALL classify each device as Camera / Lock / Hub / Other using `modelo`
  (`iM*` → Camera, `*MFR*` + sub-device → Lock, `IOT-ZG2-IB` → Hub) without extra API calls at list time.
  _(test: `DeviceClassifierTest.classifiesTestAccountInventory` using the captured response)_
- **D6** Each row shows name, model, online/offline indicator, origin badge and, for sub-devices, the
  parent hub name when known. Cameras and locks are tappable; hubs and others are not.
  _(test: `DeviceUiMapperTest.subdeviceShowsParent`; row layout proven by preview `DeviceListScreen_Success`)_
- **D7** WHEN the user pulls to refresh, THE SYSTEM SHALL reload from page 1; WHEN the user merely
  navigates back to the list, THE SYSTEM SHALL NOT call the API. _(test: `ListDevicesTest.navigationDoesNotRefetch` — call counter)_
- **D8** IF the list request fails for network reasons AND a cached list exists, THE SYSTEM SHALL show
  the cached list with "Sem conexão — última atualização há N min" and a retry action; IF no cache
  exists, THE SYSTEM SHALL show the error state with retry. _(test: `ListDevicesTest.offlineWithCacheShowsStale`, `ListDevicesTest.offlineWithoutCacheShowsError`)_
- **D9** IF the list request is token-rejected, THE SYSTEM SHALL apply S6. _(test: covered by `SessionGuardTest`)_
- **D10** WHEN a page is successfully loaded, THE SYSTEM SHALL persist it in the local cache with a
  timestamp. _(test: `DeviceCacheTest.roundTripsPage`)_
- **D11 — one request in flight per list.** Filter changes, refresh and next-page loads are serialised
  through a single list job: WHEN the user changes the filter or refreshes WHILE a page request is in
  flight, THE SYSTEM SHALL cancel that request (`collectLatest` / `Job.cancel`) and ignore any response
  whose `(origem, pagina)` differs from the current query; WHEN a next-page request is already in
  flight, THE SYSTEM SHALL ignore further next-page triggers until it completes. A late response from a
  previous filter SHALL never appear in the list. _(test:
  `PaginationTest.staleResponseFromPreviousFilterIsDropped` — fake repository answers the old query
  after the new one, `state.value` shows only the new query's rows;
  `PaginationTest.concurrentNextPageTriggersMakeOneRequest` — call counter;
  `PaginationTest.filterChangeCancelsInFlightRequest` — the old request's `Job` is cancelled)_

### Screen states
- loading (first load skeleton) · success (list) · loading-more (footer) · empty · error (no cache) ·
  stale (cache + banner) · filter chips always visible.

### Visual acceptance (previews)
`DeviceListScreenContent` with a `PreviewParameterProvider` built from the captured test-account inventory (`PreviewFixtures`); rule 11 guarantees the preview exists.
- `DeviceListScreen_Loading`, `DeviceListScreen_Success`, `DeviceListScreen_LoadingMore` (D2),
  `DeviceListScreen_Empty` (D3), `DeviceListScreen_Error` (D8 no cache), `DeviceListScreen_Stale` (D8 cache + banner)
  — plus `_Dark` variants. Edge cases in the same provider: a very long device name, an old `ultimaVezOnline`, 20 rows.

---

## 3. Live video (RF03, RF04)

**Goal:** from a camera row, watch its live stream with clear feedback while the session is created,
plays, reconnects or fails; leave without leaking streaming quota.
**Out of scope:** recordings, PTZ, two-way audio, snapshots, multi-camera grid.

### Acceptance criteria

- **V1** WHEN the user opens a camera, THE SYSTEM SHALL confirm streaming capability with at most one
  `funcoes` call per camera per install (cached), and IF `funcoes` does not contain `RTSV`, THE SYSTEM
  SHALL show "Esta câmera não anuncia vídeo ao vivo" instead of creating a session.
  _(test: `WatchLiveVideoTest.capabilityCheckedOnce`, `WatchLiveVideoTest.noRtsvNoSession`)_
- **V2** WHEN a session is created, THE SYSTEM SHALL request `stream_gb: 0.5`, `streamId: 1`,
  `canalVideo: 0` by default and hand `data.url` to the player in the same coroutine, before any
  other suspension, so playback starts within the 15-second expiry window.
  _(test: `WatchLiveVideoTest.exactCreateRequest`, `WatchLiveVideoTest.playerPreparedImmediately` — fake player records prepare timestamp relative to create)_
  Applies to: androidMain and iosMain (`LiveVideoPlayer` `expect @Composable` in `app.camera.platform` — Media3 / WKWebView, ADR-005).
- **V3** WHILE the stream is Creating or Reconnecting, THE SYSTEM SHALL show a loading overlay with
  the current step ("Criando sessão…", "Conectando…", "Reconectando (2/3)…"). _(test: `LiveVideoViewModelTest.stateSequenceOnHappyPath` — asserts `state.value` after each `advanceUntilIdle()` on a `StandardTestDispatcher`; Turbine is not used for the screen `StateFlow`)_
- **V4** IF the player reports a network drop or end of stream, THE SYSTEM SHALL retry up to 3 times
  with increasing delay (1 s, 3 s, 7 s), first re-preparing the same URL, then creating a new session;
  after the third failure THE SYSTEM SHALL show `Failed` with "Tentar novamente" and "Abrir no player web".
  _(test: `PlaybackRetryPolicyTest.*`)_ `[ASSUMED delays]`
- **V5** IF the player reports a decode/format error, THE SYSTEM SHALL NOT retry and SHALL offer the
  WebView fallback on `monitor_url`. _(test: `PlaybackRetryPolicyTest.decodeErrorNoRetry`)_
- **V6** IF session creation answers quota exceeded (402 semantics), THE SYSTEM SHALL show
  "Cota de streaming esgotada" without retry. _(test: `WatchLiveVideoTest.quotaExceededState`)_
- **V7** IF the camera is offline (`status: offline` in the list or creation error), THE SYSTEM SHALL
  show "Câmera offline" and not create a session. _(test: `WatchLiveVideoTest.offlineCameraNoSession`)_
- **V8** WHEN the user leaves the video screen (back / pop), WHEN the app goes to the background
  (`Lifecycle.Event.ON_STOP` observed by the screen through `LifecycleEventEffect`, which calls
  `viewModel.stop()`), or WHEN the stream coroutine is cancelled mid-creation, THE SYSTEM SHALL detach
  the player, cancel the stream job and call `encerrar-sessao` for the active `session_id` from the
  app-level `SupervisorJob` scope under `NonCancellable` with a 5 s timeout — never from
  `viewModelScope`, which is already cancelled when the ViewModel is cleared. WHEN the screen returns
  to the foreground (`ON_START`) while still visible, THE SYSTEM SHALL create a new session (counted
  against the 2-creations-per-visit cap of V4). _(test: `LiveVideoViewModelTest.stopEndsSessionAndDetachesPlayer`,
  `LiveVideoViewModelTest.cancellationMidCreationStillEndsSession` — `NonCancellable` teardown with a
  virtual clock, `LiveVideoViewModelTest.teardownUsesAppScopeNotViewModelScope` — the teardown completes
  after `viewModelScope` is cancelled; the `ON_STOP` wiring is proven by a fake `LifecycleOwner` in
  `LiveVideoScreenLifecycleTest` on the Android host)_
  Applies to: androidMain and iosMain (`LiveVideoPlayer` actuals in `app.camera.platform` release their
  player on dispose, ADR-005).
- **V9** Given the fallback "Abrir no player web", when tapped, then a WebView loads `monitor_url`
  inside the app (not an external browser). _(evidence: manual check on device/simulator recorded in the PR + preview `LiveVideoScreen_WebFallback`; no instrumented test in scope)_ `[ASSUMED: monitor page works in WebView — on iPhone, WKWebView only offers ManagedMediaSource (iOS 17.1+); verified on the Mac before any iOS player code]`
- **V10** On iOS, the `iosMain` actual of `LiveVideoPlayer` IS the WKWebView on `monitor_url` (no
  VLCKit, ADR-005); IF the Mac check of V9 fails, THE SYSTEM SHALL show "Abrir no player web" opening
  the system browser instead. _(test: iOS unit test `LiveVideoPlayerIosTest.rendersWebViewForMonitorUrl`
  on the simulator target)_
  Applies to: androidMain and iosMain (`LiveVideoPlayer` actuals, ADR-005).

### Screen states
- creating · live (video + camera name + "ao vivo" badge + session consumption if available) ·
  reconnecting(n) · expired · quota-exceeded · offline · failed (retry + web fallback) · web-fallback.

### Visual acceptance (previews)
`LiveVideoScreenContent` with a `PreviewParameterProvider`; the player surface renders a placeholder under `LocalInspectionMode` (no Media3/VLCKit in previews); rule 11 guarantees the preview exists.
- `LiveVideoScreen_Creating`, `LiveVideoScreen_Live`, `LiveVideoScreen_Reconnecting` (V3),
  `LiveVideoScreen_Expired`, `LiveVideoScreen_QuotaExceeded` (V6), `LiveVideoScreen_Offline` (V7),
  `LiveVideoScreen_Failed` (V4), `LiveVideoScreen_NoLiveCapability` (V1) — plus `_Dark` variants.

---

## 4. Lock control, volume, history (RF05, RF06, RF09, RF04)

**Goal:** operate a lock safely — see its state, open/close, adjust volume, read the opening history —
while making the hardware's real states visible.
**Out of scope:** passwords (dynamic/periodic/single), enabling remote open as a hidden side effect.

Lock state machine (domain vocabulary borrowed from AWS IoT commands, Home Assistant MQTT Lock and
Seam's `was_confirmed_by_device`): `Locked` · `Unlocked` · `CommandSent` (waiting for the device to
confirm) · `Confirmed` (transient — the confirmation read agreed; the UI settles on Locked/Unlocked) ·
`CommandFailed` (transport/API error, previous state restored) · `CommandExpired` (no confirmation
within **10 s** `[ASSUMED]`) · `Offline` (last known state + "última atualização há X") ·
`RemoteOpenDisabled` (precondition `habilitar-abrir-remoto`).
_EARS adapted to the lock domain vocabulary because no market standard for EARS + mobile IoT exists (see AI-LOG)._

### Acceptance criteria

- **L1** WHEN the lock screen opens, THE SYSTEM SHALL address the lock as `<lockNs>_<hubNs>_<hubIdProduto>`
  with `idProduto` = the lock's own id, and read `status-abertura`, `status-abrir-remoto` and `volume`
  once (3 requests), in parallel. _(test: `LoadLockTest.compositeAddress`, `LoadLockTest.exactlyThreeRequestsInParallel`)_
- **L2** IF `status-abrir-remoto` returns `habilitado: false`, THE SYSTEM SHALL show
  `RemoteOpenDisabled` with the explanation and an explicit "Habilitar abertura remota" action; the
  open/close control SHALL be disabled until enabled. _(test: `LockViewModelTest.remoteDisabledBlocksCommand`)_
- **L3** WHEN the user taps open or close WHILE state is `Locked`/`Unlocked`, THE SYSTEM SHALL move to
  `CommandSent`, disable the control, call `controle-fechadura` with `aberto: true|false`, then read
  `status-abertura` once; WHEN the read agrees with the requested state, THE SYSTEM SHALL pass through
  `Confirmed` and show the new `Locked`/`Unlocked`. _(test: `ToggleLockTest.happyPathConfirmsWithStatusRead`)_
- **L4** IF the command call succeeds but the confirmation read disagrees with the requested state, OR
  no confirmation arrives within 10 s `[ASSUMED]`, THE SYSTEM SHALL show `CommandExpired` with
  "Comando enviado, estado não confirmado" and a "Verificar" action (one more status read), never
  auto-polling. _(test: `ToggleLockTest.disagreementBecomesCommandExpiredNoPolling`, `ToggleLockTest.timeoutBecomesCommandExpired` with a virtual clock)_
- **L5** IF the command fails for network reasons, THE SYSTEM SHALL show `CommandFailed` for the
  duration of a retry snackbar and restore the previous state; IF it is token-rejected, apply S6; IF
  the lock is offline (`status: offline` or the command answers device-offline), THE SYSTEM SHALL show
  `Offline` with the last known state and "última atualização há X" and block new commands.
  _(test: `ToggleLockTest.networkFailureShowsCommandFailedThenRestores`, `ToggleLockTest.offlineShowsLastKnownState`)_
- **L6** WHILE `CommandSent`, WHEN the user taps again, THE SYSTEM SHALL ignore the tap (re-entrancy guard).
  _(test: `ToggleLockTest.ignoresDoubleTapWhileCommandSent`)_
- **L7** Given the volume control (0–3 with labels Mudo/Baixo/Médio/Alto), when the user picks a level,
  then `mudar-volume` is called with that integer and the UI shows the new level only after success.
  _(test: `ChangeVolumeTest.exactRequestAndOptimisticOff`)_
- **L8** WHEN reading the volume, THE SYSTEM SHALL send both `idProduto` and `productId` (contract bug
  quarantine). _(test: `LockRequestsTest.volumeRequestCarriesBothIds`)_
- **L9** WHEN the user opens the history tab, THE SYSTEM SHALL call `historico-abertura` with
  `quantidade: 50` once and list entries newest first with local time and a label for `tipo`
  (`usuarioRemoto` → "Abertura remota (nome)", `interno` → "Abertura local", unknown → raw value).
  _(test: `OpeningHistoryTest.mapsKnownTypes`, `OpeningHistoryTest.unknownTypeShownRaw`)_ `[ASSUMED: only usuarioRemoto and interno were observed; any other tipo is shown raw, never dropped]`
- **L10** IF the history is empty, THE SYSTEM SHALL show "Sem aberturas registradas".
  _(test: `OpeningHistoryTest.emptyState`)_

### Screen states
- loading · locked · unlocked · command-sent · command-failed · command-expired · remote-open-disabled ·
  offline · error; volume: current level + selector; history: loading · list · empty · error.

### Visual acceptance (previews)
`LockScreenContent` and `OpeningHistoryContent`, each with a `PreviewParameterProvider`; rule 11 guarantees the preview exists.
- `LockScreen_Loading`, `LockScreen_Locked`, `LockScreen_Unlocked`, `LockScreen_CommandSent` (L3, L6),
  `LockScreen_CommandFailed` (L5), `LockScreen_CommandExpired` (L4), `LockScreen_RemoteOpenDisabled` (L2),
  `LockScreen_Offline` (L5), `LockScreen_Error` — plus `_Dark` variants (e.g. `LockScreen_Locked_Dark`).
- `OpeningHistory_Loading`, `OpeningHistory_List` (L9), `OpeningHistory_Empty` (L10), `OpeningHistory_Error` — plus `_Dark` variants.

---

## 5. Cross-cutting error handling & UX (RF04)

- **E1** THE SYSTEM SHALL classify on the **HTTP status first** — `401` is token-rejected and `403` is
  token-expired, whatever the body looks like — and only for a `2xx` SHALL it parse the body, accepting
  both envelope shapes (`{statusCode, body}` and flat `{status}`) and treating `status != "sucesso"` as
  an error even with HTTP 200. A body it cannot deserialise SHALL NOT downgrade a `401`/`403` into
  "unexpected response": on those statuses the body is optional, and a **bare JSON string** is an
  accepted shape (ADR-012).
  _(test: `EnvelopeReaderTest.unauthorizedIsTokenRejected`, `.forbiddenIsTokenExpiredWithServerMessage`,
  `.forbiddenWithUnparseableBodyStillExpires`, `.bareJsonStringIsNotUnexpectedResponse`,
  `.wrappedSuccess`, `.flatSuccess`, `.wrappedError404`, `.flatErrorMissingToken`)_
- **E2** THE SYSTEM SHALL map transport failures to at most these user-facing categories: token
  rejected, **token expired**, offline/no network, device not found, quota exceeded, operation rejected
  (with server message), unexpected response. _(test: `ErrorMappingTest.exhaustive`)_
- **E3** THE SYSTEM SHALL never crash on malformed JSON or missing `data`; it SHALL surface
  "Resposta inesperada" with a retry. _(test: `EnvelopeReaderTest.malformedJsonIsUnexpectedResponse`)_
- **E4** THE SYSTEM SHALL rethrow `CancellationException` before any error mapping.
  _(test: `UseCaseCancellationTest.propagates` for each use case)_
- **E5** THE SYSTEM SHALL make no API call on a timer and no automatic retry on token-rejected.
  _(test: request-counter assertions in each use-case test; code review)_
- **E6** All user-facing strings live in Compose resources (pt-BR default, en fallback).
  _(test: `rg` for hard-coded Portuguese strings in `shared/app` returns only resource files)_

---

## 6. Non-functional

- Android minSdk 24, targetSdk 36; iOS deployment target 15.3, simulator build in CI (`workflow_dispatch` / `main`).
- Unit tests run on the JVM via `testAndroidHostTest` (AGP 9 KMP library plugin) on every PR, and on the iOS simulator target in the CI `ios` job.
- Compose UI tests are out of scope for the case: screen states are proven by previews (visual acceptance) and ViewModel tests.
- No token pattern (`Ot_[0-9A-Za-z]{20,}`) anywhere in the repository (CI check, ADR-008). The class is
  alphanumeric, not hexadecimal — the original `[0-9a-f]` could not match a real token (ADR-012).
- Request budget instrumentation: a session counter visible in the account screen (debug builds).

## 7. Open markers (carried from the contract)

| Marker | Where | Blocking? |
|---|---|---|
| `[ASSUMED: renovarToken path/body/response]` | S10 | No — wave 3, verified with one real call before implementing; cut list |
| `[ASSUMED: tipo list = usuarioRemoto, interno]` | L9 | No — unknown types shown raw |
| `[ASSUMED: expiry from first validation]` | S7 | No |
| `[ASSUMED delays]` | V4 | No |
| `[ASSUMED: 10 s confirmation timeout]` | L4 | No — tune on the real lock in wave 3 |
| `[ASSUMED: monitor page works in WebView]` | V9 | No — verify on device in wave 2 |
| ~~Expired token == invalid token response~~ | S3/S6 | **Resolved 2026-09-21 — false.** `401` = rejected, `403` = expired, with a server message fit to show. See ADR-012. |
| `[ASSUMED: 20 s first-frame timeout]` | U1 | No — tune on the real camera in wave 2 |
| `[CHECKPOINT: ADR-005 player plan — confirm or amend on the real camera]` | V2–V5, V8–V10, U1 | No — the video slice PR fills the ADR-005 checkpoint table and amends what does not hold; tracked as a Wave 2 chore issue |
| `[ASSUMED: return to previous destination after re-auth]` | U5 | No |

---

## 8. UX criteria from user research (within RF scope)

Source: `docs/research/user-feedback.md` (App Store RSS 250 reviews 2026-02→09, Play 20 reviews,
Reclame Aqui, official forum; read 2026-09-20). Each criterion names the cluster it answers and refines
an existing section; it never adds a feature outside RF01–RF09. Out-of-scope findings live only in
`docs/PRODUCT.md` §3.1.

- **U1 — bounded video loading (cluster 1 "trava em 9x %", RF03/RF04).** WHILE `StreamState` is
  `Creating` or `Reconnecting`, IF no first frame is reported within **20 s** `[ASSUMED]` from the
  session-creation request, THE SYSTEM SHALL move to `Failed` with "Não foi possível carregar o vídeo",
  "Tentar novamente" and "Abrir no player web"; THE SYSTEM SHALL never display a percentage or an
  unbounded spinner. _(test: `LiveVideoViewModelTest.firstFrameTimeoutBecomesFailed` — virtual clock;
  preview `LiveVideoScreen_Creating` shows the step label, no percentage)_ Refines V3/V4.
- **U2 — two taps to the picture (clusters 6 and 2, RF02/RF03).** WHEN the app starts WITH a stored
  token, THE SYSTEM SHALL render the device list from the cache before any network result (S5) and a
  camera row SHALL open the live screen directly, with no intermediate screen, dialog or banner.
  _(test: `SessionStartTest.cachedListRenderedBeforeNetwork` — state asserted before the fake
  repository answers; navigation test `DeviceListViewModelTest.cameraTapEmitsOpenLiveVideo` — Turbine
  on the one-shot event flow)_ Refines S5/D7.
- **U3 — offline is explained (cluster 4, RF02/RF05).** WHEN a device is `offline`, THE SYSTEM SHALL
  show, next to the indicator, "visto pela última vez há X" derived from `ultimaVezOnline` (or
  "nunca visto online" when absent), in the list row and in the lock `Offline` state.
  _(test: `DeviceUiMapperTest.offlineRowShowsLastSeen`, `DeviceUiMapperTest.offlineWithoutTimestampSaysNever`,
  `LockUiMapperTest.offlineStateCarriesLastSeen`; preview `DeviceListScreen_Success` includes an old
  `ultimaVezOnline`)_ Refines D6/L5.
- **U4 — lock history says who and when (cluster 8, RF09).** WHEN listing the opening history, THE
  SYSTEM SHALL show each entry with relative time ("há 5 min") **and** absolute local time, and the
  actor name whenever `tipo = usuarioRemoto` carries one; entries without a name SHALL read "Abertura
  local" / the raw `tipo`, never blank. _(test: `OpeningHistoryTest.entryShowsRelativeAndAbsoluteTime`
  with a fake clock, `OpeningHistoryTest.remoteEntryShowsActorName`)_ Refines L9.
- **U5 — session expiry keeps context (cluster 11, RF01/RF04).** WHEN S6 routes to the token screen,
  THE SYSTEM SHALL show the reason ("Sua sessão expirou (tokens valem 2 h)") and, after a new token is
  validated, SHALL return to the screen the user was on `[ASSUMED]` (device list by default).
  _(test: `SessionGuardTest.rejectionCarriesReasonAndReturnDestination`,
  `AuthenticateTokenTest.successNavigatesToReturnDestination`)_ Refines S6.
- **U6 — errors name the cause and one action (clusters 1, 4, 11, RF04).** Every user-facing error
  string SHALL state what happened and offer at most one primary action; no string SHALL contain an
  HTTP status code, an exception class name, a stack fragment or the server's raw `msg` except in the
  "operation rejected" category, where the server message is quoted below the friendly title.
  _(test: `ErrorMessagesTest.everyCategoryHasTitleAndAction`, `ErrorMessagesTest.noTechnicalLeakage`
  — regex over the resource strings)_ Refines E2/E3.
- **U7 — no noise (cluster 2, cross-cutting).** THE SYSTEM SHALL contain no promotional, survey or
  interstitial UI and no notification of any kind in this case; the only overlays are the loading and
  error states defined per screen. _(evidence: architecture-test rule — no `NotificationManager` /
  `UNUserNotificationCenter` reference in any module — plus code review of every PR; not a unit test)_
- **U8 — predictable list order (cluster 12, RF02/RF07).** WHEN rendering a page, THE SYSTEM SHALL
  order rows: online cameras and locks first, then other online devices, then offline devices, each
  group by name (locale-aware); the origin filter chips SHALL stay visible in every state including
  empty and error. _(test: `DeviceOrderingTest.onlineActionableFirstThenByName`; previews
  `DeviceListScreen_Empty` and `DeviceListScreen_Error` show the chips)_ Refines D3/D6.
