import SwiftUI
import ComposeApp

@main
struct iOSApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
                .onOpenURL { url in
                    Platform_iosKt.handleDeepLink(url: url)
                }
        }
    }
}
