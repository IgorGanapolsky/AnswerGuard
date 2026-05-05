import Foundation
import CallKit
import Combine
import UIKit
import os

@MainActor
final class CallDirectoryManager: ObservableObject {

    static let shared = CallDirectoryManager()

    @Published private(set) var isEnabled: Bool = false
    @Published private(set) var isRefreshing: Bool = false
    @Published private(set) var lastError: String?

    private let extensionID = "com.igorganapolsky.answerguard.calldirectory"
    private let logger = Logger(subsystem: "com.igorganapolsky.answerguard", category: "CallDirectoryManager")

    private init() {
        Task { await refreshStatus() }
    }

    func refreshStatus() async {
        let status = await withCheckedContinuation { continuation in
            CXCallDirectoryManager.sharedInstance.getEnabledStatusForExtension(
                withIdentifier: extensionID
            ) { status, error in
                continuation.resume(returning: status)
            }
        }
        isEnabled = (status == .enabled)
        logger.info("Call Directory status: \(status.rawValue)")
    }

    func reloadExtension() async {
        guard !isRefreshing else { return }
        isRefreshing = true
        lastError = nil
        defer { isRefreshing = false }

        await withCheckedContinuation { continuation in
            CXCallDirectoryManager.sharedInstance.reloadExtension(
                withIdentifier: extensionID
            ) { [weak self] error in
                if let error {
                    self?.lastError = error.localizedDescription
                    self?.logger.error("Reload failed: \(error.localizedDescription)")
                }
                continuation.resume()
            }
        }
        await refreshStatus()
    }

    func openSettings() {
        guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
        UIApplication.shared.open(url)
    }
}
