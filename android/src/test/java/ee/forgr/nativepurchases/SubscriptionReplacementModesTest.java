package ee.forgr.nativepurchases;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.android.billingclient.api.BillingFlowParams;
import org.junit.Test;

public class SubscriptionReplacementModesTest {

    @Test
    public void mapsEachNameToTheItemLevelConstant() {
        assertEquals(
            BillingFlowParams.ProductDetailsParams.SubscriptionProductReplacementParams.ReplacementMode.WITH_TIME_PRORATION,
            SubscriptionReplacementModes.fromName("WITH_TIME_PRORATION")
        );
        assertEquals(
            BillingFlowParams.ProductDetailsParams.SubscriptionProductReplacementParams.ReplacementMode.CHARGE_PRORATED_PRICE,
            SubscriptionReplacementModes.fromName("CHARGE_PRORATED_PRICE")
        );
        assertEquals(
            BillingFlowParams.ProductDetailsParams.SubscriptionProductReplacementParams.ReplacementMode.WITHOUT_PRORATION,
            SubscriptionReplacementModes.fromName("WITHOUT_PRORATION")
        );
        assertEquals(
            BillingFlowParams.ProductDetailsParams.SubscriptionProductReplacementParams.ReplacementMode.CHARGE_FULL_PRICE,
            SubscriptionReplacementModes.fromName("CHARGE_FULL_PRICE")
        );
        assertEquals(
            BillingFlowParams.ProductDetailsParams.SubscriptionProductReplacementParams.ReplacementMode.DEFERRED,
            SubscriptionReplacementModes.fromName("DEFERRED")
        );
    }

    @Test
    public void deferredDoesNotUseThePurchaseLevelNumber() {
        assertNotEquals(
            BillingFlowParams.SubscriptionUpdateParams.ReplacementMode.DEFERRED,
            SubscriptionReplacementModes.fromName("DEFERRED")
        );
    }

    @Test
    public void unknownNamesAreRejected() {
        assertEquals(SubscriptionReplacementModes.UNKNOWN, SubscriptionReplacementModes.fromName(null));
        assertEquals(SubscriptionReplacementModes.UNKNOWN, SubscriptionReplacementModes.fromName(""));
        assertEquals(SubscriptionReplacementModes.UNKNOWN, SubscriptionReplacementModes.fromName("deferred"));
        assertEquals(SubscriptionReplacementModes.UNKNOWN, SubscriptionReplacementModes.fromName("KEEP_EXISTING"));
        assertEquals(SubscriptionReplacementModes.UNKNOWN, SubscriptionReplacementModes.fromName("6"));
    }
}
