package ee.forgr.nativepurchases;

import com.android.billingclient.api.ProductDetails;
import com.getcapacitor.JSObject;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;

final class ProductPayloadMapper {

    private static final int RECURRENCE_MODE_INFINITE_RECURRING = 1;
    private static final int RECURRENCE_MODE_FINITE_RECURRING = 2;
    private static final int RECURRENCE_MODE_NON_RECURRING = 3;

    private static final Pattern ISO_8601_PERIOD = Pattern.compile("^P(?:(\\d+)Y)?(?:(\\d+)M)?(?:(\\d+)W)?(?:(\\d+)D)?$");

    private ProductPayloadMapper() {}

    static void applyInAppDefaults(JSObject product) {
        product.put("introductoryPrice", JSONObject.NULL);
        product.put("discounts", new JSONArray());
    }

    static void applySubscriptionPricing(
        JSObject product,
        ProductDetails.SubscriptionOfferDetails offerDetails,
        List<ProductDetails.SubscriptionOfferDetails> allOffers
    ) {
        List<ProductDetails.PricingPhase> phases =
            offerDetails.getPricingPhases() == null ? List.of() : offerDetails.getPricingPhases().getPricingPhaseList();

        ProductDetails.PricingPhase standardPhase = findStandardPricingPhase(phases);
        if (standardPhase != null) {
            double price = standardPhase.getPriceAmountMicros() / 1_000_000.0;
            product.put("price", price);
            product.put("priceString", standardPhase.getFormattedPrice());
            product.put("currencyCode", standardPhase.getPriceCurrencyCode());
            product.put("currencySymbol", currencySymbol(standardPhase.getPriceCurrencyCode()));
        }

        String currencyCode = standardPhase != null ? standardPhase.getPriceCurrencyCode() : product.getString("currencyCode", "");
        String currencySymbol = currencySymbol(currencyCode);

        if (offerDetails.getOfferId() == null) {
            ProductDetails.PricingPhase introPhase = findIntroductoryPricingPhase(phases);
            if (introPhase != null) {
                product.put("introductoryPrice", pricingPhaseToDiscount(introPhase, "", 0, currencyCode, currencySymbol));
            } else {
                product.put("introductoryPrice", JSONObject.NULL);
            }
            product.put(
                "discounts",
                promotionalDiscountsForBasePlan(offerDetails.getBasePlanId(), allOffers, currencyCode, currencySymbol)
            );
        } else {
            product.put("introductoryPrice", JSONObject.NULL);
            product.put("discounts", new JSONArray());
        }
    }

    static JSObject subscriptionPeriodFromBillingPeriod(String billingPeriod) {
        JSObject period = parseIso8601Period(billingPeriod);
        if (period == null) {
            JSObject fallback = new JSObject();
            fallback.put("numberOfUnits", 0);
            fallback.put("unit", 0);
            return fallback;
        }
        return period;
    }

    static JSObject parseIso8601Period(String billingPeriod) {
        if (billingPeriod == null || billingPeriod.isEmpty()) {
            return null;
        }

        Matcher matcher = ISO_8601_PERIOD.matcher(billingPeriod);
        if (!matcher.matches()) {
            return null;
        }

        int[][] candidates = {
            { matcher.group(1) == null ? 0 : Integer.parseInt(matcher.group(1)), 3, 12 }, // year
            { matcher.group(2) == null ? 0 : Integer.parseInt(matcher.group(2)), 2, 11 }, // month
            { matcher.group(3) == null ? 0 : Integer.parseInt(matcher.group(3)), 1, 10 }, // week
            { matcher.group(4) == null ? 0 : Integer.parseInt(matcher.group(4)), 0, 9 } // day
        };

        for (int[] candidate : candidates) {
            int numberOfUnits = candidate[0];
            if (numberOfUnits > 0) {
                JSObject period = new JSObject();
                period.put("numberOfUnits", numberOfUnits);
                period.put("unit", candidate[1]);
                period.put("unitString", unitStringFor(candidate[1]));
                return period;
            }
        }

        return null;
    }

    static String currencySymbol(String currencyCode) {
        if (currencyCode == null || currencyCode.isEmpty()) {
            return "";
        }
        try {
            return Currency.getInstance(currencyCode).getSymbol(Locale.getDefault());
        } catch (IllegalArgumentException ignored) {
            return currencyCode;
        }
    }

    private static ProductDetails.PricingPhase findStandardPricingPhase(List<ProductDetails.PricingPhase> phases) {
        if (phases == null || phases.isEmpty()) {
            return null;
        }

        for (int index = phases.size() - 1; index >= 0; index--) {
            ProductDetails.PricingPhase phase = phases.get(index);
            if (phase.getRecurrenceMode() == RECURRENCE_MODE_INFINITE_RECURRING) {
                return phase;
            }
        }

        return phases.get(phases.size() - 1);
    }

    private static ProductDetails.PricingPhase findIntroductoryPricingPhase(List<ProductDetails.PricingPhase> phases) {
        if (phases == null || phases.size() < 2) {
            return null;
        }

        ProductDetails.PricingPhase firstPhase = phases.get(0);
        if (firstPhase.getRecurrenceMode() == RECURRENCE_MODE_INFINITE_RECURRING) {
            return null;
        }

        return firstPhase;
    }

    private static JSONArray promotionalDiscountsForBasePlan(
        String basePlanId,
        List<ProductDetails.SubscriptionOfferDetails> allOffers,
        String currencyCode,
        String currencySymbol
    ) {
        JSONArray discounts = new JSONArray();
        if (allOffers == null) {
            return discounts;
        }

        for (ProductDetails.SubscriptionOfferDetails offer : allOffers) {
            if (offer.getOfferId() == null || !basePlanId.equals(offer.getBasePlanId())) {
                continue;
            }

            List<ProductDetails.PricingPhase> phases =
                offer.getPricingPhases() == null ? List.of() : offer.getPricingPhases().getPricingPhaseList();
            ProductDetails.PricingPhase phase = findStandardPricingPhase(phases);
            if (phase == null) {
                continue;
            }

            discounts.put(pricingPhaseToDiscount(phase, offer.getOfferId(), 1, currencyCode, currencySymbol));
        }

        return discounts;
    }

    private static JSObject pricingPhaseToDiscount(
        ProductDetails.PricingPhase phase,
        String identifier,
        int type,
        String currencyCode,
        String currencySymbol
    ) {
        JSObject discount = new JSObject();
        discount.put("identifier", identifier == null ? "" : identifier);
        discount.put("type", type);
        discount.put("price", phase.getPriceAmountMicros() / 1_000_000.0);
        discount.put("priceString", phase.getFormattedPrice());
        discount.put("currencySymbol", currencySymbol);
        discount.put("currencyCode", currencyCode);
        discount.put("paymentMode", paymentModeForPhase(phase));
        discount.put("numberOfPeriods", phase.getBillingCycleCount());
        discount.put("subscriptionPeriod", subscriptionPeriodFromBillingPeriod(phase.getBillingPeriod()));
        return discount;
    }

    private static int paymentModeForPhase(ProductDetails.PricingPhase phase) {
        if (phase.getPriceAmountMicros() == 0) {
            return 0;
        }
        if (phase.getRecurrenceMode() == RECURRENCE_MODE_NON_RECURRING) {
            return 1;
        }
        return 2;
    }

    private static String unitStringFor(int unit) {
        return switch (unit) {
            case 0 -> "day";
            case 1 -> "week";
            case 2 -> "month";
            case 3 -> "year";
            default -> "unknown";
        };
    }
}
