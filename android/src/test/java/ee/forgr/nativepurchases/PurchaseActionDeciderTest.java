package ee.forgr.nativepurchases;

import static org.junit.Assert.assertEquals;

import com.android.billingclient.api.Purchase;
import ee.forgr.nativepurchases.PurchaseActionDecider.PurchaseDetails;
import org.junit.Test;

public class PurchaseActionDeciderTest {

    @Test
    public void decideReturnsConsumeForConsumables() {
        PurchaseAction action =
                PurchaseActionDecider.decide(true, purchaseDetails(Purchase.PurchaseState.PURCHASED, true));

        assertEquals(PurchaseAction.CONSUME, action);
    }

    @Test
    public void decideAcknowledgesWhenNotConsumableAndNotAcknowledged() {
        PurchaseAction action =
                PurchaseActionDecider.decide(false, purchaseDetails(Purchase.PurchaseState.PURCHASED, false));

        assertEquals(PurchaseAction.ACKNOWLEDGE, action);
    }

    @Test
    public void decideDoesNothingWhenAlreadyAcknowledged() {
        PurchaseAction action =
                PurchaseActionDecider.decide(false, purchaseDetails(Purchase.PurchaseState.PURCHASED, true));

        assertEquals(PurchaseAction.NONE, action);
    }

    @Test
    public void decideIgnoresNonPurchasedStates() {
        PurchaseAction action =
                PurchaseActionDecider.decide(false, purchaseDetails(Purchase.PurchaseState.PENDING, false));

        assertEquals(PurchaseAction.NONE, action);
    }

    @Test
    public void decideHandlesNullPurchase() {
        PurchaseAction action = PurchaseActionDecider.decide(false, (PurchaseDetails) null);

        assertEquals(PurchaseAction.NONE, action);
    }

    private PurchaseDetails purchaseDetails(int state, boolean acknowledged) {
        return new PurchaseDetails() {
            @Override
            public int getPurchaseState() {
                return state;
            }

            @Override
            public boolean isAcknowledged() {
                return acknowledged;
            }
        };
    }
}
