import SwiftUI

@main
struct AnswerGuardApp: App {
    @StateObject private var timerManager = TimerManager()
    @Environment(\.scenePhase) private var scenePhase

    init() {
#if DEBUG
        if ProcessInfo.processInfo.arguments.contains("-ui-test-reset-state") {
            UserDefaults.standard.removeObject(forKey: "timer_config")
            UserDefaults.standard.removeObject(forKey: "active_timer_state")
        }
#endif
        AnalyticsService.shared.initialize()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(timerManager)
                .environmentObject(ProManager.shared)
                .preferredColorScheme(.dark)
                .onOpenURL { url in
                    AnalyticsService.shared.trackDeepLink(url)
                }
        }
        .onChange(of: scenePhase) { _, newPhase in
            switch newPhase {
            case .active:
                Task {
                    await timerManager.handleForeground()
                }
            case .background:
                timerManager.handleBackground()
            default:
                break
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
