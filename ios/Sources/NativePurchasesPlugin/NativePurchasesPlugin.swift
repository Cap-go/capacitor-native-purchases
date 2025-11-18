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
        CAPPluginMethod(name: "acknowledgePurchase", returnType: CAPPluginReturnPromise)
    ]

    private let pluginVersion: String = "7.13.5"
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

            if productIdentifier.isEmpty {
                call.reject("productIdentifier is Empty, give an id")
                return
            }

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

                    await self.handlePurchaseResult(result, call: call)
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
    private func handlePurchaseResult(_ result: Product.PurchaseResult, call: CAPPluginCall) async {
        switch result {
        case let .success(verificationResult):
            switch verificationResult {
            case .verified(let transaction):
                let response = await TransactionHelpers.buildTransactionResponse(from: transaction, jwsRepresentation: verificationResult.jwsRepresentation)
                await transaction.finish()
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
        print("acknowledgePurchase called on iOS - not needed, iOS automatically finishes transactions")
        // iOS automatically finishes transactions through StoreKit 2
        // This method is provided for API compatibility but does nothing on iOS
        call.resolve()
    }

}
