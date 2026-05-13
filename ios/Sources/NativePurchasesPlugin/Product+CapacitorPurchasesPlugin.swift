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
        let currencyCode = self.priceFormatStyle.currencyCode
        let currencySymbol = StoreKitPayloadHelpers.currencySymbol(
            forCurrencyCode: currencyCode,
            locale: self.priceFormatStyle.locale
        )

        var product: [String: Any] = [
            "identifier": self.id,
            "description": self.description,
            "title": self.displayName,
            "price": self.price,
            "priceString": self.displayPrice,
            "currencyCode": currencyCode,
            "currencySymbol": currencySymbol,
            "isFamilyShareable": self.isFamilyShareable
        ]

        if let subscription = self.subscription {
            product["subscriptionGroupIdentifier"] = subscription.subscriptionGroupID
            product["subscriptionPeriod"] = subscription.subscriptionPeriod.dictionary

            if let introOffer = subscription.introductoryOffer {
                product["introductoryPrice"] = introOffer.dictionary(
                    currencyCode: currencyCode,
                    currencySymbol: currencySymbol
                )
            } else {
                product["introductoryPrice"] = NSNull()
            }

            product["discounts"] = subscription.promotionalOffers.map {
                $0.dictionary(currencyCode: currencyCode, currencySymbol: currencySymbol)
            }
        } else {
            product["subscriptionGroupIdentifier"] = ""
            product["subscriptionPeriod"] = ["numberOfUnits": 0, "unit": 0]
            product["introductoryPrice"] = NSNull()
            product["discounts"] = [] as [[String: Any]]
        }

        #if STOREKIT_26_5
        if #available(iOS 26.4, *), let pricingTerms = self.subscription?.pricingTerms {
            product["pricingTerms"] = pricingTerms.map { $0.dictionary }
        }
        #endif

        if product["pricingTerms"] == nil,
           let productJSON = StoreKitPayloadHelpers.jsonDictionary(from: self.jsonRepresentation),
           let pricingTerms = StoreKitPayloadHelpers.pricingTerms(from: productJSON) {
            product["pricingTerms"] = pricingTerms
        }

        return product
    }
}

private extension Product.SubscriptionOffer {

    /// Convert a SubscriptionOffer to the SKProductDiscount-compatible dictionary
    /// expected by the TypeScript layer.
    func dictionary(currencyCode: String, currencySymbol: String) -> [String: Any] {
        [
            "identifier": self.id ?? "",
            "type": self.type.intValue,
            "price": self.price,
            "priceString": self.displayPrice,
            "currencySymbol": currencySymbol,
            "currencyCode": currencyCode,
            "paymentMode": self.paymentMode.intValue,
            "numberOfPeriods": self.periodCount,
            "subscriptionPeriod": self.period.dictionary
        ]
    }
}

private extension Product.SubscriptionOffer.OfferType {

    /// Map to SKProductDiscount.Type-compatible integer values:
    /// introductory=0, promotional=1, winBack=2
    var intValue: Int {
        if self == .introductory {
            return 0
        }
        if self == .promotional {
            return 1
        }
        if #available(iOS 18.0, *), self == .winBack {
            return 2
        }

        assertionFailure("Unknown SubscriptionOffer.OfferType: \(self)")
        return -1
    }
}

private extension Product.SubscriptionOffer.PaymentMode {

    /// Map to SKProductDiscount.PaymentMode-compatible integer values:
    /// freeTrial=0, payUpFront=1, payAsYouGo=2
    /// TODO: Consider migrating to string literals for better readability and forward compatibility.
    var intValue: Int {
        switch self {
        case .freeTrial:
            return 0
        case .payUpFront:
            return 1
        case .payAsYouGo:
            return 2
        default:
            return -1
        }
    }
}
