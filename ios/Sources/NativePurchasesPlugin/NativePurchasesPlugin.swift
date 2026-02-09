import Foundation
import Capacitor
import StoreKit

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

    private let pluginVersion: String = "8.0.22"
    private var transactionUpdatesTask: Task<Void, Never>?

    @objc func getPluginVersion(_ call: CAPPluginCall) {
        call.resolve(["version": self.pluginVersion])
    }

    override public func load() {
        super.load()
        startTransactionUpdatesListener()
    }

    deinit {
        transactionUpdatesTask?.cancel()
        transactionUpdatesTask = nil
    }

    private func startTransactionUpdatesListener() {
        transactionUpdatesTask?.cancel()
        transactionUpdatesTask = Task.detached { [weak self] in
            for await result in Transaction.updates {
                guard !Task.isCancelled else { break }
                switch result {
                case .verified(let transaction):
                    let payload = await TransactionHelpers.buildTransactionResponse(
                        from: transaction,
                        jwsRepresentation: result.jwsRepresentation,
                        alwaysIncludeWillCancel: true
                    )
                    await transaction.finish()
                    try? await Task.sleep(nanoseconds: 500_000_000)
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
    }

    @objc func isBillingSupported(_ call: CAPPluginCall) {
        call.resolve(["isBillingSupported": true])
    }

    @objc func purchaseProduct(_ call: CAPPluginCall) {
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

                var purchaseOptions = Set<Product.PurchaseOption>()
                purchaseOptions.insert(.quantity(quantity))
                if let token = appAccountToken, !token.isEmpty, let uuid = UUID(uuidString: token) {
                    purchaseOptions.insert(.appAccountToken(uuid))
                }

                let result = try await product.purchase(options: purchaseOptions)
                print("purchaseProduct result \(result)")
                await self.handlePurchaseResult(result, call: call, autoFinish: autoAcknowledge)
            } catch {
                print(error)
                call.reject(error.localizedDescription)
            }
        }
    }

    @MainActor
    private func handlePurchaseResult(
        _ result: Product.PurchaseResult,
        call: CAPPluginCall,
        autoFinish: Bool
    ) async {
        switch result {
        case let .success(verificationResult):
            switch verificationResult {
            case .verified(let transaction):
                let response = await TransactionHelpers.buildTransactionResponse(
                    from: transaction,
                    jwsRepresentation: verificationResult.jwsRepresentation
                )
                if autoFinish {
                    print("Auto-finishing transaction: \(transaction.id)")
                    await transaction.finish()
                } else {
                    print("Manual finish required for transaction: \(transaction.id)")
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
        print("restorePurchases")
        Task {
            do {
                try await AppStore.sync()
                for transaction in SKPaymentQueue.default().transactions {
                    SKPaymentQueue.default().finishTransaction(transaction)
                }
                await MainActor.run { call.resolve() }
            } catch {
                await MainActor.run { call.reject(error.localizedDescription) }
            }
        }
    }

    @objc func getProducts(_ call: CAPPluginCall) {
        let productIdentifiers = call.getArray("productIdentifiers", String.self) ?? []
        let productType = call.getString("productType", "inapp")
        print("productIdentifiers \(productIdentifiers)")
        print("productType \(productType)")
        Task {
            do {
                let products = try await Product.products(for: productIdentifiers)
                print("products \(products)")
                let productsJson: [[String: Any]] = products.map { $0.dictionary }
                await MainActor.run { call.resolve(["products": productsJson]) }
            } catch {
                print("error \(error)")
                await MainActor.run { call.reject(error.localizedDescription) }
            }
        }
    }

    @objc func getProduct(_ call: CAPPluginCall) {
        let productIdentifier = call.getString("productIdentifier") ?? ""
        let productType = call.getString("productType", "inapp")
        print("productIdentifier \(productIdentifier)")
        print("productType \(productType)")
        if productIdentifier.isEmpty {
            call.reject("productIdentifier is empty")
            return
        }

        Task {
            do {
                let products = try await Product.products(for: [productIdentifier])
                print("products \(products)")
                if let product = products.first {
                    await MainActor.run { call.resolve(["product": product.dictionary]) }
                } else {
                    await MainActor.run { call.reject("Product not found") }
                }
            } catch {
                print(error)
                await MainActor.run { call.reject(error.localizedDescription) }
            }
        }
    }

    @objc func getPurchases(_ call: CAPPluginCall) {
        print("getPurchases")
        let appAccountTokenFilter = call.getString("appAccountToken")
        Task {
            let allPurchases = await TransactionHelpers.collectAllPurchases(
                appAccountTokenFilter: appAccountTokenFilter
            )
            await MainActor.run { call.resolve(["purchases": allPurchases]) }
        }
    }

    @objc func manageSubscriptions(_ call: CAPPluginCall) {
        print("manageSubscriptions")
        Task { @MainActor in
            do {
                guard let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene else {
                    call.reject("Unable to get window scene")
                    return
                }
                try await AppStore.showManageSubscriptions(in: windowScene)
                call.resolve()
            } catch {
                print("manageSubscriptions error: \(error)")
                call.reject(error.localizedDescription)
            }
        }
    }

    @objc func acknowledgePurchase(_ call: CAPPluginCall) {
        print("acknowledgePurchase called on iOS")

        guard let purchaseToken = call.getString("purchaseToken") else {
            call.reject("purchaseToken is required")
            return
        }

        guard let transactionId = UInt64(purchaseToken) else {
            call.reject("Invalid purchaseToken format")
            return
        }

        Task {
            var foundTransaction: Transaction?
            for await verificationResult in Transaction.all {
                if case .verified(let transaction) = verificationResult, transaction.id == transactionId {
                    foundTransaction = transaction
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
    }

}

// MARK: - iOS 16+ App Transaction Methods
extension NativePurchasesPlugin {
    @objc func getAppTransaction(_ call: CAPPluginCall) {
        if #available(iOS 16.0, *) {
            Task { @MainActor in
                await self.handleGetAppTransaction(call)
            }
        } else {
            call.reject("App Transaction requires iOS 16.0 or later")
        }
    }

    @objc func isEntitledToOldBusinessModel(_ call: CAPPluginCall) {
        guard let targetBuildNumber = call.getString("targetBuildNumber"), !targetBuildNumber.isEmpty else {
            call.reject("targetBuildNumber is required on iOS")
            return
        }

        if #available(iOS 16.0, *) {
            Task { @MainActor in
                await self.handleIsEntitledToOldBusinessModel(call, targetBuildNumber: targetBuildNumber)
            }
        } else {
            call.reject("App Transaction requires iOS 16.0 or later")
        }
    }

    @available(iOS 16.0, *)
    @MainActor
    private func handleGetAppTransaction(_ call: CAPPluginCall) async {
        print("getAppTransaction called on iOS")
        do {
            let verificationResult = try await AppTransaction.shared
            switch verificationResult {
            case .verified(let appTransaction):
                let response: [String: Any] = [
                    "originalAppVersion": appTransaction.originalAppVersion,
                    "originalPurchaseDate": ISO8601DateFormatter().string(
                        from: appTransaction.originalPurchaseDate
                    ),
                    "bundleId": appTransaction.bundleID,
                    "appVersion": Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "",
                    "jwsRepresentation": verificationResult.jwsRepresentation,
                    "environment": appTransaction.environment.environmentString
                ]
                call.resolve(["appTransaction": response])
            case .unverified(_, let error):
                call.reject("App transaction verification failed: \(error.localizedDescription)")
            }
        } catch {
            print("getAppTransaction error: \(error)")
            call.reject("Failed to get app transaction: \(error.localizedDescription)")
        }
    }

    @available(iOS 16.0, *)
    @MainActor
    private func handleIsEntitledToOldBusinessModel(
        _ call: CAPPluginCall,
        targetBuildNumber: String
    ) async {
        print("isEntitledToOldBusinessModel called with targetBuildNumber: \(targetBuildNumber)")
        do {
            let verificationResult = try await AppTransaction.shared
            switch verificationResult {
            case .verified(let appTransaction):
                let originalBuildNumber = appTransaction.originalAppVersion
                let originalInt = Int(originalBuildNumber) ?? 0
                let targetInt = Int(targetBuildNumber) ?? 0
                call.resolve([
                    "isOlderVersion": originalInt < targetInt,
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
}

@available(iOS 16.0, *)
private extension AppStore.Environment {
    var environmentString: String {
        switch self {
        case .sandbox: return "Sandbox"
        case .production: return "Production"
        case .xcode: return "Xcode"
        default: return "Production"
        }
    }
}
