import SwiftUI

@main
struct AnswerGuardApp: App {
    @Environment(\.scenePhase) private var scenePhase

    init() {
#if DEBUG
        if ProcessInfo.processInfo.arguments.contains("-ui-test-reset-state") {
            // Cleanup any legacy state if necessary
            UserDefaults.standard.removeObject(forKey: "timer_config")
            UserDefaults.standard.removeObject(forKey: "active_timer_state")
        }
#endif
        AnalyticsService.shared.initialize()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(ProManager.shared)
                .preferredColorScheme(.dark)
                .onOpenURL { url in
                    AnalyticsService.shared.trackDeepLink(url)
                }
        }
    }
}

struct ContentView: View {
    var body: some View {
        NavigationStack {
            AnswerGuardHomeScreen()
        }
    }
}

#Preview {
    ContentView()
        .environmentObject(ProManager.shared)
}
