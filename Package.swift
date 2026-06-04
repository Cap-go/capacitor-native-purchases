// swift-tools-version: 5.9
import Foundation
import PackageDescription

func hasStoreKit265SDK() -> Bool {
    let environment = ProcessInfo.processInfo.environment
    let developerDirs = [
        environment["DEVELOPER_DIR"],
        "/Applications/Xcode.app/Contents/Developer"
    ].compactMap { $0 }
    var sdkRoots = [environment["SDKROOT"]].compactMap { $0 }

    for developerDir in developerDirs {
        sdkRoots.append(contentsOf: [
            "\(developerDir)/Platforms/iPhoneOS.platform/Developer/SDKs/iPhoneOS.sdk",
            "\(developerDir)/Platforms/iPhoneSimulator.platform/Developer/SDKs/iPhoneSimulator.sdk"
        ])
    }

    for sdkRoot in sdkRoots {
        let modulePath = "\(sdkRoot)/System/Library/Frameworks/StoreKit.framework/Modules/StoreKit.swiftmodule"
        guard let interfaceFiles = try? FileManager.default.contentsOfDirectory(atPath: modulePath) else {
            continue
        }

        for fileName in interfaceFiles where fileName.hasSuffix(".swiftinterface") {
            let fileURL = URL(fileURLWithPath: modulePath).appendingPathComponent(fileName)
            guard let contents = try? String(contentsOf: fileURL) else {
                continue
            }
            if contents.contains("BillingPlanType") && contents.contains("pricingTerms") {
                return true
            }
        }
    }

    return false
}

let storeKitSwiftSettings: [SwiftSetting] = hasStoreKit265SDK() ? [.define("STOREKIT_26_5")] : []

let package = Package(
    name: "CapgoNativePurchases",
    platforms: [.iOS(.v15)],
    products: [
        .library(
            name: "CapgoNativePurchases",
            targets: ["NativePurchasesPlugin"])
    ],
    dependencies: [
        .package(url: "https://github.com/ionic-team/capacitor-swift-pm.git", from: "8.4.0")
    ],
    targets: [
        .target(
            name: "NativePurchasesPlugin",
            dependencies: [
                .product(name: "Capacitor", package: "capacitor-swift-pm"),
                .product(name: "Cordova", package: "capacitor-swift-pm")
            ],
            path: "ios/Sources/NativePurchasesPlugin",
            swiftSettings: storeKitSwiftSettings),
        .testTarget(
            name: "NativePurchasesPluginTests",
            dependencies: ["NativePurchasesPlugin"],
            path: "ios/Tests/NativePurchasesPluginTests")
    ]
)
