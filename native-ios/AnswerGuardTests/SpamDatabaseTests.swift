import XCTest
@testable import AnswerGuard

final class SpamDatabaseTests: XCTestCase {

    override func setUp() {
        super.setUp()
        // Reset state
        SpamDatabase.shared.setBlockedNumbers([])
        SpamDatabase.shared.setIdentificationEntries([])
    }

    func testBlockedNumbersRoundTrip() {
        let numbers: [CXCallDirectoryPhoneNumber] = [12025550100, 18005550199]
        SpamDatabase.shared.setBlockedNumbers(numbers)
        let retrieved = SpamDatabase.shared.blockedNumbers()
        XCTAssertEqual(Set(retrieved), Set(numbers))
    }

    func testIdentificationEntriesRoundTrip() {
        let entries = [
            SpamDatabase.IdentificationEntry(phoneNumber: 18885550177, label: "Scam Likely"),
        ]
        SpamDatabase.shared.setIdentificationEntries(entries)
        let retrieved = SpamDatabase.shared.identificationEntries()
        XCTAssertEqual(retrieved.first?.label, "Scam Likely")
        XCTAssertEqual(retrieved.first?.phoneNumber, 18885550177)
    }

    func testDefaultSeedsNotEmpty() {
        // When no entries stored, defaults should provide seed data
        let entries = SpamDatabase.shared.identificationEntries()
        XCTAssertFalse(entries.isEmpty)
    }
}
