# ADR-013. Verify every iOS `expect/actual` by cross-compiling from the dev machine

Status: Accepted (2026-09-21)

## Context

This project's iOS story has a hole that only became visible when a slice tried to fall into it.

`docs/guides/running.md` treats iOS as "open `iosApp/` in Xcode", and `.github/workflows/ci.yml` runs
the macOS job **only on pushes to `main` or by hand** — macOS minutes cost 10× on a private repository
(ADR-009's CI shape). The consequence: an `expect` declaration whose `actual` is missing, or wrong, for
an iOS target is **not checked by anything before merge**. The PR goes green, `main` breaks, and the
break is found by the next push to `main` — after the merge gate, which is the one place this process
puts human judgement.

That is not hypothetical. The pre-dispatch gate rejected slice **V-01a** for exactly this: its text
declared `expect @Composable LiveVideoPlayer` while deferring the `iosMain` actual to a later slice. A
Kotlin `expect` with no `actual` for a declared target does not compile, so the PR would have merged
green and broken `main` for every iOS build.

In the same wave, the worker on **S-01b** (PR #37) found the missing check without being asked. On this
**Windows** machine:

```
./gradlew :shared:data:compileKotlinIosSimulatorArm64 \
  -Pkotlin.native.enableKlibsCrossCompilation=true \
  -Pkotlin.native.ignoreDisabledTargets=false
BUILD SUCCESSFUL
```

Kotlin/Native cross-compiles the iOS targets from a non-Apple host. It compiled the Keychain `cinterop`
actual — the piece nobody could verify before — and it changed that slice's outcome: the worker's first
draft had *avoided* the real `Security.framework` interop precisely because it was unverifiable, and
reverted to the correct implementation once it could be compiled.

## Decision Drivers

- Catch a broken iOS `expect/actual` **before** the human merge gate, not after `main` is red.
- Do not buy macOS CI minutes on every PR — ADR-009 priced that deliberately.
- Keep the check mechanical, so a ticket can require it and a reviewer can read its output.

## Considered Options

1. **Leave it to the macOS job.** Free, and exactly the hole above: the check runs after merge.
2. **Run the macOS job on pull requests.** Closes the hole at 10× minutes for every PR, most of which
   touch no iOS code. ADR-009 already rejected that cost.
3. **Cross-compile the iOS targets locally, as a required step for any slice touching
   `expect`/`actual`/`iosMain`.** Free, runs on the machine the work happens on, and compiles the real
   thing — cinterop included.

## Decision

Option 3, with a bounded scope.

- **Any slice that adds or changes an `expect`, an `actual`, or anything under `iosMain`** must pass,
  before its PR is opened:

  ```
  ./gradlew :<module>:compileKotlinIosSimulatorArm64 \
    -Pkotlin.native.enableKlibsCrossCompilation=true \
    -Pkotlin.native.ignoreDisabledTargets=false
  ```

  and paste the result into the PR body under Evidência. The ticket states it; the gate can check that
  the ticket states it.
- **An `expect` and actuals for every declared target land in the same PR.** A placeholder `actual` is
  an acceptable first version — V-01a ships one for iOS — but "the actual comes in a later slice" is
  not, because the compiler disagrees.
- **This is a compile check, not a test run.** It proves the code exists, resolves and type-checks for
  the target; it proves nothing about behaviour. Simulator tests stay in the macOS CI job, and a PR
  must not claim iOS was "verified" on the strength of this alone — the wording is "compiled, not
  executed".
- CI is unchanged. The macOS job keeps its `main` / `workflow_dispatch` trigger and remains the place
  where iOS *runs*.

## Consequences

- (+) The class of failure that motivated this ADR — a missing or wrong iOS actual merging green —
  is caught on the machine that wrote it, seconds after writing it.
- (+) Platform-specific code that was previously written defensively because it could not be checked
  (the Keychain cinterop) can now be written properly. That already happened once, in PR #37.
- (+) Zero added CI cost; ADR-009's macOS trigger stands.
- (−) One more required command per iOS-touching slice, and its output in the PR body.
- (−) The first cross-compile downloads the Kotlin/Native toolchain for the target, so it is slow once
  and fast after. `~/.konan` is already cached in CI for the same reason.
- (−) Compiling is weaker than running. A wrong `kSecAttrAccessible` value compiles fine. The manual
  device/Mac steps that ADR-008 §Confirmation prescribes are still the proof of behaviour.

## Confirmation

- PR #37 carries the command's real output for `:shared:data`, including the Keychain actual.
- Every ticket touching `expect`/`actual`/`iosMain` names the command under Technical detail —
  V-01a and V-01b do; a ticket that does not is a ticket the gate should send back.
- `docs/guides/running.md` §2 lists the command beside the Android ones, so it is discoverable without
  reading this ADR.
