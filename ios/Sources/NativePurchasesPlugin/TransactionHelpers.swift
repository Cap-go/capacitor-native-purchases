//
//  TransactionHelpers.swift
//  CapgoNativePurchases
//
//  Created by Martin DONADIEU
//

import Foundation
import StoreKit

@available(iOS 15.0, *)
internal class TransactionHelpers {

    static func buildTransactionResponse(from transaction: Transaction) async -> [String: Any] {
        var response: [String: Any] = ["transactionId": String(transaction.id)]

        // Get receipt data
        if let receiptBase64 = getReceiptData() {
            response["receipt"] = receiptBase64
        }

        // Add detailed transaction information
        response["productIdentifier"] = transaction.productID
        response["purchaseDate"] = ISO8601DateFormatter().string(from: transaction.purchaseDate)
        response["productType"] = transaction.productType == .autoRenewable ? "subs" : "inapp"

        if let token = transaction.appAccountToken {
            response["appAccountToken"] = token.uuidString
        }

        // Add subscription-specific information
        if transaction.productType == .autoRenewable {
            addSubscriptionInfo(to: &response, transaction: transaction)
            await addRenewalInfo(to: &response, transaction: transaction)
        }

        return response
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
        response["originalPurchaseDate"] = ISO8601DateFormatter().string(from: transaction.originalPurchaseDate)
        if let expirationDate = transaction.expirationDate {
            response["expirationDate"] = ISO8601DateFormatter().string(from: expirationDate)
            response["isActive"] = expirationDate > Date()
        }
    }

    static func addRenewalInfo(to response: inout [String: Any], transaction: Transaction) async {
        let subscriptionStatus = await transaction.subscriptionStatus
        guard let subscriptionStatus = subscriptionStatus else {
            response["willCancel"] = NSNull()
            return
        }

        if subscriptionStatus.state == .subscribed {
            let renewalInfo = subscriptionStatus.renewalInfo
            switch renewalInfo {
            case .verified(let value):
                response["willCancel"] = !value.willAutoRenew
            case .unverified:
                response["willCancel"] = NSNull()
            }
        } else {
            response["willCancel"] = NSNull()
        }
    }

    static func shouldFilterTransaction(_ transaction: Transaction, filter: String?) -> Bool {
        guard let filter = filter else { return false }
        let transactionAccountToken = transaction.appAccountToken?.uuidString
        return transactionAccountToken != filter
    }

    static func collectAllPurchases(appAccountTokenFilter: String?) async -> [[String: Any]] {
        var allPurchases: [[String: Any]] = []

        // Get all current entitlements (active subscriptions)
        await collectCurrentEntitlements(appAccountTokenFilter: appAccountTokenFilter, into: &allPurchases)

        // Also get all transactions (including non-consumables and expired subscriptions)
        await collectAllTransactions(appAccountTokenFilter: appAccountTokenFilter, into: &allPurchases)

        return allPurchases
    }

    static func collectCurrentEntitlements(appAccountTokenFilter: String?, into allPurchases: inout [[String: Any]]) async {
        for await result in Transaction.currentEntitlements {
            guard case .verified(let transaction) = result else { continue }

            if shouldFilterTransaction(transaction, filter: appAccountTokenFilter) {
                continue
            }

            let purchaseData = await buildTransactionResponse(from: transaction)
            allPurchases.append(purchaseData)
        }
    }

    static func collectAllTransactions(appAccountTokenFilter: String?, into allPurchases: inout [[String: Any]]) async {
        for await result in Transaction.all {
            guard case .verified(let transaction) = result else { continue }

            if shouldFilterTransaction(transaction, filter: appAccountTokenFilter) {
                continue
            }

            let transactionIdString = String(transaction.id)
            let alreadyExists = allPurchases.contains { purchase in
                (purchase["transactionId"] as? String) == transactionIdString
            }

            if !alreadyExists {
                let purchaseData = await buildTransactionResponse(from: transaction)
                allPurchases.append(purchaseData)
            }
        }
    }
}
