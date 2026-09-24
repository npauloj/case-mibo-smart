import SwiftUI
import Shared

@main
struct iOSApp: App {
    init() {
        // The real host is a local setting (ADR-008) and reaches iOS with the platform configuration
        // slice; until then the iOS app points at the fictitious host, exactly like Android on CI.
        //
        // Every flag is spelled out because Kotlin default arguments do not cross into Swift: the
        // generated `doInitKoin` requires all of them. The values mirror `MiboSmartApplication`
        // one for one — live video on, and lock writes on (ADR-028).
        AppModulesKt.doInitKoin(
            apiHost: "https://api.example.invalid",
            portalHost: "https://portal.example.invalid",
            liveVideoEnabled: true,
            lockWritesEnabled: true,
            debugBuild: isDebugBuild,
            appDeclaration: { _ in }
        )
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

/// `BuildConfig.DEBUG`'s counterpart on this side: it gates the ADR-006 request counter on the
/// account screen, and nothing else.
private let isDebugBuild: Bool = {
    #if DEBUG
    return true
    #else
    return false
    #endif
}()
