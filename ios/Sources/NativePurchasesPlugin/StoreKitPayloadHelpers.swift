import Foundation
import StoreKit

// swiftlint:disable:next type_body_length
internal enum StoreKitPayloadHelpers {
    static func jsonDictionary(from data: Data) -> [String: Any]? {
        guard let value = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            return nil
        }
        return value
    }

    static func normalizedBillingPlanType(from value: Any?) -> String? {
        guard let rawValue = value as? String else {
            return nil
        }

        switch rawValue {
        case "monthly", "MONTHLY":
            return "monthly"
        case "upFront", "UP_FRONT", "BILLED_UPFRONT":
            return "upFront"
        default:
            return "unknown"
        }
    }

    static func purchaseBillingPlanType(from value: String?) -> String? {
        guard let value = value, !value.isEmpty else {
            return nil
        }

        switch value {
        case "monthly", "MONTHLY":
            return "monthly"
        case "upFront", "UP_FRONT", "BILLED_UPFRONT":
            return "upFront"
        default:
            return nil
        }
    }

    static func pricingTerms(from productJSON: [String: Any]) -> [[String: Any]]? {
        guard let rawTerms = findArray(named: "pricingTerms", in: productJSON) else {
            return nil
        }

        let terms = rawTerms.compactMap { $0 as? [String: Any] }.map(normalizePricingTerm)
        return terms.isEmpty ? nil : terms
    }

    static func transactionCommitmentInfo(from value: Any?) -> [String: Any]? {
        guard let rawInfo = value as? [String: Any] else {
            return nil
        }

        var info: [String: Any] = [:]
        copyNumber(named: "billingPeriodNumber", from: rawInfo, to: &info)
        copyNumber(named: "totalBillingPeriods", from: rawInfo, to: &info)

        if let expirationDate = isoDateString(fromMilliseconds: rawInfo["commitmentExpiresDate"] ?? rawInfo["expirationDate"]) {
            info["expirationDate"] = expirationDate
        }
        if let price = decimalPrice(fromMilliunits: rawInfo["commitmentPrice"] ?? rawInfo["price"]) {
            info["price"] = price
        }

        return info.isEmpty ? nil : info
    }

    static func renewalInfo(from value: Any?, fallbackWillAutoRenew: Bool?) -> [String: Any]? {
        guard let rawInfo = value as? [String: Any] else {
            if let fallbackWillAutoRenew = fallbackWillAutoRenew {
                return ["willAutoRenew": fallbackWillAutoRenew]
            }
            return nil
        }

        var info: [String: Any] = [:]

        if let fallbackWillAutoRenew = fallbackWillAutoRenew {
            info["willAutoRenew"] = fallbackWillAutoRenew
        } else if let autoRenewStatus = rawInfo["autoRenewStatus"] as? NSNumber {
            info["willAutoRenew"] = autoRenewStatus.intValue == 1
        }

        if let autoRenewProductId = rawInfo["autoRenewProductId"] as? String {
            info["autoRenewProductId"] = autoRenewProductId
        }
        if let renewalBillingPlanType = normalizedBillingPlanType(from: rawInfo["renewalBillingPlanType"]) {
            info["renewalBillingPlanType"] = renewalBillingPlanType
        }
        if let renewalDate = isoDateString(fromMilliseconds: rawInfo["renewalDate"]) {
            info["renewalDate"] = renewalDate
        }
        if let renewalPrice = decimalPrice(fromMilliunits: rawInfo["renewalPrice"]) {
            info["renewalPrice"] = renewalPrice
        }
        if let currency = rawInfo["currency"] as? String {
            info["currencyCode"] = currency
        }
        if let commitmentInfo = renewalCommitmentInfo(from: rawInfo["commitmentInfo"]) {
            info["commitmentInfo"] = commitmentInfo
        }

        return info.isEmpty ? nil : info
    }

    static func periodDictionary(unit: Product.SubscriptionPeriod.Unit, value: Int) -> [String: Any] {
        [
            "numberOfUnits": value,
            "unit": unit.integerValue,
            "unitString": unit.stringValue
        ]
    }

    static func currencySymbol(forCurrencyCode currencyCode: String, locale: Locale) -> String {
        let formatter = NumberFormatter()
        formatter.numberStyle = .currency
        formatter.locale = locale
        formatter.currencyCode = currencyCode
        return formatter.currencySymbol ?? currencyCode
    }

    static func subscriptionOfferDictionary(from offer: Product.SubscriptionOffer) -> [String: Any] {
        var dictionary: [String: Any] = [
            "type": offer.type.rawValue,
            "price": double(from: offer.price),
            "priceString": offer.displayPrice,
            "period": offer.period.dictionary,
            "periodCount": offer.periodCount,
            "paymentMode": offer.paymentMode.rawValue
        ]

        if let id = offer.id {
            dictionary["identifier"] = id
        }

        return dictionary
    }

    private static func normalizePricingTerm(_ rawTerm: [String: Any]) -> [String: Any] {
        var term: [String: Any] = [:]

        if let billingPlanType = normalizedBillingPlanType(from: rawTerm["billingPlanType"]) {
            term["billingPlanType"] = billingPlanType
        }
        if let billingDisplayPrice = rawTerm["billingDisplayPrice"] as? String ?? rawTerm["displayPrice"] as? String {
            term["billingDisplayPrice"] = billingDisplayPrice
        }
        if let billingPrice = number(from: rawTerm["billingPrice"] ?? rawTerm["price"]) {
            term["billingPrice"] = billingPrice
        }
        if let billingPeriod = normalizePeriod(rawTerm["billingPeriod"] ?? rawTerm["period"]) {
            term["billingPeriod"] = billingPeriod
        }
        if let commitmentInfo = productCommitmentInfo(from: rawTerm["commitmentInfo"]) {
            term["commitmentInfo"] = commitmentInfo
        }
        if let rawOffers = rawTerm["subscriptionOffers"] as? [Any] {
            let offers = rawOffers.compactMap { normalizeSubscriptionOffer($0) }
            if !offers.isEmpty {
                term["subscriptionOffers"] = offers
            }
        }

        return term
    }

    private static func productCommitmentInfo(from value: Any?) -> [String: Any]? {
        guard let rawInfo = value as? [String: Any] else {
            return nil
        }

        var info: [String: Any] = [:]

        if let displayPrice = rawInfo["displayPrice"] as? String ?? rawInfo["priceString"] as? String {
            info["priceString"] = displayPrice
        }
        if let price = number(from: rawInfo["price"]) ?? decimalPrice(fromMilliunits: rawInfo["commitmentPrice"]) {
            info["price"] = price
        }
        if let period = normalizePeriod(rawInfo["period"]) {
            info["period"] = period
        }

        return info.isEmpty ? nil : info
    }

    private static func renewalCommitmentInfo(from value: Any?) -> [String: Any]? {
        guard let rawInfo = value as? [String: Any] else {
            return nil
        }

        var info: [String: Any] = [:]

        if let autoRenewProductId = rawInfo["commitmentAutoRenewProductId"] as? String ?? rawInfo["autoRenewPreference"] as? String {
            info["autoRenewProductId"] = autoRenewProductId
        }
        if let autoRenewStatus = rawInfo["commitmentAutoRenewStatus"] as? NSNumber {
            info["willAutoRenew"] = autoRenewStatus.intValue == 1
        } else if let willAutoRenew = rawInfo["willAutoRenew"] as? Bool {
            info["willAutoRenew"] = willAutoRenew
        }
        if let billingPlanType = normalizedBillingPlanType(
            from: rawInfo["commitmentRenewalBillingPlanType"] ?? rawInfo["renewalBillingPlanType"]
        ) {
            info["renewalBillingPlanType"] = billingPlanType
        }
        if let renewalDate = isoDateString(fromMilliseconds: rawInfo["commitmentRenewalDate"] ?? rawInfo["renewalDate"]) {
            info["renewalDate"] = renewalDate
        }
        if let renewalPrice = decimalPrice(fromMilliunits: rawInfo["commitmentRenewalPrice"] ?? rawInfo["renewalPrice"]) {
            info["renewalPrice"] = renewalPrice
        }

        return info.isEmpty ? nil : info
    }

    private static func normalizeSubscriptionOffer(_ value: Any) -> [String: Any]? {
        guard let rawOffer = value as? [String: Any] else {
            return nil
        }

        var offer: [String: Any] = [:]

        if let identifier = rawOffer["id"] as? String ?? rawOffer["identifier"] as? String {
            offer["identifier"] = identifier
        }
        if let type = rawOffer["type"] as? String {
            offer["type"] = type
        }
        if let price = number(from: rawOffer["price"]) {
            offer["price"] = price
        }
        if let priceString = rawOffer["displayPrice"] as? String ?? rawOffer["priceString"] as? String {
            offer["priceString"] = priceString
        }
        if let period = normalizePeriod(rawOffer["period"]) {
            offer["period"] = period
        }
        if let periodCount = rawOffer["periodCount"] as? NSNumber {
            offer["periodCount"] = periodCount.intValue
        }
        if let paymentMode = rawOffer["paymentMode"] as? String {
            offer["paymentMode"] = paymentMode
        }

        return offer.isEmpty ? nil : offer
    }

    private static func normalizePeriod(_ value: Any?) -> [String: Any]? {
        if let rawPeriod = value as? [String: Any] {
            var period: [String: Any] = [:]

            if let numberOfUnits = rawPeriod["numberOfUnits"] as? NSNumber ?? rawPeriod["value"] as? NSNumber {
                period["numberOfUnits"] = numberOfUnits.intValue
            }
            if let unit = rawPeriod["unit"] as? NSNumber {
                period["unit"] = unit.intValue
            }
            if let unitString = rawPeriod["unitString"] as? String ?? rawPeriod["unit"] as? String {
                period["unitString"] = unitString
                period["unit"] = unitInteger(from: unitString)
            }

            return period.isEmpty ? nil : period
        }

        guard let isoPeriod = value as? String else {
            return nil
        }

        return periodDictionary(fromISO8601: isoPeriod)
    }

    private static func periodDictionary(fromISO8601 value: String) -> [String: Any]? {
        let pattern = #"^P(?:(\d+)Y)?(?:(\d+)M)?(?:(\d+)W)?(?:(\d+)D)?$"#
        guard let regex = try? NSRegularExpression(pattern: pattern),
              let match = regex.firstMatch(in: value, range: NSRange(value.startIndex..., in: value)) else {
            return nil
        }

        let captures = ["year", "month", "week", "day"]
        var foundPeriod: [String: Any]?

        for index in 1...4 {
            let range = match.range(at: index)
            guard range.location != NSNotFound,
                  let swiftRange = Range(range, in: value),
                  let numberOfUnits = Int(value[swiftRange]) else {
                continue
            }

            let unitString = captures[index - 1]
            guard foundPeriod == nil else {
                return nil
            }

            foundPeriod = [
                "numberOfUnits": numberOfUnits,
                "unit": unitInteger(from: unitString),
                "unitString": unitString
            ]
        }

        return foundPeriod
    }

    private static func findArray(named name: String, in value: Any) -> [Any]? {
        if let dictionary = value as? [String: Any] {
            if let array = dictionary[name] as? [Any] {
                return array
            }

            for childValue in dictionary.values {
                if let array = findArray(named: name, in: childValue) {
                    return array
                }
            }
        } else if let array = value as? [Any] {
            for childValue in array {
                if let foundArray = findArray(named: name, in: childValue) {
                    return foundArray
                }
            }
        }

        return nil
    }

    private static func copyNumber(named name: String, from source: [String: Any], to destination: inout [String: Any]) {
        if let value = source[name] as? NSNumber {
            destination[name] = value.intValue
        }
    }

    private static func number(from value: Any?) -> Double? {
        if let value = value as? NSNumber {
            return value.doubleValue
        }
        if let value = value as? String {
            return Double(value)
        }
        return nil
    }

    private static func decimalPrice(fromMilliunits value: Any?) -> Double? {
        guard let number = number(from: value) else {
            return nil
        }
        return number / 1000
    }

    private static func isoDateString(fromMilliseconds value: Any?) -> String? {
        guard let milliseconds = number(from: value) else {
            return nil
        }

        let date = Date(timeIntervalSince1970: milliseconds / 1000)
        return ISO8601DateFormatter().string(from: date)
    }

    private static func double(from decimal: Decimal) -> Double {
        NSDecimalNumber(decimal: decimal).doubleValue
    }

    private static func unitInteger(from value: String) -> Int {
        switch value {
        case "day", "DAY", "days":
            return 0
        case "week", "WEEK", "weeks":
            return 1
        case "month", "MONTH", "months":
            return 2
        case "year", "YEAR", "years":
            return 3
        default:
            return -1
        }
    }
}
