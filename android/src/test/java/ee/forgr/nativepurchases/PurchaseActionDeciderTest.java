package ee.forgr.nativepurchases;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.billingclient.api.Purchase;
import org.junit.Test;

public class PurchaseActionDeciderTest {

    @Test
    public void decideReturnsConsumeForConsumables() {
        Purchase purchase = mockPurchase(Purchase.PurchaseState.PURCHASED, true);

        PurchaseAction action = PurchaseActionDecider.decide(true, purchase);

        assertEquals(PurchaseAction.CONSUME, action);
    }

    @Test
    public void decideAcknowledgesWhenNotConsumableAndNotAcknowledged() {
        Purchase purchase = mockPurchase(Purchase.PurchaseState.PURCHASED, false);

        PurchaseAction action = PurchaseActionDecider.decide(false, purchase);

        assertEquals(PurchaseAction.ACKNOWLEDGE, action);
    }

    @Test
    public void decideDoesNothingWhenAlreadyAcknowledged() {
        Purchase purchase = mockPurchase(Purchase.PurchaseState.PURCHASED, true);

        PurchaseAction action = PurchaseActionDecider.decide(false, purchase);

        assertEquals(PurchaseAction.NONE, action);
    }

    @Test
    public void decideIgnoresNonPurchasedStates() {
        Purchase purchase = mockPurchase(Purchase.PurchaseState.PENDING, false);

        PurchaseAction action = PurchaseActionDecider.decide(false, purchase);

        assertEquals(PurchaseAction.NONE, action);
    }

    @Test
    public void decideHandlesNullPurchase() {
        PurchaseAction action = PurchaseActionDecider.decide(false, null);

        assertEquals(PurchaseAction.NONE, action);
    }

    private Purchase mockPurchase(int state, boolean acknowledged) {
        Purchase purchase = mock(Purchase.class);
        when(purchase.getPurchaseState()).thenReturn(state);
        when(purchase.isAcknowledged()).thenReturn(acknowledged);
        return purchase;
    }
}
