import Foundation

enum NativePurchasesLog {
    private static var explicitDebugLogging = false

    static func configure(debugLogging: Bool) {
        explicitDebugLogging = debugLogging
    }

    static var isLoggingEnabled: Bool {
        #if DEBUG
        return true
        #else
        return explicitDebugLogging
        #endif
    }

    static func debug(_ message: String) {
        guard isLoggingEnabled else { return }
        print("[NativePurchases] \(message)")
    }

    static func debug(_ error: Error) {
        debug(error.localizedDescription)
    }
}

enum IntroEligibilityMapper {
    /// Maps StoreKit intro-offer eligibility to INTRO_ELIGIBILITY_STATUS raw values.
    static func status(hasIntroOffer: Bool, isEligibleForIntroOffer: Bool?) -> Int {
        guard hasIntroOffer else {
            return 0
        }
        guard let isEligibleForIntroOffer else {
            return 0
        }
        return isEligibleForIntroOffer ? 2 : 1
    }
}
