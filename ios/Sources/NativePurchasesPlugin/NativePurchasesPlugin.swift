import Foundation
import Capacitor
import StoreKit

/**
 * Please read the Capacitor iOS Plugin Development Guide
 * here: https://capacitorjs.com/docs/plugins/ios
 */
@objc(NativePurchasesPlugin)
public class NativePurchasesPlugin: CAPPlugin, CAPBridgedPlugin {
    public let identifier = "NativePurchasesPlugin"
    public let jsName = "NativePurchases"
    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "isBillingSupported", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "purchaseProduct", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "restorePurchases", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getProducts", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getProduct", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getPluginVersion", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getPurchases", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "manageSubscriptions", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "acknowledgePurchase", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getAppTransaction", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "isEntitledToOldBusinessModel", returnType: CAPPluginReturnPromise)
    ]

    private let pluginVersion: String = "8.0.3"
    private var transactionUpdatesTask: Task<Void, Never>?

    @objc func getPluginVersion(_ call: CAPPluginCall) {
        call.resolve(["version": self.pluginVersion])
    }

    override public func load() {
        super.load()
        // Start listening to StoreKit transaction updates as early as possible
        if #available(iOS 15.0, *) {
            startTransactionUpdatesListener()
        }
    }

    deinit {
        if #available(iOS 15.0, *) { cancelTransactionUpdatesListener() }
    }

    private func cancelTransactionUpdatesListener() {
        self.transactionUpdatesTask?.cancel()
        self.transactionUpdatesTask = nil
    }

    @available(iOS 15.0, *)
    private func startTransactionUpdatesListener() {
        // Ensure only one listener is running
        cancelTransactionUpdatesListener()
        let task = Task.detached { [weak self] in
            for await result in Transaction.updates {
                guard !Task.isCancelled else { break }
                switch result {
                case .verified(let transaction):
                    // Build payload similar to purchase response
                    let payload = await TransactionHelpers.buildTransactionResponse(from: transaction, jwsRepresentation: result.jwsRepresentation, alwaysIncludeWillCancel: true)

                    // Finish the transaction to avoid blocking future purchases
                    await transaction.finish()

                    // Notify JS listeners on main thread, after slight delay
                    try? await Task.sleep(nanoseconds: 500_000_000) // 0.5s delay
                    await MainActor.run {
                        self?.notifyListeners("transactionUpdated", data: payload)
                    }
                case .unverified(let transaction, let error):
                    await MainActor.run {
                        self?.notifyListeners("transactionVerificationFailed", data: [
                            "transactionId": String(transaction.id),
                            "error": error.localizedDescription
                        ])
                    }
                }
            }
        }
        transactionUpdatesTask = task
    }

    // MARK: - Plugin Methods

    @objc func isBillingSupported(_ call: CAPPluginCall) {
        if #available(iOS 15, *) {
            call.resolve([
                "isBillingSupported": true
            ])
        } else {
            call.resolve([
                "isBillingSupported": false
            ])
        }
    }

    @objc func purchaseProduct(_ call: CAPPluginCall) {
        if #available(iOS 15, *) {
            print("purchaseProduct")
            let productIdentifier = call.getString("productIdentifier", "")
            let quantity = call.getInt("quantity", 1)
            let appAccountToken = call.getString("appAccountToken")
            let autoAcknowledge = call.getBool("autoAcknowledgePurchases") ?? true

            if productIdentifier.isEmpty {
                call.reject("productIdentifier is Empty, give an id")
                return
            }

            print("Auto-acknowledge enabled: \(autoAcknowledge)")

            Task { @MainActor in
                do {
                    let products = try await Product.products(for: [productIdentifier])
                    guard let product = products.first else {
                        call.reject("Cannot find product for id \(productIdentifier)")
                        return
                    }

                    let purchaseOptions = self.buildPurchaseOptions(quantity: quantity, appAccountToken: appAccountToken)
                    let result = try await product.purchase(options: purchaseOptions)
                    print("purchaseProduct result \(result)")

                    await self.handlePurchaseResult(result, call: call, autoFinish: autoAcknowledge)
                } catch {
                    print(error)
                    call.reject(error.localizedDescription)
                }
            }
        } else {
            print("Not implemented under ios 15")
            call.reject("Not implemented under ios 15")
        }
    }

    @available(iOS 15.0, *)
    private func buildPurchaseOptions(quantity: Int, appAccountToken: String?) -> Set<Product.PurchaseOption> {
        var purchaseOptions = Set<Product.PurchaseOption>()
        purchaseOptions.insert(Product.PurchaseOption.quantity(quantity))

        if let accountToken = appAccountToken, !accountToken.isEmpty, let tokenData = UUID(uuidString: accountToken) {
            purchaseOptions.insert(Product.PurchaseOption.appAccountToken(tokenData))
        }

        return purchaseOptions
    }

    @available(iOS 15.0, *)
    @MainActor
    private func handlePurchaseResult(_ result: Product.PurchaseResult, call: CAPPluginCall, autoFinish: Bool) async {
        switch result {
        case let .success(verificationResult):
            switch verificationResult {
            case .verified(let transaction):
                let response = await TransactionHelpers.buildTransactionResponse(from: transaction, jwsRepresentation: verificationResult.jwsRepresentation)

                if autoFinish {
                    print("Auto-finishing transaction: \(transaction.id)")
                    await transaction.finish()
                } else {
                    print("Manual finish required for transaction: \(transaction.id)")
                    print("Transaction will remain unfinished until acknowledgePurchase() is called")
                    // Don't finish - transaction remains in StoreKit's queue
                    // Can be retrieved later via Transaction.all
                }

                call.resolve(response)
            case .unverified(_, let error):
                call.reject(error.localizedDescription)
            }
        case .pending:
            call.reject("Transaction pending")
        case .userCancelled:
            call.reject("User cancelled")
        @unknown default:
            call.reject("Unknown error")
        }
    }

    @objc func restorePurchases(_ call: CAPPluginCall) {
        if #available(iOS 15.0, *) {
            print("restorePurchases")
            Task {
                do {
                    try await AppStore.sync()
                    // make finish() calls for all transactions and consume all consumables
                    for transaction in SKPaymentQueue.default().transactions {
                        SKPaymentQueue.default().finishTransaction(transaction)
                    }
                    await MainActor.run {
                        call.resolve()
                    }
                } catch {
                    await MainActor.run {
                        call.reject(error.localizedDescription)
                    }
                }
            }
        } else {
            print("Not implemented under ios 15")
            call.reject("Not implemented under ios 15")
        }
    }

    @objc func getProducts(_ call: CAPPluginCall) {
        if #available(iOS 15.0, *) {
            let productIdentifiers = call.getArray("productIdentifiers", String.self) ?? []
            print("productIdentifiers \(productIdentifiers)")
            Task {
                do {
                    let products = try await Product.products(for: productIdentifiers)
                    print("products \(products)")
                    let productsJson: [[String: Any]] = products.map { $0.dictionary }
                    await MainActor.run {
                        call.resolve([
                            "products": productsJson
                        ])
                    }
                } catch {
                    print("error \(error)")
                    await MainActor.run {
                        call.reject(error.localizedDescription)
                    }
                }
            }
        } else {
            print("Not implemented under ios 15")
            call.reject("Not implemented under ios 15")
        }
    }

    @objc func getProduct(_ call: CAPPluginCall) {
        if #available(iOS 15.0, *) {
            let productIdentifier = call.getString("productIdentifier") ?? ""
            print("productIdentifier \(productIdentifier)")
            if productIdentifier.isEmpty {
                call.reject("productIdentifier is empty")
                return
            }

            Task {
                do {
                    let products = try await Product.products(for: [productIdentifier])
                    print("products \(products)")
                    if let product = products.first {
                        let productJson = product.dictionary
                        await MainActor.run {
                            call.resolve(["product": productJson])
                        }
                    } else {
                        await MainActor.run {
                            call.reject("Product not found")
                        }
                    }
                } catch {
                    print(error)
                    await MainActor.run {
                        call.reject(error.localizedDescription)
                    }
                }
            }
        } else {
            print("Not implemented under iOS 15")
            call.reject("Not implemented under iOS 15")
        }
    }

    @objc func getPurchases(_ call: CAPPluginCall) {
        let appAccountTokenFilter = call.getString("appAccountToken")
        if #available(iOS 15.0, *) {
            print("getPurchases")
            Task {
                let allPurchases = await TransactionHelpers.collectAllPurchases(appAccountTokenFilter: appAccountTokenFilter)
                await MainActor.run {
                    call.resolve(["purchases": allPurchases])
                }
            }
        } else {
            print("Not implemented under iOS 15")
            call.reject("Not implemented under iOS 15")
        }
    }

    @objc func manageSubscriptions(_ call: CAPPluginCall) {
        if #available(iOS 15.0, *) {
            print("manageSubscriptions")
            Task { @MainActor in
                do {
                    // Get the current window scene
                    guard let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene else {
                        call.reject("Unable to get window scene")
                        return
                    }
                    // Open the App Store subscription management page
                    try await AppStore.showManageSubscriptions(in: windowScene)
                    call.resolve()
                } catch {
                    print("manageSubscriptions error: \(error)")
                    call.reject(error.localizedDescription)
                }
            }
        } else {
            print("Not implemented under iOS 15")
            call.reject("Not implemented under iOS 15")
        }
    }

    @objc func acknowledgePurchase(_ call: CAPPluginCall) {
        if #available(iOS 15.0, *) {
            print("acknowledgePurchase called on iOS")

            guard let purchaseToken = call.getString("purchaseToken") else {
                call.reject("purchaseToken is required")
                return
            }

            // On iOS, purchaseToken is the transactionId (UInt64 as string)
            guard let transactionId = UInt64(purchaseToken) else {
                call.reject("Invalid purchaseToken format")
                return
            }

            Task {
                // Search for the transaction in StoreKit's unfinished transactions
                // This works even after app restart because StoreKit persists them
                var foundTransaction: Transaction?

                for await verificationResult in Transaction.all {
                    switch verificationResult {
                    case .verified(let transaction):
                        if transaction.id == transactionId {
                            foundTransaction = transaction
                            break
                        }
                    case .unverified:
                        continue
                    }
                    if foundTransaction != nil {
                        break
                    }
                }

                guard let transaction = foundTransaction else {
                    await MainActor.run {
                        call.reject("Transaction not found or already finished. Transaction ID: \(transactionId)")
                    }
                    return
                }

                print("Manually finishing transaction: \(transaction.id)")
                await transaction.finish()

                await MainActor.run {
                    print("Transaction finished successfully")
                    call.resolve()
                }
            }
        } else {
            call.reject("Not implemented under iOS 15")
        }
    }

    @objc func getAppTransaction(_ call: CAPPluginCall) {
        if #available(iOS 16.0, *) {
            print("getAppTransaction called on iOS")
            Task { @MainActor in
                do {
                    let verificationResult = try await AppTransaction.shared
                    switch verificationResult {
                    case .verified(let appTransaction):
                        var response: [String: Any] = [:]

                        // originalAppVersion is the CFBundleVersion (build number) at the time of original download
                        response["originalAppVersion"] = appTransaction.originalAppVersion

                        // Original purchase date
                        response["originalPurchaseDate"] = ISO8601DateFormatter().string(from: appTransaction.originalPurchaseDate)

                        // Bundle ID
                        response["bundleId"] = appTransaction.bundleID

                        // Current app version (build number)
                        response["appVersion"] = Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? ""

                        // Environment
                        switch appTransaction.environment {
                        case .sandbox:
                            response["environment"] = "Sandbox"
                        case .production:
                            response["environment"] = "Production"
                        case .xcode:
                            response["environment"] = "Xcode"
                        default:
                            response["environment"] = "Production"
                        }

                        // JWS representation for server-side verification
                        response["jwsRepresentation"] = verificationResult.jwsRepresentation

                        call.resolve(["appTransaction": response])

                    case .unverified(_, let error):
                        call.reject("App transaction verification failed: \(error.localizedDescription)")
                    }
                } catch {
                    print("getAppTransaction error: \(error)")
                    call.reject("Failed to get app transaction: \(error.localizedDescription)")
                }
            }
        } else {
            print("getAppTransaction not implemented under iOS 16")
            call.reject("App Transaction requires iOS 16.0 or later")
        }
    }

    @objc func isEntitledToOldBusinessModel(_ call: CAPPluginCall) {
        guard let targetBuildNumber = call.getString("targetBuildNumber"), !targetBuildNumber.isEmpty else {
            call.reject("targetBuildNumber is required on iOS")
            return
        }

        if #available(iOS 16.0, *) {
            print("isEntitledToOldBusinessModel called with targetBuildNumber: \(targetBuildNumber)")
            Task { @MainActor in
                do {
                    let verificationResult = try await AppTransaction.shared
                    switch verificationResult {
                    case .verified(let appTransaction):
                        let originalBuildNumber = appTransaction.originalAppVersion

                        // Compare build numbers (integers)
                        let isOlder = self.compareVersions(originalBuildNumber, targetBuildNumber) < 0

                        call.resolve([
                            "isOlderVersion": isOlder,
                            "originalAppVersion": originalBuildNumber
                        ])

                    case .unverified(_, let error):
                        call.reject("App transaction verification failed: \(error.localizedDescription)")
                    }
                } catch {
                    print("isEntitledToOldBusinessModel error: \(error)")
                    call.reject("Failed to get app transaction: \(error.localizedDescription)")
                }
            }
        } else {
            print("isEntitledToOldBusinessModel not implemented under iOS 16")
            call.reject("App Transaction requires iOS 16.0 or later")
        }
    }

    // MARK: - Version Comparison Helper

    /// Compares two build numbers as integers.
    /// Returns: negative if v1 < v2, zero if v1 == v2, positive if v1 > v2
    private func compareVersions(_ version1: String, _ version2: String) -> Int {
        let v1Int = Int(version1) ?? 0
        let v2Int = Int(version2) ?? 0
        return v1Int - v2Int
    }

}
