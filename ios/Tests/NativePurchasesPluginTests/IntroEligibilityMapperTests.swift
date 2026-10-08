import XCTest
@testable import NativePurchasesPlugin

final class IntroEligibilityMapperTests: XCTestCase {
    func testUnknownWhenNoIntroOffer() {
        XCTAssertEqual(IntroEligibilityMapper.status(hasIntroOffer: false, isEligibleForIntroOffer: true), 0)
        XCTAssertEqual(IntroEligibilityMapper.status(hasIntroOffer: false, isEligibleForIntroOffer: false), 0)
    }

    func testEligibleWhenIntroOfferAndEligible() {
        XCTAssertEqual(IntroEligibilityMapper.status(hasIntroOffer: true, isEligibleForIntroOffer: true), 2)
    }

    func testIneligibleWhenIntroOfferButNotEligible() {
        XCTAssertEqual(IntroEligibilityMapper.status(hasIntroOffer: true, isEligibleForIntroOffer: false), 1)
    }
}
