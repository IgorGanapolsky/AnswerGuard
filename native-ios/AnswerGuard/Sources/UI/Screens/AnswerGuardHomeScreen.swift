import SwiftUI

struct AnswerGuardHomeScreen: View {

    @StateObject private var cdManager = CallDirectoryManager.shared
    @State private var showOnboarding = false

    var body: some View {
        ScrollView {
            VStack(spacing: 24) {
                headerSection
                statusCard
                actionsSection
                howItWorksSection
            }
            .padding()
        }
        .navigationTitle("AnswerGuard")
        .navigationBarTitleDisplayMode(.large)
        .sheet(isPresented: $showOnboarding) {
            CallDirectoryOnboardingView()
        }
        .task {
            await cdManager.refreshStatus()
        }
    }

    // MARK: - Sections

    private var headerSection: some View {
        VStack(spacing: 8) {
            Image(systemName: "phone.badge.checkmark")
                .font(.system(size: 64))
                .foregroundStyle(.tint)
            Text("Spam & Scam Call Protection")
                .font(.title2.bold())
                .multilineTextAlignment(.center)
            Text("Identify and block unwanted callers before your phone rings.")
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
        }
        .padding(.top, 16)
    }

    private var statusCard: some View {
        HStack {
            VStack(alignment: .leading, spacing: 4) {
                Text("Call Screening")
                    .font(.headline)
                Text(cdManager.isEnabled ? "Active" : "Not enabled")
                    .font(.subheadline)
                    .foregroundStyle(cdManager.isEnabled ? .green : .orange)
            }
            Spacer()
            if cdManager.isEnabled {
                Image(systemName: "checkmark.shield.fill")
                    .font(.title)
                    .foregroundStyle(.green)
            } else {
                Button("Enable") {
                    showOnboarding = true
                }
                .buttonStyle(.borderedProminent)
            }
        }
        .padding()
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 16))
    }

    private var actionsSection: some View {
        VStack(spacing: 12) {
            if cdManager.isEnabled {
                Button {
                    Task { await cdManager.reloadExtension() }
                } label: {
                    Label(
                        cdManager.isRefreshing ? "Refreshing…" : "Refresh Block List",
                        systemImage: "arrow.clockwise"
                    )
                    .frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)
                .disabled(cdManager.isRefreshing)
            }

            Button {
                cdManager.openSettings()
            } label: {
                Label("Open Settings", systemImage: "gear")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.bordered)
        }
    }

    private var howItWorksSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("How It Works")
                .font(.headline)

            ForEach(howItWorksSteps, id: \.title) { step in
                HStack(alignment: .top, spacing: 12) {
                    Image(systemName: step.icon)
                        .font(.title3)
                        .foregroundStyle(.tint)
                        .frame(width: 32)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(step.title).font(.subheadline.bold())
                        Text(step.detail).font(.caption).foregroundStyle(.secondary)
                    }
                }
            }
        }
        .padding()
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 16))
    }

    private let howItWorksSteps = [
        (icon: "shield.lefthalf.filled", title: "Local-first screening", detail: "Decisions happen on-device. Your call data never leaves your phone."),
        (icon: "list.bullet.rectangle", title: "Known spam database", detail: "Populated with confirmed spam prefixes and crowdsourced reports."),
        (icon: "arrow.clockwise.circle", title: "Always up to date", detail: "Tap Refresh to pull in the latest block list at any time."),
    ].map { HowItWorksStep(icon: $0.icon, title: $0.title, detail: $0.detail) }
}

private struct HowItWorksStep {
    let icon: String
    let title: String
    let detail: String
}

// MARK: - Onboarding

struct CallDirectoryOnboardingView: View {
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            VStack(spacing: 32) {
                Spacer()
                Image(systemName: "phone.badge.plus")
                    .font(.system(size: 80))
                    .foregroundStyle(.tint)

                VStack(spacing: 12) {
                    Text("Enable Call Screening")
                        .font(.title.bold())
                    Text("Go to Settings → Phone → Call Blocking & Identification, then turn on AnswerGuard.")
                        .multilineTextAlignment(.center)
                        .foregroundStyle(.secondary)
                }

                Button("Open Settings") {
                    if let url = URL(string: UIApplication.openSettingsURLString) {
                        UIApplication.shared.open(url)
                    }
                    dismiss()
                }
                .buttonStyle(.borderedProminent)
                .controlSize(.large)

                Button("Maybe Later") { dismiss() }
                    .foregroundStyle(.secondary)

                Spacer()
            }
            .padding()
            .navigationTitle("Setup")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Close") { dismiss() }
                }
            }
        }
    }
}

#Preview {
    NavigationStack {
        AnswerGuardHomeScreen()
    }
}
