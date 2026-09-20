# ADR-005. Live video via `expect/actual` native players (fMP4), WebView on `monitor_url` as fallback

Status: Accepted (2026-09-20) · Amended 2026-09-20 (player moved from a domain contract to an
`expect @Composable` in `:shared:app`; VLCKit dropped for WKWebView on iOS; background teardown named)
· **Checkpoint open until the video slice lands (wave 2)** — see the last section.

## Context

RF03 (mandatory) is live video. The Swagger reveals the stream format: `criar-fluxo-video` returns
`data.url`, a **fragmented MP4 over HTTP** intended for MediaSource Extensions — not RTSP, not HLS —
plus `data.monitor_url`, a ready HTML page with a player. Constraints from the contract
(`docs/api-contract.md` §6): the stream expires if not opened within **15 seconds** of creation, ends
automatically at `stream_gb`, quota exhaustion is a 402, and only consumed bandwidth is debited.
The account has 2 GB of streaming and a request budget (ADR-006).

The Sept-2026 KMP survey found no mature multiplatform video abstraction for this format (MediaMP and
KMP-Player target VOD/HLS); `AVPlayer` does not play a raw live fMP4 HTTP stream without an HLS
manifest, while Media3/ExoPlayer can via `ProgressiveMediaSource` + fMP4 extractor, and VLCKit plays
it on iOS.

## Decision Drivers

- Must work on Android first (primary target), compile on iOS (proof of sharing).
- Session lifecycle must be atomic: create → attach player within 15 s; end session on exit.
- Quota safety during demo and tests.

## Considered Options

1. Native player surface as an `expect @Composable` in the app layer: Media3 on Android, a native
   view on iOS.
2. WebView loading `monitor_url` on both platforms only.
3. A third-party KMP player library.
4. A `VideoPlayer` interface in `:shared:domain` with actuals in the platform apps (first draft —
   rejected: a player is a UI concern, not a business rule, and the platform apps cannot host actuals,
   see ADR-001).

## Decision

Option 1 as the default on Android, with Option 2 as the explicit, user-visible fallback ("Abrir no
player web"), as the automatic path when the native player reports an unrecoverable decode error, and
as the **only** path on iOS.

- **The player is a UI surface, not a domain contract.** `:shared:domain` knows only
  `StreamingRepository` (`createSession`, `endSession`, `quota`) and the `StreamSession` value
  (`url`, `monitorUrl`, `sessionId`, `expiresAt`). Nothing in the domain mentions playback.
- `:shared:app`, package `camera.platform`, declares
  `expect @Composable fun LiveVideoPlayer(url: String, onEvent: (PlayerEvent) -> Unit, modifier: Modifier)`
  with `PlayerEvent = FirstFrame | Ended | NetworkError | DecodeError`. Under `LocalInspectionMode`
  it renders a placeholder so previews never touch a real player.
  - `androidMain` actual: Media3 `ExoPlayer` with `ProgressiveMediaSource` (fMP4 extractor), owned by
    `remember` + `DisposableEffect`, released on dispose.
  - `iosMain` actual: `UIKitView` hosting a `WKWebView` on `monitorUrl` (the partner's own player
    page). VLCKit was dropped: binary size, a CocoaPods/SPM step and Objective-C interop do not fit
    the time box, and the Mac check of `monitor_url` in WKWebView (SPEC V9, iOS 17.1+
    `ManagedMediaSource`) is the gate — if it fails, the iOS screen shows the "Abrir no player web"
    action opening Safari and this ADR is amended.
- **The ViewModel owns the session, the composable owns the player.** `LiveVideoViewModel`
  (`:shared:app`, `camera` package) runs `WatchLiveVideo`: confirm `RTSV` capability (one `funcoes`
  call, cached), create the session with `stream_gb = 0.5`, `streamId = 1`, `canalVideo = 0`, publish
  `StreamState.Live(url)` in the same coroutine so the composable attaches within the 15 s window, and
  map `PlayerEvent`s through `PlaybackRetryPolicy`: decode error → no retry (fallback offered);
  network drop / end of stream → retry with backoff, first reusing the URL, then creating a new session
  (cap: 2 session creations per screen visit). Teardown (`encerrar-sessao` under `NonCancellable`,
  `withTimeout(5 s)`) runs from a `SupervisorJob` app scope so it survives the ViewModel being cleared
  (`viewModelScope` is cancelled too early to await a network call).
- **Background is an explicit trigger.** The screen installs `LifecycleEventEffect(ON_STOP)` (multiplatform
  lifecycle 2.9.x) that calls `viewModel.stop()`: player detached, stream job cancelled, session ended.
  `ON_START` re-creates the session if the screen is still visible. This is the mechanism behind SPEC
  V8 — a phone in a pocket must not burn streaming quota.

## Consequences

- (+) Correct format support on Android; on iOS the partner's page carries the decoding burden.
- (+) The WebView fallback guarantees a working demo even if the native player misbehaves.
- (+) Domain stays player-free; the only expect/actual is a composable in one `platform` package.
- (−) Two player surfaces to maintain (Media3, WKWebView); iOS playback quality depends on the
  partner's page.
- (−) Playback cannot be unit-tested; the retry policy, the teardown and the lifecycle trigger can
  (events come from a fake source in tests).

## Confirmation

- Unit tests for `LiveVideoViewModel` with a fake `StreamingRepository` and a fake event source:
  `StreamState.Live` published in the same coroutine as the session creation (no other suspension),
  `encerrar-sessao` called on cancel and on `stop()` (ON_STOP), at most 2 session creations per visit,
  retry policy per event type, 402 → `QuotaExceeded`, first-frame timeout → `Failed` (SPEC U1).
- Manual check on the demo device: video appears in < 15 s after tapping the camera; leaving the
  screen or backgrounding the app makes `minhas-sessoes` return no session for that camera.
- Criteria touching the player (`SPEC` V2, V8, V10) carry `Applies to: androidMain and iosMain`; the
  iOS half is only proven by the macOS CI job.

## Guardrail

- Guardrail: rule 4 — `shared code stays portable` (`sourceSet("commonMain") { mustBePlatformIndependent() }`) in `:konture-test`
- Guardrail: rule 8 — `platform bridges live in one place` (`expect`/`actual` only in packages named
  `platform`: `data.platform.*`, `app.<feature>.platform`) in `:konture-test`
- Guardrail: rule 1 — the domain must not reference `LiveVideoPlayer`, `PlayerEvent` or any Media3 /
  WebKit symbol (already covered by domain purity).

## Checkpoint — confirm or amend with the real camera (wave 2)

This ADR was written from the contract and from library documentation, before any frame was played.
It is deliberately **not final**: the video slice (RF03, epic "Vídeo ao vivo") must confirm each line
below against the real camera and either keep the decision or amend this file *in the same PR*
(section "Decisões de arquitetura" of the PR template). Tracked as a chore issue in milestone Wave 2.

| What to confirm | Planned | Amend if… |
|---|---|---|
| Media3 plays the fMP4 stream as-is | `ProgressiveMediaSource` + default extractors, no custom `MediaSource` | it needs an explicit `FragmentedMp4Extractor`, a `DataSource` with headers, or does not play at all → try `HlsMediaSource`, then the Android WebView on `monitor_url` as the primary path |
| Time to first frame | < 15 s so the session does not expire; U1 timeout 20 s | first frame regularly takes > 10 s → lower `stream_gb`, or move the timeout; if the session expires before the player attaches, create the session *inside* the composable's effect |
| Background teardown | `LifecycleEventEffect(ON_STOP)` → `stop()`; session ended within 5 s | `ON_STOP` fires late or twice on multi-window / PiP → use `repeatOnLifecycle(STARTED)` around the stream job instead |
| Retry policy | 3 retries (1 s, 3 s, 7 s), first same URL then new session, cap 2 creations | the stream URL is single-use (re-prepare fails) → skip the same-URL step; the cap starves a flaky network → raise to 3 |
| iOS surface | WKWebView on `monitor_url` (Mac check, V9) | the page needs `ManagedMediaSource` and fails on the simulator → "Abrir no player web" in Safari only, iOS stays list + lock |
| Session cost | `stream_gb = 0.5`, `streamId = 1` | 0.5 GB ends the stream too early in the demo → 1 GB; substream looks too poor → `streamId = 0` |

Nothing above is a reason to skip the slice: the WebView fallback is the floor that keeps RF03
demonstrable while a better native path is evaluated.
