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

        let reloadError: String? = await withCheckedContinuation { continuation in
            CXCallDirectoryManager.sharedInstance.reloadExtension(
                withIdentifier: extensionID
            ) { error in
                if let error {
                    continuation.resume(returning: error.localizedDescription)
                } else {
                    continuation.resume(returning: nil)
                }
            }
        }

        if let reloadError {
            lastError = reloadError
            logger.error("Reload failed: \(reloadError)")
        }

        await refreshStatus()
    }

    func openSettings() {
        guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
        UIApplication.shared.open(url)
    }
}
