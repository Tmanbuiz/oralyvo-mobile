import XCTest

final class LaunchTests: XCTestCase {
    func testWelcomeReplacesScaffoldAndSignInCanBeDismissed() {
        let app = XCUIApplication()
        app.launch()
        let signIn = app.buttons["Sign in or create an account"]
        XCTAssertTrue(signIn.waitForExistence(timeout: 10))
        XCTAssertFalse(app.staticTexts["iOS app scaffold — more to come."].exists)
        signIn.tap()
        XCTAssertTrue(app.navigationBars["Sign in to Oralyvo"].waitForExistence(timeout: 5))
        app.buttons["Cancel"].tap()
        XCTAssertTrue(signIn.waitForExistence(timeout: 5))
    }
}
