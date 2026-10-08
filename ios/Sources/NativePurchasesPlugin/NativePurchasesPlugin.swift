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
        CAPPluginMethod(name: "presentOfferCodeRedeemSheet", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "acknowledgePurchase", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "consumePurchase", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getAppTransaction", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "isEntitledToOldBusinessModel", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getStorefront", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "configure", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getUnfinishedTransactions", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "finishTransaction", returnType: CAPPluginReturnPromise)
    ]

    private let pluginVersion: String = "8.8.3"
    var autoFinishTransactions: Bool = true
    private var transactionUpdatesTask: Task<Void, Never>?

    @objc func getPluginVersion(_ call: CAPPluginCall) {
        call.resolve(["version": self.pluginVersion])
    }

    override public func load() {
        readAutoFinishTransactionsFromConfig()
        super.load()
        NativePurchasesLog.configure(debugLogging: getConfig().getBoolean("debugLogging", false))
        startTransactionUpdatesListener()
    }

    deinit {
        transactionUpdatesTask?.cancel()
        transactionUpdatesTask = nil
    }

    func applyAutoFinishTransactionsSetting(_ value: Bool) {
        autoFinishTransactions = value
    }

    private func startTransactionUpdatesListener() {
        transactionUpdatesTask?.cancel()
        transactionUpdatesTask = Task.detached { [weak self] in
            for await result in Transaction.updates {
                guard !Task.isCancelled else { break }
                switch result {
                case .verified(let transaction):
                    let shouldFinish = await MainActor.run { self?.autoFinishTransactions ?? true }
                    var payload = await TransactionHelpers.buildTransactionResponse(
                        from: transaction,
                        jwsRepresentation: result.jwsRepresentation,
                        alwaysIncludeWillCancel: true
                    )
                    if shouldFinish {
                        await transaction.finish()
                        try? await Task.sleep(nanoseconds: 500_000_000)
                    } else {
                        payload["needsFinish"] = true
                    }
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

    @objc func getStorefront(_ call: CAPPluginCall) {
        NativePurchasesLog.debug("getStorefront")
        Task {
            let storefront = await Storefront.current
            await MainActor.run {
                if let storefront = storefront {
                    call.resolve([
                        "countryCode": storefront.countryCode,
                        "storefrontId": storefront.id
                    ])
                } else {
                    // No storefront (e.g. alternative distribution).
                    NativePurchasesLog.debug("getStorefront: no storefront available")
                    call.resolve(["countryCode": ""])
                }
            }
        }
    }

    @objc func purchaseProduct(_ call: CAPPluginCall) {
        NativePurchasesLog.debug("purchaseProduct")
        let productIdentifier = call.getString("productIdentifier", "")
        let quantity = call.getInt("quantity", 1)
        let appAccountToken = call.getString("appAccountToken")
        let billingPlanType = call.getString("billingPlanType")
        let autoAcknowledge = call.getBool("autoAcknowledgePurchases") ?? true

        if productIdentifier.isEmpty {
            call.reject("productIdentifier is Empty, give an id")
            return
        }

        NativePurchasesLog.debug("Auto-acknowledge enabled: \(autoAcknowledge)")

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
                switch self.billingPlanPurchaseOption(from: billingPlanType) {
                case .none:
                    break
                case .option(let option):
                    purchaseOptions.insert(option)
                case .failure(let message):
                    call.reject(message)
                    return
                }

                let result = try await product.purchase(options: purchaseOptions)
                NativePurchasesLog.debug("purchaseProduct flow finished with result type \(String(describing: result))")
                let shouldAutoFinish = self.autoFinishTransactions && autoAcknowledge
                await self.handlePurchaseResult(result, call: call, autoFinish: shouldAutoFinish)
            } catch {
                NativePurchasesLog.debug(error)
                call.reject(error.localizedDescription)
            }
        }
    }

    @objc func restorePurchases(_ call: CAPPluginCall) {
        NativePurchasesLog.debug("restorePurchases")
        Task {
            do {
                try await AppStore.sync()
                let shouldAutoFinish = await MainActor.run { self.autoFinishTransactions }
                await MainActor.run {
                    for transaction in SKPaymentQueue.default().transactions {
                        switch transaction.transactionState {
                        case .failed:
                            SKPaymentQueue.default().finishTransaction(transaction)
                        case .purchased, .restored:
                            if shouldAutoFinish {
                                SKPaymentQueue.default().finishTransaction(transaction)
                            }
                        case .purchasing, .deferred:
                            continue
                        @unknown default:
                            continue
                        }
                    }
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
        NativePurchasesLog.debug("productIdentifiers \(productIdentifiers)")
        NativePurchasesLog.debug("productType \(productType)")
        Task {
            do {
                let products = try await Product.products(for: productIdentifiers)
                NativePurchasesLog.debug("getProducts returned \(products.count) product(s)")
                var productsJson: [[String: Any]] = []
                for product in products {
                    productsJson.append(await product.pluginDictionary())
                }
                await MainActor.run { call.resolve(["products": productsJson]) }
            } catch {
                NativePurchasesLog.debug("error \(error)")
                await MainActor.run { call.reject(error.localizedDescription) }
            }
        }
    }

    @objc func getProduct(_ call: CAPPluginCall) {
        let productIdentifier = call.getString("productIdentifier") ?? ""
        let productType = call.getString("productType", "inapp")
        NativePurchasesLog.debug("productIdentifier \(productIdentifier)")
        NativePurchasesLog.debug("productType \(productType)")
        if productIdentifier.isEmpty {
            call.reject("productIdentifier is empty")
            return
        }

        Task {
            do {
                let products = try await Product.products(for: [productIdentifier])
                NativePurchasesLog.debug("getProduct returned \(products.count) product(s)")
                if let product = products.first {
                    let payload = await product.pluginDictionary()
                    await MainActor.run { call.resolve(["product": payload]) }
                } else {
                    await MainActor.run { call.reject("Product not found") }
                }
            } catch {
                NativePurchasesLog.debug(error)
                await MainActor.run { call.reject(error.localizedDescription) }
            }
        }
    }

    @objc func getPurchases(_ call: CAPPluginCall) {
        NativePurchasesLog.debug("getPurchases")
        let appAccountTokenFilter = call.getString("appAccountToken")
        let onlyCurrentEntitlements = call.getBool("onlyCurrentEntitlements") ?? false
        Task {
            do {
                let allPurchases = try await TransactionHelpers.collectAllPurchases(
                    appAccountTokenFilter: appAccountTokenFilter,
                    onlyCurrentEntitlements: onlyCurrentEntitlements
                )
                await MainActor.run { call.resolve(["purchases": allPurchases]) }
            } catch {
                await MainActor.run { call.reject(error.localizedDescription) }
            }
        }
    }

    @objc func manageSubscriptions(_ call: CAPPluginCall) {
        NativePurchasesLog.debug("manageSubscriptions")
        Task { @MainActor in
            do {
                guard let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene else {
                    call.reject("Unable to get window scene")
                    return
                }
                try await AppStore.showManageSubscriptions(in: windowScene)
                call.resolve()
            } catch {
                NativePurchasesLog.debug("manageSubscriptions error: \(error)")
                call.reject(error.localizedDescription)
            }
        }
    }

    @objc func presentOfferCodeRedeemSheet(_ call: CAPPluginCall) {
        NativePurchasesLog.debug("presentOfferCodeRedeemSheet")
        if #available(iOS 16.0, *) {
            Task { @MainActor in
                await self.handlePresentOfferCodeRedeemSheet(call)
            }
        } else {
            call.reject("Offer code redemption requires iOS 16.0 or later")
        }
    }

    @objc func acknowledgePurchase(_ call: CAPPluginCall) {
        NativePurchasesLog.debug("acknowledgePurchase called on iOS")

        guard let purchaseToken = call.getString("purchaseToken") else {
            call.reject("purchaseToken is required")
            return
        }

        guard let transactionId = UInt64(purchaseToken) else {
            call.reject("Invalid purchaseToken format")
            return
        }

        Task {
            do {
                try await finishStoreKitTransaction(
                    transactionId: transactionId,
                    transactionIdString: purchaseToken
                )
                await MainActor.run {
                    NativePurchasesLog.debug("Transaction finished successfully")
                    call.resolve()
                }
            } catch let error as FinishTransactionError {
                await MainActor.run { call.reject(error.message) }
            } catch {
                await MainActor.run { call.reject(error.localizedDescription) }
            }
        }
    }

    @objc func consumePurchase(_ call: CAPPluginCall) {
        call.reject("consumePurchase is only available on Android")
    }

}

private enum BillingPlanPurchaseOptionResult {
    case none
    case option(Product.PurchaseOption)
    case failure(String)
}

private extension NativePurchasesPlugin {
    func billingPlanPurchaseOption(from billingPlanType: String?) -> BillingPlanPurchaseOptionResult {
        guard let billingPlanType = billingPlanType, !billingPlanType.isEmpty else {
            return .none
        }
        guard let normalizedBillingPlanType = StoreKitPayloadHelpers.purchaseBillingPlanType(from: billingPlanType) else {
            return .failure("billingPlanType must be monthly or upFront")
        }
        guard #available(iOS 26.4, *) else {
            return .failure("billingPlanType requires iOS 26.4 or later")
        }

        #if STOREKIT_26_5
        if let option = Product.PurchaseOption.capacitorBillingPlanType(normalizedBillingPlanType) {
            return .option(option)
        }
        return .failure("billingPlanType must be monthly or upFront")
        #else
        return .failure("billingPlanType requires building with Xcode 26.5 SDK or later")
        #endif
    }
}

// MARK: - iOS 16+ App Transaction Methods
extension NativePurchasesPlugin {
    @available(iOS 16.0, *)
    @MainActor
    private func handlePresentOfferCodeRedeemSheet(_ call: CAPPluginCall) async {
        do {
            guard let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene else {
                call.reject("Unable to get window scene")
                return
            }
            try await AppStore.presentOfferCodeRedeemSheet(in: windowScene)
            call.resolve()
        } catch {
            NativePurchasesLog.debug("presentOfferCodeRedeemSheet error: \(error)")
            call.reject(error.localizedDescription)
        }
    }


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
        NativePurchasesLog.debug("getAppTransaction called on iOS")
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
            NativePurchasesLog.debug("getAppTransaction error: \(error)")
            call.reject("Failed to get app transaction: \(error.localizedDescription)")
        }
    }

    @available(iOS 16.0, *)
    @MainActor
    private func handleIsEntitledToOldBusinessModel(
        _ call: CAPPluginCall,
        targetBuildNumber: String
    ) async {
        NativePurchasesLog.debug("isEntitledToOldBusinessModel called with targetBuildNumber: \(targetBuildNumber)")
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
            NativePurchasesLog.debug("isEntitledToOldBusinessModel error: \(error)")
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
