import XCTest
@testable import AnswerGuard

final class ContactsServiceTests: XCTestCase {

    @MainActor
    func testContactsServiceInitialization() {
        let service = ContactsService.shared
        XCTAssertNotNil(service)
    }

    @MainActor
    func testIsNumberInContacts_withoutPermission_returnsFalse() {
        // This test assumes a clean environment where permission is not yet granted
        let service = ContactsService.shared
        if !service.isAuthorized {
            XCTAssertFalse(service.isNumberInContacts("16175550100"))
        }
    }
}
