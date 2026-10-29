package ee.forgr.nativepurchases;

import com.android.billingclient.api.BillingFlowParams;

/**
 * Maps the replacement mode name passed from JavaScript to Play Billing's item-level constant
 * ({@code BillingFlowParams.ProductDetailsParams.SubscriptionProductReplacementParams.ReplacementMode}).
 *
 * <p>The name is mapped here instead of accepting a number because Play Billing exposes two tables with
 * different values: {@code DEFERRED} is 5 in the item-level table and 6 in
 * {@code SubscriptionUpdateParams.ReplacementMode}, and the exported JS {@code PRORATION_MODE} enum still uses
 * the older ProrationMode numbering (4).
 */
final class SubscriptionReplacementModes {

    /** Returned for a missing or unsupported name. Callers must reject the purchase instead of guessing. */
    static final int UNKNOWN = 0;

    private SubscriptionReplacementModes() {}

    /**
     * Returns {@code null} when the replacement options are consistent, otherwise the message to reject the call with.
     *
     * <p>A partial request ({@code oldProductIdentifier} or {@code replacementMode} without {@code oldPurchaseToken}) is
     * rejected instead of being treated as a normal purchase, which would create a second subscription.
     */
    static String validationError(String productType, String oldPurchaseToken, String oldProductIdentifier, String replacementModeName) {
        boolean hasToken = isSet(oldPurchaseToken);
        if (!hasToken) {
            return isSet(oldProductIdentifier) || isSet(replacementModeName)
                ? "oldPurchaseToken is required when oldProductIdentifier or replacementMode is set"
                : null;
        }
        if (!"subs".equals(productType)) {
            return "oldPurchaseToken is only supported for subscriptions";
        }
        if (!isSet(oldProductIdentifier)) {
            return "oldProductIdentifier cannot be empty when oldPurchaseToken is set";
        }
        if (fromName(replacementModeName) == UNKNOWN) {
            return "Unknown replacementMode: " + replacementModeName;
        }
        return null;
    }

    private static boolean isSet(String value) {
        return value != null && !value.isEmpty();
    }

    static int fromName(String name) {
        if (name == null) {
            return UNKNOWN;
        }
        switch (name) {
            case "WITH_TIME_PRORATION":
                return BillingFlowParams.ProductDetailsParams.SubscriptionProductReplacementParams.ReplacementMode.WITH_TIME_PRORATION;
            case "CHARGE_PRORATED_PRICE":
                return BillingFlowParams.ProductDetailsParams.SubscriptionProductReplacementParams.ReplacementMode.CHARGE_PRORATED_PRICE;
            case "WITHOUT_PRORATION":
                return BillingFlowParams.ProductDetailsParams.SubscriptionProductReplacementParams.ReplacementMode.WITHOUT_PRORATION;
            case "CHARGE_FULL_PRICE":
                return BillingFlowParams.ProductDetailsParams.SubscriptionProductReplacementParams.ReplacementMode.CHARGE_FULL_PRICE;
            case "DEFERRED":
                return BillingFlowParams.ProductDetailsParams.SubscriptionProductReplacementParams.ReplacementMode.DEFERRED;
            default:
                return UNKNOWN;
        }
    }
}
