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
        return decide(isConsumable, purchase == null ? null : new PurchaseDetailsAdapter(purchase));
    }

    static PurchaseAction decide(boolean isConsumable, PurchaseDetails purchase) {
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

    interface PurchaseDetails {
        int getPurchaseState();
        boolean isAcknowledged();
    }

    private static final class PurchaseDetailsAdapter implements PurchaseDetails {

        private final Purchase purchase;

        PurchaseDetailsAdapter(Purchase purchase) {
            this.purchase = purchase;
        }

        @Override
        public int getPurchaseState() {
            return purchase.getPurchaseState();
        }

        @Override
        public boolean isAcknowledged() {
            return purchase.isAcknowledged();
        }
    }
}
