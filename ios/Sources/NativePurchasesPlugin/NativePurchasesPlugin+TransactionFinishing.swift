import Capacitor
import StoreKit

extension NativePurchasesPlugin {
    func readAutoFinishTransactionsFromConfig() {
        autoFinishTransactions = getConfig().getBoolean("autoFinishTransactions", true)
    }

    @objc func configure(_ call: CAPPluginCall) {
        if let value = call.getBool("autoFinishTransactions") {
            applyAutoFinishTransactionsSetting(value)
            print("autoFinishTransactions set to \(value)")
        }
        call.resolve()
    }

    @objc func getUnfinishedTransactions(_ call: CAPPluginCall) {
        Task {
            do {
                let transactions = try await TransactionHelpers.collectUnfinishedTransactions()
                await MainActor.run {
                    call.resolve(["transactions": transactions])
                }
            } catch {
                await MainActor.run {
                    call.reject(error.localizedDescription)
                }
            }
        }
    }

    @objc func finishTransaction(_ call: CAPPluginCall) {
        guard let transactionIdString = call.getString("transactionId"), !transactionIdString.isEmpty else {
            call.reject("transactionId is required")
            return
        }

        guard let transactionId = UInt64(transactionIdString) else {
            call.reject("Invalid transactionId format")
            return
        }

        Task {
            do {
                try await finishStoreKitTransaction(
                    transactionId: transactionId,
                    transactionIdString: transactionIdString
                )
                await MainActor.run { call.resolve() }
            } catch let error as FinishTransactionError {
                await MainActor.run { call.reject(error.message) }
            } catch {
                await MainActor.run { call.reject(error.localizedDescription) }
            }
        }
    }

    func finishStoreKitTransaction(transactionId: UInt64, transactionIdString: String) async throws {
        for await result in Transaction.unfinished {
            switch result {
            case .verified(let transaction) where transaction.id == transactionId:
                print("Manually finishing transaction: \(transaction.id)")
                await transaction.finish()
                return
            case .unverified(let transaction, let error) where transaction.id == transactionId:
                throw FinishTransactionError.verificationFailed(error.localizedDescription)
            default:
                continue
            }
        }

        for await verificationResult in Transaction.all {
            if case .verified(let transaction) = verificationResult, transaction.id == transactionId {
                print("Transaction \(transactionId) already finished")
                return
            }
        }

        let finishedLegacyTransaction = await MainActor.run { () -> Bool in
            for paymentTransaction in SKPaymentQueue.default().transactions {
                if paymentTransaction.transactionIdentifier == transactionIdString {
                    SKPaymentQueue.default().finishTransaction(paymentTransaction)
                    return true
                }
            }
            return false
        }
        if finishedLegacyTransaction {
            return
        }

        throw FinishTransactionError.notFound(transactionId)
    }
}

enum FinishTransactionError: Error {
    case notFound(UInt64)
    case verificationFailed(String)

    var message: String {
        switch self {
        case .notFound(let id):
            return "Transaction not found or already finished. Transaction ID: \(id)"
        case .verificationFailed(let reason):
            return "Transaction verification failed: \(reason)"
        }
    }
}
