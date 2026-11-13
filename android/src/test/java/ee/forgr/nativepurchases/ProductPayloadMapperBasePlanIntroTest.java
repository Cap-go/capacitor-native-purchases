package ee.forgr.nativepurchases;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.billingclient.api.ProductDetails;
import com.getcapacitor.JSObject;
import java.util.List;
import org.json.JSONObject;
import org.junit.Test;

/**
 * A base plan entry must not report a sibling offer's trial on introductoryPrice, because its own
 * offerToken buys the base offer at full price. The trial belongs on the offer's own entry.
 */
public class ProductPayloadMapperBasePlanIntroTest {

    private static final int RECURRENCE_MODE_INFINITE_RECURRING = 1;
    private static final int RECURRENCE_MODE_FINITE_RECURRING = 2;

    private final ProductDetails.PricingPhase recurring = phase(9_990_000L, "$9.99", "P1M", RECURRENCE_MODE_INFINITE_RECURRING, 0);
    private final ProductDetails.PricingPhase trial = phase(0L, "Free", "P3D", RECURRENCE_MODE_FINITE_RECURRING, 1);
    private final ProductDetails.SubscriptionOfferDetails basePlan = offer(null, "monthly", List.of(recurring));
    private final ProductDetails.SubscriptionOfferDetails trialOffer = offer("free-trial", "monthly", List.of(trial, recurring));
    private final List<ProductDetails.SubscriptionOfferDetails> allOffers = List.of(basePlan, trialOffer);

    @Test
    public void basePlanEntry_withASiblingTrialOffer_hasNoIntroductoryPrice() throws Exception {
        JSObject product = new JSObject();

        ProductPayloadMapper.applySubscriptionPricing(product, basePlan, allOffers);

        assertTrue(product.has("introductoryPrice"));
        assertTrue(product.isNull("introductoryPrice"));
        assertEquals(9.99, product.getDouble("price"), 0.0001);
    }

    @Test
    public void trialOfferEntry_reportsItsOwnTrial() throws Exception {
        JSObject product = new JSObject();

        ProductPayloadMapper.applySubscriptionPricing(product, trialOffer, allOffers);

        JSONObject intro = product.getJSONObject("introductoryPrice");
        assertEquals("free-trial", intro.getString("identifier"));
        assertEquals(0.0, intro.getDouble("price"), 0.0001);
        assertEquals(0, intro.getInt("paymentMode"));
    }

    private static ProductDetails.PricingPhase phase(long micros, String formatted, String period, int recurrenceMode, int cycles) {
        ProductDetails.PricingPhase phase = mock(ProductDetails.PricingPhase.class);
        when(phase.getPriceAmountMicros()).thenReturn(micros);
        when(phase.getFormattedPrice()).thenReturn(formatted);
        when(phase.getPriceCurrencyCode()).thenReturn("USD");
        when(phase.getBillingPeriod()).thenReturn(period);
        when(phase.getRecurrenceMode()).thenReturn(recurrenceMode);
        when(phase.getBillingCycleCount()).thenReturn(cycles);
        return phase;
    }

    private static ProductDetails.SubscriptionOfferDetails offer(
        String offerId,
        String basePlanId,
        List<ProductDetails.PricingPhase> phases
    ) {
        ProductDetails.PricingPhases pricingPhases = mock(ProductDetails.PricingPhases.class);
        when(pricingPhases.getPricingPhaseList()).thenReturn(phases);
        ProductDetails.SubscriptionOfferDetails offer = mock(ProductDetails.SubscriptionOfferDetails.class);
        when(offer.getOfferId()).thenReturn(offerId);
        when(offer.getBasePlanId()).thenReturn(basePlanId);
        when(offer.getPricingPhases()).thenReturn(pricingPhases);
        return offer;
    }
}
