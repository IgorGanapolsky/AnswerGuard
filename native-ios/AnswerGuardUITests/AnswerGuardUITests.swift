import XCTest

@MainActor
final class AnswerGuardUITests: XCTestCase {
    override func setUp() {
        super.setUp()
        continueAfterFailure = false
    }

    func testHomeScreenShowsAnswerGuardCallScreening() {
        let app = XCUIApplication()
        app.launchArguments += ["-ui-test-reset-state"]
        app.launch()

        XCTAssertTrue(app.navigationBars["AnswerGuard"].waitForExistence(timeout: 4.0))
        XCTAssertTrue(app.staticTexts["Spam & Scam Call Protection"].waitForExistence(timeout: 4.0))
        XCTAssertTrue(app.staticTexts["Call Screening"].waitForExistence(timeout: 4.0))
        XCTAssertTrue(app.staticTexts["How It Works"].waitForExistence(timeout: 4.0))
    }

    func testEnableOpensOnboardingSheet() {
        let app = XCUIApplication()
        app.launchArguments += ["-ui-test-reset-state"]
        app.launch()

        let enableButton = app.buttons["Enable"]
        XCTAssertTrue(enableButton.waitForExistence(timeout: 4.0))
        enableButton.tap()

        XCTAssertTrue(app.staticTexts["Enable Call Screening"].waitForExistence(timeout: 4.0))
        XCTAssertTrue(app.buttons["Open Settings"].waitForExistence(timeout: 4.0))
        XCTAssertTrue(app.buttons["Maybe Later"].waitForExistence(timeout: 4.0))
    }
}
