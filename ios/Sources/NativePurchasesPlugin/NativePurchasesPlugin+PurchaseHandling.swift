import Capacitor
import StoreKit

extension NativePurchasesPlugin {
    @MainActor
    func handlePurchaseResult(
        _ result: Product.PurchaseResult,
        call: CAPPluginCall,
        autoFinish: Bool
    ) async {
        switch result {
        case let .success(verificationResult):
            switch verificationResult {
            case .verified(let transaction):
                var response = await TransactionHelpers.buildTransactionResponse(
                    from: transaction,
                    jwsRepresentation: verificationResult.jwsRepresentation
                )
                if autoFinish {
                    print("Auto-finishing verified transaction")
                    await transaction.finish()
                } else {
                    print("Manual finish required for verified transaction")
                    response["needsFinish"] = true
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
}
