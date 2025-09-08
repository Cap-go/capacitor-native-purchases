// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "CapgoNativePurchases",
    platforms: [.iOS(.v14)],
    products: [
        .library(
            name: "CapgoNativePurchases",
            targets: ["NativePurchasesPlugin"])
    ],
    dependencies: [
        .package(url: "https://github.com/ionic-team/capacitor-swift-pm.git", from: "7.4.3")
    ],
    targets: [
        .target(
            name: "NativePurchasesPlugin",
            dependencies: [
                .product(name: "Capacitor", package: "capacitor-swift-pm"),
                .product(name: "Cordova", package: "capacitor-swift-pm")
            ],
            path: "ios/Sources/NativePurchasesPlugin"),
        .testTarget(
            name: "NativePurchasesPluginTests",
            dependencies: ["NativePurchasesPlugin"],
            path: "ios/Tests/NativePurchasesPluginTests")
    ]
)
