# ADR-024. Screenshot tests read the previews, and their goldens are born in CI

Status: Accepted (2026-09-22) — delivers the screenshot tests `CLAUDE.md` had listed as planned

## Context

Until now the previews were the visual evidence: 49 `@Preview` functions across six screens, one per
state, written as each slice landed. Nothing verified them. A preview proves a composable *compiles*
and can be eyeballed in the IDE; it does not notice when a change makes a screen unreadable, and
nobody opens 49 of them by hand before a merge.

That gap became expensive the moment a redesign was on the table. Changing a theme touches every
screen at once, and this repository has no UI tests at all — the only composition test in the tree
(`LiveVideoScreenLifecycleTest`) drives a UI-less composable with a no-op applier to check lifecycle
wiring, and measures nothing about what is drawn.

Three dependencies are new and each needs the line `CLAUDE.md` asks for: **Roborazzi** (the capture and
comparison), **Robolectric** (the host-side Android runtime it composes against), and
**ComposablePreviewScanner** (a transitive requirement of Roborazzi's preview support). All three are
`androidHostTest`-only and reach no production artifact.

## Decision Drivers

- The set of states under test must not be a second list that drifts from the previews. The previews
  already *are* the inventory; a hand-kept copy of it would be wrong within two slices.
- A golden that silently records the wrong thing is worse than no golden: it passes forever while
  nobody looks. Locale and device have to be pinned, deliberately, not defaulted.
- Goldens must be comparable. Font rasterisation differs between operating systems, so an image
  recorded on a developer's Windows machine and compared on a Linux CI runner differs in every
  anti-aliased pixel for reasons that have nothing to do with the UI.
- No new dependency may reach production.

## Considered Options

1. **Roborazzi's preview-test generator** (`generateComposePreviewRobolectricTests`). The obvious
   choice: it scans the previews and writes the tests, so there is no list to keep at all.
   **Rejected — it does not work in this project, measured rather than assumed.** Enabling it produces
   124 compilation errors in files it never touches: `commonTest` loses `:shared:data` and Koin. It
   generates into a source set it names `androidMain`, and registering that breaks the association a
   KMP `androidLibrary` target keeps between its host tests and its main compilation. With the
   generator off, the same tree compiles. The generator assumes a plain Android project with
   `testImplementation`; this is a Kotlin Multiplatform module with an Android target.
2. **Hand-written captures, one test per state.** Rejected: 49 tests naming 49 states is exactly the
   drifting second list.
3. **Hand-written captures that iterate the `PreviewParameterProvider`s, with a drift guard.**
   Chosen.
4. **Paparazzi instead of Roborazzi.** Not evaluated in depth: Paparazzi does not support Compose
   Multiplatform, which is what this module is.

## Decision

**Option 3.** `ScreenshotTest` names the states per screen and then asserts that it named all of them:

```kotlin
assertEquals(provider.values.count(), states.size, "$screen has states the goldens do not cover …")
```

Add a state to a provider without adding it here and the test fails. The golden set cannot fall behind
the previews in silence, which is the property the generator would have given for free and the reason
this is not simply a worse version of option 2.

Three settings are load-bearing, and each was found by looking at an image rather than at a log:

- **`GraphicsMode.NATIVE`.** The legacy mode draws nothing. A full set of blank goldens compares green
  forever.
- **`qualifiers=pt-rBR-…` in `robolectric.properties`.** Robolectric defaults to `en`, and the first
  capture came out in English off `values-en/`. The app ships pt-BR (SPEC E6); goldens of the fallback
  language would have verified a screen no user sees.
- **`robolectric.pixelCopyRenderMode=hardware`.** Roborazzi asks for it explicitly; without it the
  capture path is lower fidelity and images differ between machines.

**Goldens are recorded and compared on the CI Linux runner only, and never committed from a developer
machine.**
 `shared/app/src/androidHostTest/goldens/` is in `.gitignore` to enforce that mechanically —
an ignore with an expiry date, written in the file: it comes out the day a baseline recorded by this
job is committed, and the set turns from pictures attached to a PR into a real regression gate. The `verify` job records them and uploads them as a build artifact; a PR that changes the UI
carries its images with it, so a reviewer sees the screens instead of reading a diff and imagining
them. The `assertEquals` drift guard runs on every machine, because it needs no image.

## Consequences

- (+) A redesign becomes reviewable. Six screens and their states arrive as pictures attached to the
  PR that changed them.
- (+) The previews stop being decorative: they are now the source the tests read, so a state without a
  preview is a state without a golden, and the existing convention gains teeth.
- (+) The drift guard runs everywhere and costs nothing — it is an integer comparison.
- (−) **The state list is still written twice**, once in the provider and once in `ScreenshotTest`. The
  guard catches the count, not the naming: swapping two names would pass. This is the price of option
  1 not working, and it is worth revisiting whenever Roborazzi supports the KMP Android layout.
- (−) Recording is heavy. A full run composes every state under Robolectric and was killed once for
  memory pressure on a developer machine; CI is where it belongs anyway.
- (−) Three more dependencies to keep current, all test-only.
- (−) The Roborazzi Gradle DSL is `@ExperimentalRoborazziApi` and emits an opt-in warning. Left visible
  rather than suppressed: the API *is* experimental, and hiding that would misrepresent it.

## Confirmation

- `./gradlew :shared:app:testAndroidHostTest` passes the drift guard on any machine.
- The CI `verify` job runs `recordRoborazziAndroidHostTest` and uploads `shared/app/src/androidHostTest/goldens/`.
- No production artifact gains a dependency:
  `git diff --stat -- gradle/libs.versions.toml shared/*/build.gradle.kts` shows the three additions
  confined to the `androidHostTest` source set.
