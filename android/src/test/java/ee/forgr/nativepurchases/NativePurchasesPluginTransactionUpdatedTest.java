package ee.forgr.nativepurchases;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.util.Log;
import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.AcknowledgePurchaseResponseListener;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.Purchase;
import com.getcapacitor.JSObject;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

public class NativePurchasesPluginTransactionUpdatedTest {

    private static final BillingResult ok = BillingResult.newBuilder().setResponseCode(BillingClient.BillingResponseCode.OK).build();

    private MockedStatic<Log> log;
    private final BillingClient client = mock(BillingClient.class);
    private final Purchase purchase = mock(Purchase.class);
    private final RecordingNativePurchasesPlugin plugin = new RecordingNativePurchasesPlugin();

    @Before
    public void setUp() {
        log = mockStatic(Log.class);
        when(client.isReady()).thenReturn(true);
        when(purchase.getPurchaseState()).thenReturn(Purchase.PurchaseState.PURCHASED);
        when(purchase.getPurchaseToken()).thenReturn("token");
        when(purchase.getProducts()).thenReturn(List.of("premium"));
        when(purchase.isAcknowledged()).thenReturn(false);
        when(purchase.isAutoRenewing()).thenReturn(false);
        when(purchase.getPurchaseTime()).thenReturn(1_700_000_000_000L);
        when(purchase.getQuantity()).thenReturn(1);
        when(purchase.getOrderId()).thenReturn("order");
    }

    @After
    public void tearDown() {
        log.close();
    }

    @Test
    public void processPurchaseForTransactionUpdated_setsNeedsFinishWhenManualMode() {
        plugin.setAutoFinishTransactionsForTest(false);

        plugin.processPurchaseForTransactionUpdated(purchase, "inapp", client);

        assertEquals(1, plugin.transactionUpdatedEvents.size());
        assertTrue(plugin.transactionUpdatedEvents.get(0).has("needsFinish"));
        assertTrue(plugin.transactionUpdatedEvents.get(0).getBoolean("needsFinish"));
        verify(client, never()).acknowledgePurchase(any(), any());
    }

    @Test
    public void processPurchaseForTransactionUpdated_autoAcknowledgesBeforeEmit() {
        plugin.setAutoFinishTransactionsForTest(true);

        plugin.processPurchaseForTransactionUpdated(purchase, "inapp", client);

        ArgumentCaptor<AcknowledgePurchaseResponseListener> listener = ArgumentCaptor.forClass(AcknowledgePurchaseResponseListener.class);
        verify(client).acknowledgePurchase(any(AcknowledgePurchaseParams.class), listener.capture());
        assertTrue(plugin.transactionUpdatedEvents.isEmpty());

        listener.getValue().onAcknowledgePurchaseResponse(ok);

        assertEquals(1, plugin.transactionUpdatedEvents.size());
        assertTrue(plugin.transactionUpdatedEvents.get(0).getBoolean("isAcknowledged"));
    }

    @Test
    public void handleUpdatesPurchasesUpdated_emitsForEachPurchase() {
        Purchase second = mock(Purchase.class);
        when(second.getPurchaseState()).thenReturn(Purchase.PurchaseState.PENDING);
        when(second.getPurchaseToken()).thenReturn("token2");
        when(second.getProducts()).thenReturn(List.of("other"));
        when(second.isAutoRenewing()).thenReturn(false);
        when(second.getPurchaseTime()).thenReturn(1_700_000_000_100L);
        when(second.getQuantity()).thenReturn(1);
        when(second.getOrderId()).thenReturn("order2");

        plugin.processPurchaseForTransactionUpdated = false;
        plugin.handleUpdatesPurchasesUpdated(ok, List.of(purchase, second), client);

        assertEquals(2, plugin.transactionUpdatedEvents.size());
    }

    private static final class RecordingNativePurchasesPlugin extends NativePurchasesPlugin {

        final List<JSObject> transactionUpdatedEvents = new ArrayList<>();
        boolean processPurchaseForTransactionUpdated = true;

        @Override
        protected boolean hasListeners(String eventName) {
            return "transactionUpdated".equals(eventName) || super.hasListeners(eventName);
        }

        @Override
        protected void notifyListeners(String eventName, JSObject data) {
            if ("transactionUpdated".equals(eventName)) {
                transactionUpdatedEvents.add(data);
            }
        }

        @Override
        void processPurchaseForTransactionUpdated(Purchase purchase, String productType, BillingClient client) {
            if (!processPurchaseForTransactionUpdated) {
                transactionUpdatedEvents.add(new JSObject());
                return;
            }
            super.processPurchaseForTransactionUpdated(purchase, productType, client);
        }
    }
}
