import XCTest
@testable import NativePurchasesPlugin

final class NativePurchasesPluginTests: XCTestCase {
    func testPluginVersionIsNonEmpty() {
        let plugin = NativePurchasesPlugin()
        XCTAssertFalse(plugin.identifier.isEmpty)
    }
}
