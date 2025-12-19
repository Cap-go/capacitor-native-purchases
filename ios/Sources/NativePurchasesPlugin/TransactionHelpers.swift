import Foundation
import StoreKit

internal class TransactionHelpers {

    static func buildTransactionResponse(
        from transaction: Transaction,
        jwsRepresentation: String? = nil,
        alwaysIncludeWillCancel: Bool = false
    ) async -> [String: Any] {
        var response: [String: Any] = ["transactionId": String(transaction.id)]

        if alwaysIncludeWillCancel {
            response["willCancel"] = NSNull()
        }

        addReceiptAndJws(to: &response, jws: jwsRepresentation)
        addTransactionDetails(to: &response, transaction: transaction)

        if transaction.productType == .autoRenewable {
            addSubscriptionInfo(to: &response, transaction: transaction)
            await addRenewalInfo(to: &response, transaction: transaction)
        }

        return response
    }

    private static func addReceiptAndJws(to response: inout [String: Any], jws: String?) {
        if let receiptBase64 = getReceiptData() {
            response["receipt"] = receiptBase64
        }
        if let jws = jws {
            response["jwsRepresentation"] = jws
        }
    }

    private static func addTransactionDetails(to response: inout [String: Any], transaction: Transaction) {
        response["productIdentifier"] = transaction.productID
        response["purchaseDate"] = ISO8601DateFormatter().string(from: transaction.purchaseDate)
        response["productType"] = transaction.productType == .autoRenewable ? "subs" : "inapp"
        response["isUpgraded"] = transaction.isUpgraded
        response["ownershipType"] = transaction.ownershipType.descriptionString

        if let revocationDate = transaction.revocationDate {
            response["revocationDate"] = ISO8601DateFormatter().string(from: revocationDate)
        }
        if let revocationReason = transaction.revocationReason {
            response["revocationReason"] = revocationReason.descriptionString
        }
        if #available(iOS 17.0, *) {
            response["transactionReason"] = transaction.reason.descriptionString
        }
        if #available(iOS 16.0, *) {
            response["environment"] = transaction.environment.descriptionString
        }
        if let token = transaction.appAccountToken {
            response["appAccountToken"] = token.uuidString
        }
    }

    static func getReceiptData() -> String? {
        guard let appStoreReceiptURL = Bundle.main.appStoreReceiptURL,
              FileManager.default.fileExists(atPath: appStoreReceiptURL.path),
              let receiptData = try? Data(contentsOf: appStoreReceiptURL) else {
            return nil
        }
        return receiptData.base64EncodedString()
    }

    static func addSubscriptionInfo(to response: inout [String: Any], transaction: Transaction) {
        response["originalPurchaseDate"] = ISO8601DateFormatter().string(
            from: transaction.originalPurchaseDate
        )
        if let expirationDate = transaction.expirationDate {
            response["expirationDate"] = ISO8601DateFormatter().string(from: expirationDate)
            response["isActive"] = expirationDate > Date()
        }
    }

    static func addRenewalInfo(to response: inout [String: Any], transaction: Transaction) async {
        guard let subscriptionStatus = await transaction.subscriptionStatus else {
            response["willCancel"] = NSNull()
            return
        }

        response["subscriptionState"] = subscriptionStatus.state.descriptionString

        if subscriptionStatus.state == .subscribed {
            if case .verified(let value) = subscriptionStatus.renewalInfo {
                response["willCancel"] = !value.willAutoRenew
            } else {
                response["willCancel"] = NSNull()
            }
        } else {
            response["willCancel"] = NSNull()
        }
    }

    static func collectAllPurchases(appAccountTokenFilter: String?) async -> [[String: Any]] {
        var allPurchases: [[String: Any]] = []
        var seenIds = Set<String>()

        for await result in Transaction.currentEntitlements {
            guard case .verified(let transaction) = result else { continue }
            if let filter = appAccountTokenFilter,
               transaction.appAccountToken?.uuidString != filter { continue }

            let idStr = String(transaction.id)
            seenIds.insert(idStr)
            let data = await buildTransactionResponse(
                from: transaction,
                jwsRepresentation: result.jwsRepresentation
            )
            allPurchases.append(data)
        }

        for await result in Transaction.all {
            guard case .verified(let transaction) = result else { continue }
            if let filter = appAccountTokenFilter,
               transaction.appAccountToken?.uuidString != filter { continue }

            let idStr = String(transaction.id)
            if seenIds.contains(idStr) { continue }

            let data = await buildTransactionResponse(
                from: transaction,
                jwsRepresentation: result.jwsRepresentation
            )
            allPurchases.append(data)
        }

        return allPurchases
    }
}

private extension Transaction.RevocationReason {
    var descriptionString: String {
        switch self {
        case .developerIssue: return "developerIssue"
        case .other: return "other"
        default: return "unknown"
        }
    }
}

@available(iOS 17.0, *)
private extension Transaction.Reason {
    var descriptionString: String {
        switch self {
        case .purchase: return "purchase"
        case .renewal: return "renewal"
        default: return "unknown"
        }
    }
}

private extension Transaction.OwnershipType {
    var descriptionString: String {
        switch self {
        case .purchased: return "purchased"
        case .familyShared: return "familyShared"
        default: return "purchased"
        }
    }
}

@available(iOS 16.0, *)
private extension AppStore.Environment {
    var descriptionString: String {
        switch self {
        case .sandbox: return "Sandbox"
        case .production: return "Production"
        case .xcode: return "Xcode"
        default: return "Production"
        }
    }
}

private extension Product.SubscriptionInfo.RenewalState {
    var descriptionString: String {
        switch self {
        case .subscribed: return "subscribed"
        case .expired: return "expired"
        case .revoked: return "revoked"
        case .inGracePeriod: return "inGracePeriod"
        case .inBillingRetryPeriod: return "inBillingRetryPeriod"
        default: return "unknown"
        }
    }
}
