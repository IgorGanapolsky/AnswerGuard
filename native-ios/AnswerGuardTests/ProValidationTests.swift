import XCTest
@testable import AnswerGuard

final class ProValidationTests: XCTestCase {

    func testEntitlementLevelsMapToProAccess() {
        XCTAssertFalse(EntitlementLevel.none.isPro)
        XCTAssertTrue(EntitlementLevel.base.isPro)
        XCTAssertTrue(EntitlementLevel.elite.isPro)
    }

    func testPurchaseResultRawValuesAreAnalyticsSafe() {
        XCTAssertEqual(ProPurchaseResult.success.rawValue, "success")
        XCTAssertEqual(ProPurchaseResult.userCancelled.rawValue, "user_cancelled")
        XCTAssertEqual(ProPurchaseResult.productUnavailable.rawValue, "product_unavailable")
    }

    func testRestoreResultRawValuesAreAnalyticsSafe() {
        XCTAssertEqual(ProRestoreResult.restored.rawValue, "restored")
        XCTAssertEqual(ProRestoreResult.alreadyUnlocked.rawValue, "already_unlocked")
        XCTAssertEqual(ProRestoreResult.notFound.rawValue, "not_found")
    }
}
