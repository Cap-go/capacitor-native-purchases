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
        CAPPluginMethod(name: "manageSubscriptions", returnType: CAPPluginReturnPromise)
    ]

    private let PLUGIN_VERSION: String = "7.11.3"
    private var transactionUpdatesTask: Task<Void, Never>?

    @objc func getPluginVersion(_ call: CAPPluginCall) {
        call.resolve(["version": self.PLUGIN_VERSION])
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
            // Create a single ISO8601DateFormatter once per Task to avoid repeated allocations
            let dateFormatter = ISO8601DateFormatter()

            for await result in Transaction.updates {
                guard !Task.isCancelled else { break }
                do {
                    guard case .verified(let transaction) = result else {
                        // Ignore/unverified transactions; nothing to finish
                        continue
                    }

                    // Build payload similar to purchase response
                    var payload: [String: Any] = ["transactionId": String(transaction.id)]

                    // Always include willCancel key with NSNull() default
                    payload["willCancel"] = NSNull()

                    if let appStoreReceiptURL = Bundle.main.appStoreReceiptURL,
                       FileManager.default.fileExists(atPath: appStoreReceiptURL.path),
                       let receiptData = try? Data(contentsOf: appStoreReceiptURL) {
                        payload["receipt"] = receiptData.base64EncodedString()
                    }

                    payload["productIdentifier"] = transaction.productID
                    payload["purchaseDate"] = dateFormatter.string(from: transaction.purchaseDate)
                    payload["productType"] = transaction.productType == .autoRenewable ? "subs" : "inapp"

                    if transaction.productType == .autoRenewable {
                        payload["originalPurchaseDate"] = dateFormatter.string(from: transaction.originalPurchaseDate)
                        if let expirationDate = transaction.expirationDate {
                            payload["expirationDate"] = dateFormatter.string(from: expirationDate)
                            payload["isActive"] = expirationDate > Date()
                        }
                    }

                    let subscriptionStatus = await transaction.subscriptionStatus
                    if let subscriptionStatus = subscriptionStatus {
                        if subscriptionStatus.state == .subscribed {
                            let renewalInfo = subscriptionStatus.renewalInfo
                            switch renewalInfo {
                            case .verified(let value):
                                payload["willCancel"] = !value.willAutoRenew
                            case .unverified:
                                // willCancel remains NSNull() for unverified renewalInfo
                                break
                            }
                        }
                    }

                    // Finish the transaction to avoid blocking future purchases
                    await transaction.finish()

                    // Notify JS listeners on main thread, after slight delay
                    try? await Task.sleep(nanoseconds: 500_000_000) // 0.5s delay
                    await MainActor.run {
                        self?.notifyListeners("transactionUpdated", data: payload)
                    }
                }
            }
        }
        transactionUpdatesTask = task
    }

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

            Task {
                do {
                    let products = try await Product.products(for: [productIdentifier])
                    guard let product = products.first else {
                        call.reject("Cannot find product for id \(productIdentifier)")
                        return
                    }
                    var purchaseOptions = Set<Product.PurchaseOption>()
                    purchaseOptions.insert(Product.PurchaseOption.quantity(quantity))

                    // Add appAccountToken if provided
                    if let accountToken = appAccountToken, !accountToken.isEmpty {
                        if let tokenData = UUID(uuidString: accountToken) {
                            purchaseOptions.insert(Product.PurchaseOption.appAccountToken(tokenData))
                        }
                    }

                    let result = try await product.purchase(options: purchaseOptions)
                    print("purchaseProduct result \(result)")
                    switch result {
                    case let .success(.verified(transaction)):
                        // Successful purchase
                        var response: [String: Any] = ["transactionId": transaction.id]

                        // Get receipt data
                        if let appStoreReceiptURL = Bundle.main.appStoreReceiptURL,
                           FileManager.default.fileExists(atPath: appStoreReceiptURL.path),
                           let receiptData = try? Data(contentsOf: appStoreReceiptURL) {
                            let receiptBase64 = receiptData.base64EncodedString()
                            response["receipt"] = receiptBase64
                        }

                        // Add detailed transaction information
                        response["productIdentifier"] = transaction.productID
                        response["purchaseDate"] = ISO8601DateFormatter().string(from: transaction.purchaseDate)
                        response["productType"] = transaction.productType == .autoRenewable ? "subs" : "inapp"
                        if let token = transaction.appAccountToken {
                            let tokenString = token.uuidString
                            response["appAccountToken"] = tokenString
                        }

                        // Add subscription-specific information
                        if transaction.productType == .autoRenewable {
                            response["originalPurchaseDate"] = ISO8601DateFormatter().string(from: transaction.originalPurchaseDate)
                            if let expirationDate = transaction.expirationDate {
                                response["expirationDate"] = ISO8601DateFormatter().string(from: expirationDate)
                                let isActive = expirationDate > Date()
                                response["isActive"] = isActive
                            }
                        }

                        let subscriptionStatus = await transaction.subscriptionStatus
                        if let subscriptionStatus = subscriptionStatus {
                            // You can use 'state' here if needed
                            let state = subscriptionStatus.state
                            if state == .subscribed {
                                // Use Objective-C reflection to access advancedCommerceInfo
                                let renewalInfo = subscriptionStatus.renewalInfo

                                switch renewalInfo {
                                case .verified(let value):
                                    //                                            if #available(iOS 18.4, *) {
                                    //                                                // This should work but may need runtime access
                                    //                                                let advancedInfo = value.advancedCommerceInfo
                                    //                                                print("Advanced commerce info: \(advancedInfo)")
                                    //                                            }
                                    //                                            print("[InAppPurchase] Subscription renewalInfo verified.")
                                    response["willCancel"] = !value.willAutoRenew
                                case .unverified:
                                    print("[InAppPurchase] Subscription renewalInfo not verified.")
                                    response["willCancel"] = NSNull()
                                }
                            }
                        }

                        await transaction.finish()
                        call.resolve(response)
                    case let .success(.unverified(_, error)):
                        // Successful purchase but transaction/receipt can't be verified
                        // Could be a jailbroken phone
                        call.reject(error.localizedDescription)
                    case .pending:
                        // Transaction waiting on SCA (Strong Customer Authentication) or
                        // approval from Ask to Buy
                        call.reject("Transaction pending")
                    case .userCancelled:
                        // ^^^
                        call.reject("User cancelled")
                    @unknown default:
                        call.reject("Unknown error")
                    }
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

    @objc func restorePurchases(_ call: CAPPluginCall) {
        if #available(iOS 15.0, *) {
            print("restorePurchases")
            DispatchQueue.global().async {
                Task {
                    do {
                        try await AppStore.sync()
                        // make finish() calls for all transactions and consume all consumables
                        for transaction in SKPaymentQueue.default().transactions {
                            SKPaymentQueue.default().finishTransaction(transaction)
                        }
                        call.resolve()
                    } catch {
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
            DispatchQueue.global().async {
                Task {
                    do {
                        let products = try await Product.products(for: productIdentifiers)
                        print("products \(products)")
                        let productsJson: [[String: Any]] = products.map { $0.dictionary }
                        call.resolve([
                            "products": productsJson
                        ])
                    } catch {
                        print("error \(error)")
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

            DispatchQueue.global().async {
                Task {
                    do {
                        let products = try await Product.products(for: [productIdentifier])
                        print("products \(products)")
                        if let product = products.first {
                            let productJson = product.dictionary
                            call.resolve(["product": productJson])
                        } else {
                            call.reject("Product not found")
                        }
                    } catch {
                        print(error)
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
            DispatchQueue.global().async {
                Task {
                    do {
                        var allPurchases: [[String: Any]] = []

                        // Get all current entitlements (active subscriptions)
                        for await result in Transaction.currentEntitlements {
                            if case .verified(let transaction) = result {
                                let transactionAccountToken = transaction.appAccountToken?.uuidString
                                if let filter = appAccountTokenFilter {
                                    guard let token = transactionAccountToken, token == filter else {
                                        continue
                                    }
                                }
                                var purchaseData: [String: Any] = ["transactionId": String(transaction.id)]

                                // Get receipt data
                                if let appStoreReceiptURL = Bundle.main.appStoreReceiptURL,
                                   FileManager.default.fileExists(atPath: appStoreReceiptURL.path),
                                   let receiptData = try? Data(contentsOf: appStoreReceiptURL) {
                                    let receiptBase64 = receiptData.base64EncodedString()
                                    purchaseData["receipt"] = receiptBase64
                                }

                                // Add detailed transaction information
                                purchaseData["productIdentifier"] = transaction.productID
                                purchaseData["purchaseDate"] = ISO8601DateFormatter().string(from: transaction.purchaseDate)
                                purchaseData["productType"] = transaction.productType == .autoRenewable ? "subs" : "inapp"
                                if let token = transactionAccountToken {
                                    purchaseData["appAccountToken"] = token
                                }

                                // Add subscription-specific information
                                if transaction.productType == .autoRenewable {
                                    purchaseData["originalPurchaseDate"] = ISO8601DateFormatter().string(from: transaction.originalPurchaseDate)
                                    if let expirationDate = transaction.expirationDate {
                                        purchaseData["expirationDate"] = ISO8601DateFormatter().string(from: expirationDate)
                                        let isActive = expirationDate > Date()
                                        purchaseData["isActive"] = isActive
                                    }
                                }

                                let subscriptionStatus = await transaction.subscriptionStatus
                                if let subscriptionStatus = subscriptionStatus {
                                    // You can use 'state' here if needed
                                    let state = subscriptionStatus.state
                                    if state == .subscribed {
                                        // Use Objective-C reflection to access advancedCommerceInfo
                                        let renewalInfo = subscriptionStatus.renewalInfo

                                        switch renewalInfo {
                                        case .verified(let value):
                                            //                                            if #available(iOS 18.4, *) {
                                            //                                                // This should work but may need runtime access
                                            //                                                let advancedInfo = value.advancedCommerceInfo
                                            //                                                print("Advanced commerce info: \(advancedInfo)")
                                            //                                            }
                                            //                                            print("[InAppPurchase] Subscription renewalInfo verified.")
                                            purchaseData["willCancel"] = !value.willAutoRenew
                                        case .unverified:
                                            print("[InAppPurchase] Subscription renewalInfo not verified.")
                                            purchaseData["willCancel"] = NSNull()
                                        }
                                    }
                                }

                                allPurchases.append(purchaseData)
                            }
                        }

                        // Also get all transactions (including non-consumables and expired subscriptions)
                        for await result in Transaction.all {
                            if case .verified(let transaction) = result {
                                let transactionIdString = String(transaction.id)
                                let transactionAccountToken = transaction.appAccountToken?.uuidString

                                if let filter = appAccountTokenFilter {
                                    guard let token = transactionAccountToken, token == filter else {
                                        continue
                                    }
                                }

                                // Check if we already have this transaction
                                let alreadyExists = allPurchases.contains { purchase in
                                    if let existingId = purchase["transactionId"] as? String {
                                        return existingId == transactionIdString
                                    }
                                    return false
                                }

                                if !alreadyExists {
                                    var purchaseData: [String: Any] = ["transactionId": transactionIdString]

                                    // Get receipt data
                                    if let appStoreReceiptURL = Bundle.main.appStoreReceiptURL,
                                       FileManager.default.fileExists(atPath: appStoreReceiptURL.path),
                                       let receiptData = try? Data(contentsOf: appStoreReceiptURL) {
                                        let receiptBase64 = receiptData.base64EncodedString()
                                        purchaseData["receipt"] = receiptBase64
                                    }

                                    // Add detailed transaction information
                                    purchaseData["productIdentifier"] = transaction.productID
                                    purchaseData["purchaseDate"] = ISO8601DateFormatter().string(from: transaction.purchaseDate)
                                    purchaseData["productType"] = transaction.productType == .autoRenewable ? "subs" : "inapp"
                                    if let token = transactionAccountToken {
                                        purchaseData["appAccountToken"] = token
                                    }

                                    // Add subscription-specific information
                                    if transaction.productType == .autoRenewable {
                                        purchaseData["originalPurchaseDate"] = ISO8601DateFormatter().string(from: transaction.originalPurchaseDate)
                                        if let expirationDate = transaction.expirationDate {
                                            purchaseData["expirationDate"] = ISO8601DateFormatter().string(from: expirationDate)
                                            let isActive = expirationDate > Date()
                                            purchaseData["isActive"] = isActive
                                        }
                                    }

                                    let subscriptionStatus = await transaction.subscriptionStatus
                                    if let subscriptionStatus = subscriptionStatus {
                                        // You can use 'state' here if needed
                                        let state = subscriptionStatus.state
                                        if state == .subscribed {
                                            // Use Objective-C reflection to access advancedCommerceInfo
                                            let renewalInfo = subscriptionStatus.renewalInfo

                                            switch renewalInfo {
                                            case .verified(let value):
                                                //                                            if #available(iOS 18.4, *) {
                                                //                                                // This should work but may need runtime access
                                                //                                                let advancedInfo = value.advancedCommerceInfo
                                                //                                                print("Advanced commerce info: \(advancedInfo)")
                                                //                                            }
                                                //                                            print("[InAppPurchase] Subscription renewalInfo verified.")
                                                purchaseData["willCancel"] = !value.willAutoRenew
                                            case .unverified:
                                                print("[InAppPurchase] Subscription renewalInfo not verified.")
                                                purchaseData["willCancel"] = NSNull()
                                            }
                                        }
                                    }

                                    allPurchases.append(purchaseData)
                                }
                            }
                        }

                        call.resolve(["purchases": allPurchases])
                    } catch {
                        print("getPurchases error: \(error)")
                        call.reject(error.localizedDescription)
                    }
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

}
