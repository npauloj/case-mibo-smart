# ADR-025. The streaming calls go to the portal host, and the api host is the wrong address for them

Status: Accepted (2026-09-23) — confirms ADR-005 rather than amending it; extends the single-host
assumption of ADR-008

## Context

Live video never worked on a real camera. The screen said "Resposta inesperada", then — after the DTO
was loosened — that the stream had failed. Four layers of cause were peeled off in one session, each
hiding the next, and only the last one mattered.

The partner platform has **two hosts**, both already in `local.properties`: `smarthome.apiHost` and
`smarthome.portalHost`. The app only ever used the first. `docs/api-contract.md` §6 was the one
section built from the Swagger instead of from the wire, and it said so, in what turned out to be the
most expensive line in the repository: *"Not probed — each call opens a real session and spends
quota."*

`POST /cameras/criar-fluxo-video/v1` **exists on both hosts and answers differently on each**:

| Host | Answer |
|---|---|
| `<PORTAL_HOST>` | the documented shape — `url` (fragmented MP4 over HTTPS), `monitor_url`, `session_id`, `quota_gb`, `warning` |
| `<API_HOST>` | `{"url": "rtsp://<proxy>:8554/<session>"}` and nothing else |

Both return `200`. Both return a url. Neither reports an error. **No code in the app could have told
them apart**, and no `MockEngine` test could either: the fixtures were written from the Swagger, so
they described the portal's answer while the app talked to the api host.

What the api host's url actually is, measured with a hand-written RTSP client because Media3 logs
nothing about the SDP it receives:

- `DESCRIBE` → `200`, SDP for H.265 on one camera and H.264 on the other, audio MPEG4-GENERIC.
- **No `a=fmtp` on any track**, so Media3 refuses in `RtspClient.buildTrackList`
  (`missing attribute fmtp`): it requires `sprop-parameter-sets` / `config`, and this server
  (`a=packetization-supported:DH`, a Dahua stack) sends the parameter sets in-band instead.
- `SETUP` and `PLAY` → `200`, over TCP interleaved and over UDP, on both tracks, for `streamId` 0 and
  1 and `canalVideo` 0, 1 and 2.
- **Zero RTP packets in 45 s**, and the server's own RTCP sender report declares `0 packets / 0 bytes
  sent`.

So that endpoint speaks the control protocol correctly and never carries media. It also carries no
`session_id` — and that is the part that did real damage. `encerrar-sessao` accepts no other handle,
so SPEC V8's teardown could never run: **27 sessions were left open on an account several people
share**, found only by calling `streaming/minhas-sessoes/v1`, which is also the only place their ids
can be recovered.

The three other streaming endpoints answer `403 {"message":"Forbidden"}` on the api host. That is the
gateway refusing an unknown route, not the platform refusing the token, and it is the cheapest way to
tell the two hosts apart.

## Decision Drivers

- A wrong host must fail loudly. Today it fails by returning `200` forever.
- The app must never be able to open a session it cannot close. Not a style preference: the account is
  shared and finite, and the failure is invisible from inside the app.
- ADR-008 withholds both hosts from version control. Whatever is chosen keeps them in
  `local.properties` and out of the repository.
- The fix must not be "make the DTO tolerant". Tolerance is what turned a loud parse failure into a
  silent session leak.

## Considered Options

1. **Keep one base url and switch hosts inside the camera repository.** Rejected: the knowledge would
   sit in a feature, and the next feature needing a portal endpoint (`cota-disponivel`,
   `minhas-sessoes`, `sessao-info`) would rediscover it.
2. **Make `session_id` optional and run without teardown.** Tried, for two hours, and it is what
   produced the 27 leaked sessions. Rejected, and recorded in `AI-LOG.md`: the parse failure was a
   correct alarm about a wrong address, and silencing an alarm is not a fix.
3. **Add `media3-exoplayer-rtsp` and play the rtsp url.** Rejected on measurement: that url carries no
   media, so the dependency would only have changed the error message. It was added during the
   investigation and has been reverted.
4. **Swap Media3 for libVLC, which tolerates an SDP with no `fmtp`.** Rejected for the same reason,
   and it would have cost an ADR-005 reversal, tens of megabytes of native libraries, and an iOS story
   (VLCKit) that ADR-005 had already declined.
5. **A second base url on the transport, chosen per endpoint.** Chosen.

## Decision

**Option 5.** `SmartHomeApi` takes a `streamingBaseUrl` beside `baseUrl`, and its private `post` takes
the host as a parameter defaulting to `baseUrl`. Exactly two call sites pass the other one:
`criar-fluxo-video` and `encerrar-sessao`. `dataModule` and `appModule` take `portalHost` alongside
`apiHost`; both entry points read it where they already read the api host
(`BuildConfig.SMARTHOME_PORTAL_HOST` on Android, the initialiser on iOS).

**`portalHost` has no default value.** Defaulting it to `apiHost` would reproduce this defect in
silence, which is exactly the property that let it survive a full day of debugging.

**`StreamSessionDto.session_id` stays required.** A nullable id is a session the app can open and not
close.

`WatchLiveVideoTest.streamingCallsGoToThePortalHostAndTheRestDoesNot` asserts the host of every
request the streaming path makes. It is the only mechanical defence available, because the partner
will keep answering `200` from the wrong host.

**ADR-005 is confirmed, not amended**: Media3 on a fragmented-MP4 HTTP stream was the right choice,
asked at the wrong address.

## Consequences

- (+) `session_id`, `monitor_url` and `quota_gb` come back, so SPEC V8's teardown, SPEC V9's web
  fallback and the live screen's quota line stop being dead code.
- (+) The app can no longer leak sessions by construction.
- (+) `streaming/minhas-sessoes/v1` is now known to exist and to be the only way to recover a session
  id after the fact. It is what the 27 were closed with.
- (−) Two hosts to configure instead of one. A machine with only `smarthome.apiHost` set falls back to
  a fictitious portal host and live video fails offline — loudly, which is the intent.
- (−) The api host's `403 {"message":"Forbidden"}` is indistinguishable from a real authorisation
  failure by status alone. Nothing in the app depends on telling them apart today.

## Confirmation

- `./gradlew :shared:data:testAndroidHostTest --tests "*WatchLiveVideoTest*"` — the routing assertion.
- `./gradlew :konture-test:test :shared:domain:testAndroidHostTest :shared:data:testAndroidHostTest
  :shared:app:testAndroidHostTest :androidApp:assembleDebug` — green on 2026-09-23.
- `docs/api-contract.md` §6 carries the measured answers from both hosts, the SDP, and the RTCP
  accounting.

## `[OPEN]` The upstream was empty when this was written

With the host fixed, connecting to `/stream/<session>` **0.1 s** after creating the session returns
`200 video/mp4 chunked` and then **EOF after exactly 15 s with zero bytes**, on both cameras, every
channel and every profile. The documented "expires if not opened within 15 s" is the transcoder giving
up on *its* upstream, not on the client.

This is not believed to be permanent. `minhas-sessoes` showed ten sessions from earlier the same day
with real consumption — 0.036 GB / 325 s, 0.051 GB / 470 s, 0.067 GB / 617 s, a constant ~0.87 Mbit/s
— and a session that receives nothing reports `mb_consumed: 0.0`, so consumption counts bytes
**delivered to a client**. The path carried video that day and then stopped.

**A picture on a device is therefore still unconfirmed.** The code is correct against the contract as
measured; what it decodes has not been seen.
