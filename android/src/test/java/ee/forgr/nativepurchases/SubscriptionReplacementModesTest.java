package ee.forgr.nativepurchases;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

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
    public void aCompleteReplacementRequestIsValid() {
        assertNull(SubscriptionReplacementModes.validationError("subs", "old-token", "basic", "CHARGE_PRORATED_PRICE"));
        assertNull(SubscriptionReplacementModes.validationError("subs", null, null, null));
        assertNull(SubscriptionReplacementModes.validationError("inapp", "", "", ""));
    }

    @Test
    public void partialReplacementOptionsAreRejectedInsteadOfBuyingASecondSubscription() {
        assertNotNull(SubscriptionReplacementModes.validationError("subs", null, "basic", null));
        assertNotNull(SubscriptionReplacementModes.validationError("subs", "", null, "DEFERRED"));
        assertNotNull(SubscriptionReplacementModes.validationError("subs", null, "basic", "DEFERRED"));
    }

    @Test
    public void inconsistentReplacementRequestsAreRejected() {
        assertNotNull(SubscriptionReplacementModes.validationError("inapp", "old-token", "basic", "DEFERRED"));
        assertNotNull(SubscriptionReplacementModes.validationError("subs", "old-token", "", "DEFERRED"));
        assertNotNull(SubscriptionReplacementModes.validationError("subs", "old-token", "basic", null));
        assertNotNull(SubscriptionReplacementModes.validationError("subs", "old-token", "basic", "KEEP_EXISTING"));
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
