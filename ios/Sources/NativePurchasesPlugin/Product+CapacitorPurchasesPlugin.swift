//
//  Extensions.swift
//  CapgoCapacitorPurchases
//
//  Created by Martin DONADIEU on 2023-08-08.
//

import Foundation
import StoreKit

extension Product {

    var dictionary: [String: Any] {
        //        /**
        //         * Currency code for price and original price.
        //         */
        //        readonly currencyCode: string;
        //        /**
        //         * Currency symbol for price and original price.
        //         */
        //        readonly currencySymbol: string;
        //        /**
        //         * Boolean indicating if the product is sharable with family
        //         */
        //        readonly isFamilyShareable: boolean;
        //        /**
        //         * Group identifier for the product.
        //         */
        //        readonly subscriptionGroupIdentifier: string;
        //        /**
        //         * The Product subscription group identifier.
        //         */
        //        readonly subscriptionPeriod: SubscriptionPeriod;
        //        /**
        //         * The Product introductory Price.
        //         */
        //        readonly introductoryPrice: SKProductDiscount | null;
        //        /**
        //         * The Product discounts list.
        //         */
        //        readonly discounts: SKProductDiscount[];
        var result: [String: Any] = [
            "identifier": self.id,
            "description": self.description,
            "title": self.displayName,
            "price": self.price,
            "priceString": self.displayPrice,
            "currencyCode": self.priceFormatStyle.currencyCode,
            "isFamilyShareable": self.isFamilyShareable
        ]

        // Subscription-specific fields
        if let subscription = self.subscription {
            result["subscriptionGroupIdentifier"] = subscription.subscriptionGroupID
            result["subscriptionPeriod"] = subscription.subscriptionPeriod.dictionary

            if let introOffer = subscription.introductoryOffer {
                result["introductoryPrice"] = introOffer.dictionary(currencyCode: self.priceFormatStyle.currencyCode)
            } else {
                result["introductoryPrice"] = NSNull()
            }

            let promotionalOffers = subscription.promotionalOffers
            result["discounts"] = promotionalOffers.map {
                $0.dictionary(currencyCode: self.priceFormatStyle.currencyCode)
            }
        } else {
            result["subscriptionGroupIdentifier"] = ""
            result["subscriptionPeriod"] = ["numberOfUnits": 0, "unit": 0]
            result["introductoryPrice"] = NSNull()
            result["discounts"] = [] as [[String: Any]]
        }

        return result
    }
}

// MARK: - Product.SubscriptionPeriod helpers

extension Product.SubscriptionPeriod {

    var dictionary: [String: Any] {
        return [
            "numberOfUnits": self.value,
            "unit": self.unit.intValue
        ]
    }
}

extension Product.SubscriptionPeriod.Unit {

    /// Map to SKProduct.PeriodUnit-compatible integer values:
    /// day=0, week=1, month=2, year=3
    var intValue: Int {
        switch self {
        case .day: return 0
        case .week: return 1
        case .month: return 2
        case .year: return 3
        @unknown default: return -1
        }
    }
}

// MARK: - Product.SubscriptionOffer helpers

extension Product.SubscriptionOffer {

    /// Convert a SubscriptionOffer to the SKProductDiscount-compatible dictionary
    /// expected by the TypeScript layer.
    func dictionary(currencyCode: String) -> [String: Any] {
        return [
            "identifier": self.id ?? "",
            "type": self.type.intValue,
            "price": self.price,
            "priceString": self.displayPrice,
            "currencyCode": currencyCode,
            "paymentMode": self.paymentMode.intValue,
            "numberOfPeriods": self.periodCount,
            "subscriptionPeriod": self.period.dictionary
        ]
    }
}

extension Product.SubscriptionOffer.OfferType {

    /// Map to SKProductDiscount.Type-compatible integer values:
    /// introductory=0, promotional=1
    var intValue: Int {
        switch self {
        case .introductory: return 0
        case .promotional: return 1
        default: return -1
        }
    }
}

extension Product.SubscriptionOffer.PaymentMode {

    /// Map to SKProductDiscount.PaymentMode-compatible integer values:
    /// freeTrial=0, payUpFront=1, payAsYouGo=2
    /// TODO: Consider migrating to string literals for better readability and forward compatibility.
    var intValue: Int {
        switch self {
        case .freeTrial: return 0
        case .payUpFront: return 1
        case .payAsYouGo: return 2
        default: return -1
        }
    }
}
