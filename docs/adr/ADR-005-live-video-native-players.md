# ADR-005. Live video via `expect/actual` native players (fMP4), WebView on `monitor_url` as fallback

Status: Accepted (2026-09-20) · Amended 2026-09-20 (player moved from a domain contract to an
`expect @Composable` in `:shared:app`; VLCKit dropped for WKWebView on iOS; background teardown named)
· Amended 2026-09-21 by **V-01b** (the "Mac check" stops being a gate; the surface carries both urls)
· Amended 2026-09-21 by **V-02** (the ladder, the first-frame budget and the null-`monitor_url` rule)
· **Checkpoint closed** — see the last section for what was observed and what was not.

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
  `expect @Composable fun LiveVideoPlayer(url: String, monitorUrl: String?, onEvent: (PlayerEvent) -> Unit, modifier: Modifier)`
  with `PlayerEvent = FirstFrame | Ended | NetworkError | DecodeError`. Under `LocalInspectionMode`
  it renders a placeholder so previews never touch a real player.
  **The surface takes both urls of the session** (amended 2026-09-21, V-01b): the two platforms play
  different things, and which one a platform needs is the actual's business, not the screen's. The
  screen passes `session.url` and `session.monitorUrl` and stays identical on both.
  - `androidMain` actual: Media3 `ExoPlayer` with `ProgressiveMediaSource` (fMP4 extractor), owned by
    `remember` + `DisposableEffect`, released on dispose. It ignores `monitorUrl`.
  - `iosMain` actual: `UIKitView` hosting a `WKWebView` on `monitorUrl` (the partner's own player
    page), **unconditionally**. VLCKit was dropped: binary size, a CocoaPods/SPM step and Objective-C
    interop do not fit the time box.
    **Amended 2026-09-21 (V-01b): the "Mac check" of `monitor_url` is no longer a gate.** It was one
    in the first draft — "if it fails, iOS shows Safari only" — and that blocked two slices at the
    dispatch gate, because no agent can obtain the result and a ticket that waits on a human
    observation cannot be executed. The answer is in the product instead, and it is observable at
    runtime: the actual reports `FirstFrame` **only when a `<video>` element on the page actually
    starts playing** (a `WKUserScript` probe posting on a `WKScriptMessageHandler` channel), never on
    "the page finished loading". A page that loads but plays nothing therefore runs out U1's
    first-frame budget, the screen moves to `Failed`, and `Failed` offers "Abrir no player web"
    (SPEC V10, SPEC U1, V-02). That is correct whether the page plays or not, so nothing in the code
    depends on knowing which. A session whose `monitor_url` is null reports `DecodeError` at once —
    there is nothing to play, and an unplayable stream must not keep spending quota.
- **The ViewModel owns the session, the composable owns the player.** `LiveVideoViewModel`
  (`:shared:app`, `camera` package) runs `WatchLiveVideo`: confirm `RTSV` capability (one `funcoes`
  call, cached), create the session with `stream_gb = 0.5`, `streamId = 1`, `canalVideo = 0`, publish
  `StreamState.Live(url)` in the same coroutine so the composable attaches within the 15 s window, and
  map `PlayerEvent`s through `PlaybackRetryPolicy`: decode error → no retry (fallback offered);
  network drop / end of stream → retry with backoff, first reusing the URL, then creating a new session
  (cap: 2 session creations per screen visit). Teardown (`encerrar-sessao` under `NonCancellable`,
  `withTimeout(5 s)`) runs from a `SupervisorJob` app scope so it survives the ViewModel being cleared
  (`viewModelScope` is cancelled too early to await a network call).
- **The wait is bounded, and so is what it costs** (amended 2026-09-21, V-02). The rule lives in
  `:shared:domain` as `PlaybackRetryPolicy`, a pure function of the failure and of the attempts
  already made, so the whole ladder is asserted on a virtual clock without a player or a camera:
  - a drop or an end of stream retries **three** times, after 1 s / 3 s / 7 s `[ASSUMED]`. The first
    attempt re-prepares the url the app already has and costs **no** partner request; the other two
    create a session, which is why the per-visit ceiling is **three creations in total** — the one
    that opens the screen plus the ladder's two (ADR-006). A foreground return (`ON_START`) spends
    from the same allowance instead of resetting it.
  - a decode or format error never retries (SPEC V5): the same decoder on the same bytes fails the
    same way, and paying two creations to prove it is the waste ADR-006 exists to prevent.
  - every attempt gets **20 s** `[ASSUMED]` to draw a first frame (SPEC U1); when it does not, the
    screen moves to `Failed` and the session is ended. The budget covers the ladder's wait as well as
    the attempt, because the user is looking at a screen that is not playing either way.
  - what the ceiling bounds is what the app spends **by itself**. SPEC V4 requires the failed screen
    to offer "Tentar novamente", so a tap starts the allowance over — otherwise the one action the
    state offers would be the dead button SPEC V6 refuses. Nothing here retries without being asked.
- **A null `monitor_url` has no fallback, and the screen says so** (amended 2026-09-21, V-02). The
  fallback surface is `WebPlayerFallback` — an `expect @Composable` beside the player in
  `app.camera.platform` (rule 8): an in-app `WebView` on Android, and on iOS the **system browser**,
  because the player surface there already is a `WKWebView` on this very url (SPEC V9, V10).
  `StreamSession.monitorUrl` is `String?` and no probe has ever seen a real one, so
  `StreamState.Failed` carries it and the screen offers "Abrir no player web" **only** when it is not
  null. This closes the loop this ADR opened: on iOS a session without a monitor page reports
  `DecodeError` at once, and SPEC V5 sends a decode error to the fallback — which would have had
  nothing to load. With no url the screen shows "Tentar novamente" alone, which on Android may still
  work and on iOS is the honest answer.
  - **Open, and not decidable from here:** the session is ended *before* the fallback opens, because
    an unplayable stream must stop spending quota (SPEC V8) and a session nobody closes bills until
    the partner's own cap. Whether the monitor page still plays after `encerrar-sessao` is unknown —
    it is part of the manual check of SPEC V9. If it does not, the fix is to keep the session open
    while the fallback is on screen and close it on the way out, and it belongs in this ADR.
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

## Checkpoint — closed 2026-09-21 by V-01b

This ADR was written from the contract and from library documentation, before any frame was played.
The video slices have now landed, so the table below says, row by row, **what was observed and what
was not** — and names who observes the rest. Filling it is the point of the checkpoint; pretending a
row was observed would be worse than leaving it open.

Two things bound what this row could ever say here. The machine that wrote the code is a **Windows**
machine, so iOS is cross-compiled and never executed (ADR-013 — "compiled, not executed"); and the
macOS CI job **does not run on pull requests** (ADR-009 — 10× minutes on a private repo), so no PR in
this project can claim a green iOS build. The remaining rows need the partner's real camera, which no
automated check in this repository reaches.

| What to confirm | Planned | Observed? | Outcome |
|---|---|---|---|
| Media3 plays the fMP4 stream as-is | `ProgressiveMediaSource` + default extractors, no custom `MediaSource` | **Not observed** — needs the real camera on a device | Kept as planned. Observed by whoever runs the demo build against the test camera; if it needs an explicit `FragmentedMp4Extractor` or a `DataSource` with headers, amend here and fall back to the Android WebView on `monitor_url` |
| Time to first frame | < 15 s so the session does not expire; U1 timeout 20 s | **Not observed** — needs the real camera | Both numbers stay `[ASSUMED]` in the SPEC. Observed by the demo run; the 20 s budget is enforced in code by V-02 on a virtual clock, which proves the *mechanism*, not the number |
| Background teardown | `LifecycleEventEffect(ON_STOP)` → `stop()`; session ended within 5 s | **Partly observed** — the wiring, on the JVM host | `LiveVideoScreenLifecycleTest` (fake `LifecycleOwner`) and `LiveVideoViewModelTest.stopEndsSessionAndDetachesPlayer` / `.teardownUsesAppScopeNotViewModelScope` pass. Multi-window / PiP behaviour on a device: not observed, observed by the demo run |
| Retry policy | 3 retries (1 s, 3 s, 7 s), first same URL then new session, cap 2 creations | **Implemented, not observed** — V-02, on a virtual clock | `PlaybackRetryPolicy` + `LiveVideoViewModelTest.ladderCreatesAtMostTwoExtraSessions` / `.firstFrameTimeoutBecomesFailed` pass on the Android host: the *mechanism* is proven, the *numbers* are not. Delays and the 20 s budget stay `[ASSUMED]`. Whether re-preparing the same url works at all — the stream expires 15 s after it is minted — is a property of the partner, observed by the demo run; if it never does, the first rung should be dropped and the ceiling with it |
| iOS surface | WKWebView on `monitor_url` | **Observed, in part — and amended** | The actual is implemented unconditionally and **compiles for both iOS targets** from Windows: `:shared:app:compileKotlinIosArm64` and `:compileKotlinIosSimulatorArm64` are `BUILD SUCCESSFUL`, and so is `:compileTestKotlinIosSimulatorArm64`. `linkDebugFrameworkIosSimulatorArm64` is `SKIPPED` on a non-Apple host — this is a compile check, not a link and not a run. `LiveVideoPlayerIosTest` exists and executes **only** on the macOS job. **Whether the monitor page plays on a real iPhone is not observed and is no longer a gate**: see the amendment in Decision — the page is probed for a real `playing` event, so U1's timeout catches a page that shows nothing and the product answers it with "Abrir no player web" |
| Session cost | `stream_gb = 0.5`, `streamId = 1` | **Not observed** — needs the real camera | Kept. Observed by the demo run; a stream that ends too early → 1 GB, a substream that looks too poor → `streamId = 0` |

Who observes what is left: the **author running the demo build against the partner's test camera**
(every "needs the real camera" row) and the **macOS CI job on `main` or `workflow_dispatch`** (the iOS
simulator tests). Nothing above is a reason to skip the slice — the web fallback is the floor that
keeps RF03 demonstrable while a better native path is evaluated.
