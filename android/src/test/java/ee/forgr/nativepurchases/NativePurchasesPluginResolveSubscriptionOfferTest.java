package ee.forgr.nativepurchases;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.when;

import com.android.billingclient.api.ProductDetails;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Asserts the subscription offer token {@link NativePurchasesPlugin#purchaseProduct} feeds into
 * {@link com.android.billingclient.api.BillingFlowParams}, via
 * {@link NativePurchasesPlugin#resolveSubscriptionOfferForPurchase}.
 */
@RunWith(MockitoJUnitRunner.class)
public class NativePurchasesPluginResolveSubscriptionOfferTest {

    @Mock
    private ProductDetails.SubscriptionOfferDetails offerMonthly;

    @Mock
    private ProductDetails.SubscriptionOfferDetails offerYearly;

    @Test
    public void resolve_prefersExplicitOfferTokenOverPlanIdentifier() {
        when(offerMonthly.getOfferToken()).thenReturn("token-monthly");
        when(offerYearly.getOfferToken()).thenReturn("token-yearly");
        when(offerYearly.getBasePlanId()).thenReturn("yearly");

        List<ProductDetails.SubscriptionOfferDetails> offers = List.of(offerMonthly, offerYearly);
        NativePurchasesPlugin.SubscriptionOfferPurchaseResult result = NativePurchasesPlugin.resolveSubscriptionOfferForPurchase(
            offers,
            "monthly",
            "token-yearly"
        );

        assertEquals(NativePurchasesPlugin.SubscriptionOfferPurchaseOutcome.OK, result.outcome);
        assertEquals("token-yearly", result.offerToken);
        assertEquals("yearly", result.basePlanId);
    }

    @Test
    public void resolve_withPlanOnly_setsFirstMatchingBasePlanToken() {
        when(offerMonthly.getBasePlanId()).thenReturn("monthly");
        when(offerYearly.getBasePlanId()).thenReturn("yearly");
        when(offerYearly.getOfferToken()).thenReturn("token-yearly");

        List<ProductDetails.SubscriptionOfferDetails> offers = List.of(offerMonthly, offerYearly);
        NativePurchasesPlugin.SubscriptionOfferPurchaseResult result = NativePurchasesPlugin.resolveSubscriptionOfferForPurchase(
            offers,
            "yearly",
            null
        );

        assertEquals(NativePurchasesPlugin.SubscriptionOfferPurchaseOutcome.OK, result.outcome);
        assertEquals("token-yearly", result.offerToken);
        assertEquals("yearly", result.basePlanId);
    }

    @Test
    public void resolve_unknownOfferToken_returnsNotFound() {
        when(offerMonthly.getOfferToken()).thenReturn("token-monthly");

        NativePurchasesPlugin.SubscriptionOfferPurchaseResult result = NativePurchasesPlugin.resolveSubscriptionOfferForPurchase(
            Collections.singletonList(offerMonthly),
            "monthly",
            "token-stale"
        );

        assertEquals(NativePurchasesPlugin.SubscriptionOfferPurchaseOutcome.OFFER_TOKEN_NOT_FOUND, result.outcome);
        assertNull(result.offerToken);
        assertNull(result.basePlanId);
    }

    @Test
    public void resolve_emptyOffers_returnsNoOffers() {
        NativePurchasesPlugin.SubscriptionOfferPurchaseResult result = NativePurchasesPlugin.resolveSubscriptionOfferForPurchase(
            Collections.emptyList(),
            "monthly",
            null
        );

        assertEquals(NativePurchasesPlugin.SubscriptionOfferPurchaseOutcome.NO_OFFERS, result.outcome);
        assertNull(result.offerToken);
    }
}
