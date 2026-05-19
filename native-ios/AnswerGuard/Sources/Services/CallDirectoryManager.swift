import Foundation
import CallKit
import Combine
import UIKit
import os

@MainActor
final class CallDirectoryManager: ObservableObject {

    static let shared = CallDirectoryManager()

    enum Status: String {
        case unknown
        case enabled
        case disabled
        case unavailable
    }

    @Published private(set) var isEnabled: Bool = false
    @Published private(set) var status: Status = .unknown
    @Published private(set) var isRefreshing: Bool = false
    @Published private(set) var lastError: String?

    private let extensionID = "com.igorganapolsky.answerguard.calldirectory"
    private let logger = Logger(subsystem: "com.igorganapolsky.answerguard", category: "CallDirectoryManager")

    private init() {
        Task { await refreshStatus() }
    }

    func refreshStatus() async {
        let rawStatus = await withCheckedContinuation { continuation in
            CXCallDirectoryManager.sharedInstance.getEnabledStatusForExtension(
                withIdentifier: extensionID
            ) { status, _ in
                continuation.resume(returning: status)
            }
        }
        switch rawStatus {
        case .enabled:
            status = .enabled
            isEnabled = true
        case .disabled:
            status = .disabled
            isEnabled = false
        case .unknown:
            status = .unknown
            isEnabled = false
        @unknown default:
            status = .unavailable
            isEnabled = false
        }
        logger.info("Call Directory status: \(rawStatus.rawValue)")
    }

    /// Asks iOS to re-fetch the block/identification lists from the extension.
    /// Call after mutating the shared `SpamDatabase`.
    func reloadExtension() async {
        guard !isRefreshing else { return }
        isRefreshing = true
        lastError = nil
        defer { isRefreshing = false }

        let reloadError = await withCheckedContinuation { continuation in
            CXCallDirectoryManager.sharedInstance.reloadExtension(
                withIdentifier: extensionID
            ) { error in
                continuation.resume(returning: error?.localizedDescription)
            }
        }
        if let reloadError {
            lastError = reloadError
            logger.error("Reload failed: \(reloadError)")
        }
        await refreshStatus()
    }

    /// Adds a number to the shared block list and triggers an extension reload.
    @discardableResult
    func block(_ number: CXCallDirectoryPhoneNumber) async -> Bool {
        let inserted = SpamDatabase.shared.addBlockedNumber(number)
        if inserted {
            await reloadExtension()
        }
        return inserted
    }

    /// Removes a number from the shared block list and triggers an extension reload.
    @discardableResult
    func unblock(_ number: CXCallDirectoryPhoneNumber) async -> Bool {
        let removed = SpamDatabase.shared.removeBlockedNumber(number)
        if removed {
            await reloadExtension()
        }
        return removed
    }

    func openSettings() {
        // UIApplication.openSettingsURLString opens the app's own Settings page.
        // Apple provides no deep link to Settings > Phone > Call Blocking & Identification;
        // the UI must instruct the user to navigate there manually.
        guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
        UIApplication.shared.open(url)
    }
}
