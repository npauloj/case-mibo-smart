import SwiftUI
import Shared

@main
struct iOSApp: App {
    init() {
        AppModulesKt.doInitKoin(appDeclaration: { _ in })
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
