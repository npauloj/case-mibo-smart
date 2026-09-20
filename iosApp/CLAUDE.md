# iosApp/ — iOS entry point (proof that the shared module is really shared)

SwiftUI host for the Kotlin `Shared` framework produced by `:shared:app`: `iOSApp.swift` starts Koin
(`AppModulesKt.doInitKoin(appDeclaration: { _ in })`), `ContentView.swift` wraps `MainViewControllerKt.MainViewController()`.
The Xcode build phase runs `./gradlew :shared:app:embedAndSignAppleFrameworkForXcode`.

## Rules

- Android is the primary target; iOS must build and show the device list. There is **no Kotlin `actual`
  in this folder** — a Swift target cannot host one. The video surface (`WKWebView` on `monitor_url`)
  is the `iosMain` actual of `LiveVideoPlayer` in `:shared:app` (package `camera.platform`, ADR-005);
  the Keychain token vault is the `iosMain` actual in `:shared:data` (package `platform.vault`, ADR-008).
- Anything that needs a Mac (Xcode, simulator) is verified in the CI `ios` job or on the borrowed Mac —
  do not assume it can be checked on Windows.
- Keep `Config.xcconfig` free of team/secret values; `TEAM_ID` stays empty in the repository.

## Build

- Locally (Mac): open `iosApp/iosApp.xcodeproj` in Xcode and run the `iosApp` scheme on a simulator.
- CI: `./gradlew :shared:app:linkDebugFrameworkIosSimulatorArm64` then
  `xcodebuild -project iosApp/iosApp.xcodeproj -target iosApp -sdk iphonesimulator -arch arm64 CODE_SIGNING_ALLOWED=NO build`
  (`-target`, not `-scheme`: no shared scheme is versioned).
