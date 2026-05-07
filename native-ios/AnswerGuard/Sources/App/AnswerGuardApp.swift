import SwiftUI

@main
struct AnswerGuardApp: App {

    init() {
#if DEBUG
        if ProcessInfo.processInfo.arguments.contains("-ui-test-reset-state") {
            SpamDatabase.shared.setBlockedNumbers([])
            SpamDatabase.shared.setIdentificationEntries([])
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
