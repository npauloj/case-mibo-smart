import SwiftUI
import Shared

@main
struct iOSApp: App {
    init() {
        // The real host is a local setting (ADR-008) and reaches iOS with the platform configuration
        // slice; until then the iOS app points at the fictitious host, exactly like Android on CI.
        AppModulesKt.doInitKoin(apiHost: "https://api.example.invalid", appDeclaration: { _ in })
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
