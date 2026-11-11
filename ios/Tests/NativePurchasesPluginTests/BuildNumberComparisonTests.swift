import XCTest
@testable import NativePurchasesPlugin

final class BuildNumberComparisonTests: XCTestCase {
    func testPlainIntegersKeepNumericOrder() {
        XCTAssertTrue(TransactionHelpers.isBuildNumber("42", olderThan: "100"))
        XCTAssertFalse(TransactionHelpers.isBuildNumber("100", olderThan: "42"))
        XCTAssertFalse(TransactionHelpers.isBuildNumber("42", olderThan: "42"))
    }

    func testDottedBuildNumbersCompareEachPart() {
        XCTAssertTrue(TransactionHelpers.isBuildNumber("1.2.3", olderThan: "1.5.0"))
        XCTAssertFalse(TransactionHelpers.isBuildNumber("3.0", olderThan: "2"))
        XCTAssertFalse(TransactionHelpers.isBuildNumber("1.10", olderThan: "1.9"))
    }

    func testMissingPartsCountAsZero() {
        XCTAssertFalse(TransactionHelpers.isBuildNumber("2.0.0", olderThan: "2"))
        XCTAssertTrue(TransactionHelpers.isBuildNumber("2", olderThan: "2.0.1"))
    }
}
