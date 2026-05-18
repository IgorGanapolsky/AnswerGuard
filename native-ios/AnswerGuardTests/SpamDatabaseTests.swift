import XCTest
import CallKit
@testable import AnswerGuard

final class SpamDatabaseTests: XCTestCase {

    override func setUp() {
        super.setUp()
        // Reset state
        SpamDatabase.shared.setBlockedNumbers([])
        SpamDatabase.shared.setIdentificationEntries([])
        SpamDatabase.shared.clearPendingBlockedDeltas()
    }

    // MARK: - Blocked numbers round-trip

    func testBlockedNumbersRoundTrip() {
        let numbers: [CXCallDirectoryPhoneNumber] = [12025550100, 18005550199]
        SpamDatabase.shared.setBlockedNumbers(numbers)
        let retrieved = SpamDatabase.shared.blockedNumbers()
        XCTAssertEqual(Set(retrieved), Set(numbers))
    }

    // MARK: - Sorting / dedup invariant (iOS requirement)

    func testSortedUniqueBlockedNumbersAreAscending() {
        let unsorted: [CXCallDirectoryPhoneNumber] = [
            18005550199, 12025550100, 19995550111, 13105550123,
        ]
        SpamDatabase.shared.setBlockedNumbers(unsorted)
        let sorted = SpamDatabase.shared.sortedUniqueBlockedNumbers()
        XCTAssertEqual(sorted, sorted.sorted())
        XCTAssertEqual(sorted.first, 12025550100)
        XCTAssertEqual(sorted.last, 19995550111)
    }

    func testSortedUniqueBlockedNumbersRemovesDuplicates() {
        let withDuplicates: [CXCallDirectoryPhoneNumber] = [
            12025550100, 12025550100, 18005550199, 18005550199, 18005550199,
        ]
        SpamDatabase.shared.setBlockedNumbers(withDuplicates)
        let sorted = SpamDatabase.shared.sortedUniqueBlockedNumbers()
        XCTAssertEqual(sorted, [12025550100, 18005550199])
    }

    func testSortedUniqueBlockedNumbersWhenEmpty() {
        SpamDatabase.shared.setBlockedNumbers([])
        XCTAssertEqual(SpamDatabase.shared.sortedUniqueBlockedNumbers(), [])
    }

    // MARK: - Add / remove API

    func testAddBlockedNumberInsertsOnce() {
        let n: CXCallDirectoryPhoneNumber = 12025550100
        XCTAssertTrue(SpamDatabase.shared.addBlockedNumber(n))
        XCTAssertFalse(SpamDatabase.shared.addBlockedNumber(n), "Re-adding existing number should be a no-op")
        XCTAssertEqual(SpamDatabase.shared.sortedUniqueBlockedNumbers(), [n])
    }

    func testRemoveBlockedNumberRemovesOnce() {
        let n: CXCallDirectoryPhoneNumber = 12025550100
        SpamDatabase.shared.addBlockedNumber(n)
        XCTAssertTrue(SpamDatabase.shared.removeBlockedNumber(n))
        XCTAssertFalse(SpamDatabase.shared.removeBlockedNumber(n), "Removing absent number should be a no-op")
        XCTAssertTrue(SpamDatabase.shared.sortedUniqueBlockedNumbers().isEmpty)
    }

    // MARK: - Incremental deltas

    func testIncrementalDeltasRecorded() {
        SpamDatabase.shared.addBlockedNumber(12025550100)
        SpamDatabase.shared.addBlockedNumber(18005550199)
        SpamDatabase.shared.removeBlockedNumber(12025550100)

        let added = SpamDatabase.shared.pendingAddedBlockedNumbers()
        let removed = SpamDatabase.shared.pendingRemovedBlockedNumbers()

        XCTAssertEqual(added, [18005550199], "Removed-after-added should not appear in adds")
        XCTAssertEqual(removed, [12025550100])
    }

    func testClearPendingDeltas() {
        SpamDatabase.shared.addBlockedNumber(12025550100)
        SpamDatabase.shared.clearPendingBlockedDeltas()
        XCTAssertTrue(SpamDatabase.shared.pendingAddedBlockedNumbers().isEmpty)
        XCTAssertTrue(SpamDatabase.shared.pendingRemovedBlockedNumbers().isEmpty)
    }

    func testFullSetClearsPendingDeltas() {
        SpamDatabase.shared.addBlockedNumber(12025550100)
        SpamDatabase.shared.setBlockedNumbers([18005550199])
        XCTAssertTrue(SpamDatabase.shared.pendingAddedBlockedNumbers().isEmpty)
        XCTAssertTrue(SpamDatabase.shared.pendingRemovedBlockedNumbers().isEmpty)
    }

    // MARK: - Identification entries

    func testIdentificationEntriesRoundTrip() {
        let entries = [
            SpamDatabase.IdentificationEntry(phoneNumber: 18885550177, label: "Scam Likely"),
        ]
        SpamDatabase.shared.setIdentificationEntries(entries)
        let retrieved = SpamDatabase.shared.identificationEntries()
        XCTAssertEqual(retrieved.first?.label, "Scam Likely")
        XCTAssertEqual(retrieved.first?.phoneNumber, 18885550177)
    }

    func testSortedUniqueIdentificationEntries() {
        let entries = [
            SpamDatabase.IdentificationEntry(phoneNumber: 18885550177, label: "Scam Likely"),
            SpamDatabase.IdentificationEntry(phoneNumber: 12025550100, label: "Telemarketer"),
            SpamDatabase.IdentificationEntry(phoneNumber: 18885550177, label: "Duplicate"),
        ]
        SpamDatabase.shared.setIdentificationEntries(entries)
        let sorted = SpamDatabase.shared.sortedUniqueIdentificationEntries()
        XCTAssertEqual(sorted.count, 2)
        XCTAssertEqual(sorted[0].phoneNumber, 12025550100)
        XCTAssertEqual(sorted[1].phoneNumber, 18885550177)
        XCTAssertEqual(sorted[1].label, "Scam Likely", "First entry for a number should win on dedup")
    }

    func testDefaultSeedsNotEmpty() {
        // When no entries stored, defaults should provide seed data
        let entries = SpamDatabase.shared.identificationEntries()
        XCTAssertFalse(entries.isEmpty)
    }
}
