import Foundation
import CallKit
import os

/// Thread-safe local spam/block number store.
/// Backed by UserDefaults (App Group) so the Call Directory extension can read it.
/// Shared between the main app and the AnswerGuardCallDirectory extension target.
public final class SpamDatabase: @unchecked Sendable {

    public static let shared = SpamDatabase()

    private let appGroupID = "group.com.igorganapolsky.answerguard"
    private let blockedKey = "spam_blocked_numbers"
    private let identifiedKey = "spam_identified_entries"
    private let pendingAddedBlockedKey = "spam_pending_added_blocked"
    private let pendingRemovedBlockedKey = "spam_pending_removed_blocked"
    private let logger = Logger(subsystem: "com.igorganapolsky.answerguard", category: "SpamDatabase")

    private let lock = NSLock()

    private var defaults: UserDefaults {
        UserDefaults(suiteName: appGroupID) ?? .standard
    }

    private init() {}

    // MARK: - Blocked numbers

    /// Returns blocked numbers as stored (unsorted, possibly with duplicates).
    public func blockedNumbers() -> [CXCallDirectoryPhoneNumber] {
        let raw = defaults.array(forKey: blockedKey) as? [Int64] ?? []
        return raw.map { CXCallDirectoryPhoneNumber($0) }
    }

    /// Returns blocked numbers sorted ascending with duplicates removed.
    /// iOS REQUIRES sorted-ascending, unique entries when supplied to CXCallDirectoryExtensionContext.
    public func sortedUniqueBlockedNumbers() -> [CXCallDirectoryPhoneNumber] {
        let unique = Set(blockedNumbers())
        return unique.sorted()
    }

    public func setBlockedNumbers(_ numbers: [CXCallDirectoryPhoneNumber]) {
        lock.lock(); defer { lock.unlock() }
        let normalized = Array(Set(numbers)).sorted()
        defaults.set(normalized.map { Int64($0) }, forKey: blockedKey)
        // Reset incremental deltas — caller has chosen full-load semantics.
        defaults.removeObject(forKey: pendingAddedBlockedKey)
        defaults.removeObject(forKey: pendingRemovedBlockedKey)
        logger.info("Updated blocked list: \(normalized.count) entries")
    }

    @discardableResult
    public func addBlockedNumber(_ number: CXCallDirectoryPhoneNumber) -> Bool {
        lock.lock(); defer { lock.unlock() }
        var existing = Set(blockedNumbers())
        guard existing.insert(number).inserted else { return false }
        defaults.set(existing.sorted().map { Int64($0) }, forKey: blockedKey)
        appendToInt64Array(key: pendingAddedBlockedKey, value: Int64(number))
        removeFromInt64Array(key: pendingRemovedBlockedKey, value: Int64(number))
        logger.info("Added blocked number")
        return true
    }

    @discardableResult
    public func removeBlockedNumber(_ number: CXCallDirectoryPhoneNumber) -> Bool {
        lock.lock(); defer { lock.unlock() }
        var existing = Set(blockedNumbers())
        guard existing.remove(number) != nil else { return false }
        defaults.set(existing.sorted().map { Int64($0) }, forKey: blockedKey)
        appendToInt64Array(key: pendingRemovedBlockedKey, value: Int64(number))
        removeFromInt64Array(key: pendingAddedBlockedKey, value: Int64(number))
        logger.info("Removed blocked number")
        return true
    }

    // MARK: - Incremental delta support
    //
    // CXCallDirectoryExtensionContext supports incremental mode via `isIncremental`.
    // The main app records adds/removes since the last full reload here; the extension
    // can read & clear them after publishing.

    public func pendingAddedBlockedNumbers() -> [CXCallDirectoryPhoneNumber] {
        let raw = defaults.array(forKey: pendingAddedBlockedKey) as? [Int64] ?? []
        return Array(Set(raw)).sorted().map { CXCallDirectoryPhoneNumber($0) }
    }

    public func pendingRemovedBlockedNumbers() -> [CXCallDirectoryPhoneNumber] {
        let raw = defaults.array(forKey: pendingRemovedBlockedKey) as? [Int64] ?? []
        return Array(Set(raw)).sorted().map { CXCallDirectoryPhoneNumber($0) }
    }

    public func clearPendingBlockedDeltas() {
        defaults.removeObject(forKey: pendingAddedBlockedKey)
        defaults.removeObject(forKey: pendingRemovedBlockedKey)
    }

    private func appendToInt64Array(key: String, value: Int64) {
        var arr = defaults.array(forKey: key) as? [Int64] ?? []
        arr.append(value)
        defaults.set(arr, forKey: key)
    }

    private func removeFromInt64Array(key: String, value: Int64) {
        guard var arr = defaults.array(forKey: key) as? [Int64] else { return }
        arr.removeAll { $0 == value }
        defaults.set(arr, forKey: key)
    }

    // MARK: - Identification entries

    public struct IdentificationEntry: Codable, Hashable {
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

    /// Returns identification entries sorted by phoneNumber, with duplicate phone numbers removed (first wins).
    public func sortedUniqueIdentificationEntries() -> [IdentificationEntry] {
        var seen = Set<CXCallDirectoryPhoneNumber>()
        var result: [IdentificationEntry] = []
        for entry in identificationEntries().sorted(by: { $0.phoneNumber < $1.phoneNumber }) {
            if seen.insert(entry.phoneNumber).inserted {
                result.append(entry)
            }
        }
        return result
    }

    public func setIdentificationEntries(_ entries: [IdentificationEntry]) {
        lock.lock(); defer { lock.unlock() }
        guard let data = try? JSONEncoder().encode(entries) else { return }
        defaults.set(data, forKey: identifiedKey)
        logger.info("Updated identification list: \(entries.count) entries")
    }

    /// Removes all stored identification entries, reverting to built-in seeds.
    public func clearIdentificationEntries() {
        lock.lock(); defer { lock.unlock() }
        defaults.removeObject(forKey: identifiedKey)
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
