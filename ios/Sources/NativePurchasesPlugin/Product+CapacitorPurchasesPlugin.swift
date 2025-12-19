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
        return [
            "identifier": self.id,
            "description": self.description,
            "title": self.displayName,
            "price": self.price,
            "priceString": self.displayPrice,
            "currencyCode": self.priceFormatStyle.currencyCode,
            "isFamilyShareable": self.isFamilyShareable
        ]
    }
}
