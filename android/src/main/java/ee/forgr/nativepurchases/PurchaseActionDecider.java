package ee.forgr.nativepurchases;

import com.android.billingclient.api.Purchase;

enum PurchaseAction {
    CONSUME,
    ACKNOWLEDGE,
    NONE
}

final class PurchaseActionDecider {

    private PurchaseActionDecider() {}

    static PurchaseAction decide(boolean isConsumable, Purchase purchase) {
        if (purchase == null) {
            return PurchaseAction.NONE;
        }
        if (purchase.getPurchaseState() != Purchase.PurchaseState.PURCHASED) {
            return PurchaseAction.NONE;
        }
        if (isConsumable) {
            return PurchaseAction.CONSUME;
        }
        if (purchase.isAcknowledged()) {
            return PurchaseAction.NONE;
        }
        return PurchaseAction.ACKNOWLEDGE;
    }
}
