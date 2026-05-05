import Foundation
import os

/// Thread-safe local spam/block number store.
/// Backed by UserDefaults (App Group) so the Call Directory extension can read it.
public final class SpamDatabase {

    public static let shared = SpamDatabase()

    private let appGroupID = "group.com.igorganapolsky.answerguard"
    private let blockedKey = "spam_blocked_numbers"
    private let identifiedKey = "spam_identified_entries"
    private let logger = Logger(subsystem: "com.igorganapolsky.answerguard", category: "SpamDatabase")

    private var defaults: UserDefaults {
        UserDefaults(suiteName: appGroupID) ?? .standard
    }

    private init() {}

    // MARK: - Blocked numbers

    public func blockedNumbers() -> [CXCallDirectoryPhoneNumber] {
        let raw = defaults.array(forKey: blockedKey) as? [Int64] ?? []
        return raw.map { CXCallDirectoryPhoneNumber($0) }
    }

    public func setBlockedNumbers(_ numbers: [CXCallDirectoryPhoneNumber]) {
        defaults.set(numbers.map { Int64($0) }, forKey: blockedKey)
        logger.info("Updated blocked list: \(numbers.count) entries")
    }

    // MARK: - Identification entries

    public struct IdentificationEntry: Codable {
        public let phoneNumber: CXCallDirectoryPhoneNumber
        public let label: String

        public init(phoneNumber: CXCallDirectoryPhoneNumber, label: String) {
            self.phoneNumber = phoneNumber
            self.label = label
        }
    }

    public func identificationEntries() -> [IdentificationEntry] {
        guard let data = defaults.data(forKey: identifiedKey),
              let entries = try? JSONDecoder().decode([IdentificationEntry].self, from: data) else {
            return defaultSpamSeeds()
        }
        return entries
    }

    public func setIdentificationEntries(_ entries: [IdentificationEntry]) {
        guard let data = try? JSONEncoder().encode(entries) else { return }
        defaults.set(data, forKey: identifiedKey)
        logger.info("Updated identification list: \(entries.count) entries")
    }

    // MARK: - Seed data (built-in known spam prefixes for demo/bootstrap)

    private func defaultSpamSeeds() -> [IdentificationEntry] {
        // Well-known US robocall prefixes (illustrative; replace with real dataset)
        let knownSpam: [(Int64, String)] = [
            (12025550100, "Likely Spam"),
            (18005550199, "Telemarketer"),
            (18885550177, "Scam Likely"),
        ]
        return knownSpam.map { IdentificationEntry(phoneNumber: CXCallDirectoryPhoneNumber($0.0), label: $0.1) }
    }
}
