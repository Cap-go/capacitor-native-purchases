package ee.forgr.nativepurchases;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.AcknowledgePurchaseResponseListener;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ConsumeParams;
import com.android.billingclient.api.ConsumeResponseListener;
import com.android.billingclient.api.Purchase;
import com.getcapacitor.JSObject;
import com.getcapacitor.PluginCall;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

/**
 * BillingClient.endConnection() shuts down the executor that runs acknowledge and consume, so the
 * client must stay open until Play answers.
 */
public class NativePurchasesPluginPurchaseCompletionTest {

    private final BillingResult ok = BillingResult.newBuilder().setResponseCode(BillingClient.BillingResponseCode.OK).build();
    private final BillingClient client = mock(BillingClient.class);
    private final PluginCall call = mock(PluginCall.class);
    private final Purchase purchase = mock(Purchase.class);
    private final NativePurchasesPlugin plugin = new NativePurchasesPlugin();

    @Before
    public void setUp() {
        plugin.billingClient = client;
        when(client.isReady()).thenReturn(true);
        when(purchase.getPurchaseState()).thenReturn(Purchase.PurchaseState.PURCHASED);
        when(purchase.getPurchaseToken()).thenReturn("token");
        when(purchase.getProducts()).thenReturn(List.of("premium"));
        when(purchase.isAcknowledged()).thenReturn(false);
        when(call.getBoolean("isConsumable", false)).thenReturn(false);
        when(call.getBoolean("autoAcknowledgePurchases", true)).thenReturn(true);
    }

    @Test
    public void autoAcknowledge_keepsClientOpenUntilPlayAnswers() {
        assertTrue(plugin.handlePurchase(purchase, call, client));

        ArgumentCaptor<AcknowledgePurchaseResponseListener> listener = ArgumentCaptor.forClass(AcknowledgePurchaseResponseListener.class);
        verify(client).acknowledgePurchase(any(AcknowledgePurchaseParams.class), listener.capture());
        verify(client, never()).endConnection();
        verify(call, never()).resolve(any(JSObject.class));

        listener.getValue().onAcknowledgePurchaseResponse(ok);

        verify(client).endConnection();
        verify(call).resolve(any(JSObject.class));
    }

    @Test
    public void autoConsume_keepsClientOpenUntilPlayAnswers() {
        when(call.getBoolean("isConsumable", false)).thenReturn(true);

        assertTrue(plugin.handlePurchase(purchase, call, client));

        ArgumentCaptor<ConsumeResponseListener> listener = ArgumentCaptor.forClass(ConsumeResponseListener.class);
        verify(client).consumeAsync(any(ConsumeParams.class), listener.capture());
        verify(client, never()).endConnection();
        verify(call, never()).resolve(any(JSObject.class));

        listener.getValue().onConsumeResponse(ok, "token");

        verify(client).endConnection();
        verify(call).resolve(any(JSObject.class));
    }

    @Test
    public void replacedClient_purchaseFinishesOnItsOwnClientAndLeavesTheNewOneAlone() {
        BillingClient newer = mock(BillingClient.class);
        plugin.billingClient = newer;

        assertTrue(plugin.handlePurchase(purchase, call, client));

        ArgumentCaptor<AcknowledgePurchaseResponseListener> listener = ArgumentCaptor.forClass(AcknowledgePurchaseResponseListener.class);
        verify(client).acknowledgePurchase(any(AcknowledgePurchaseParams.class), listener.capture());
        verify(newer, never()).acknowledgePurchase(any(), any());

        listener.getValue().onAcknowledgePurchaseResponse(ok);

        verify(client).endConnection();
        verify(newer, never()).endConnection();
        assertSame(newer, plugin.billingClient);
        verify(call).resolve(any(JSObject.class));
    }

    @Test
    public void laterCallDuringAcknowledge_detachesTheClientWithoutEndingIt() {
        assertTrue(plugin.handlePurchase(purchase, call, client));

        ArgumentCaptor<AcknowledgePurchaseResponseListener> listener = ArgumentCaptor.forClass(AcknowledgePurchaseResponseListener.class);
        verify(client).acknowledgePurchase(any(AcknowledgePurchaseParams.class), listener.capture());

        // Every new billing operation closes the current client before creating its own.
        plugin.closeBillingClient();

        assertNull(plugin.billingClient);
        verify(client, never()).endConnection();
        verify(call, never()).resolve(any(JSObject.class));

        listener.getValue().onAcknowledgePurchaseResponse(ok);

        verify(client).endConnection();
        verify(call).resolve(any(JSObject.class));
    }

    @Test
    public void clientAlreadyClosed_resolvesWithoutWaitingForAnAcknowledgeThatCannotRun() {
        when(client.isReady()).thenReturn(false);

        assertFalse(plugin.handlePurchase(purchase, call, client));

        verify(client, never()).acknowledgePurchase(any(), any());
        verify(call).resolve(any(JSObject.class));
    }

    @Test
    public void finishedClient_isEndedByTheNextCloseAgain() {
        assertTrue(plugin.handlePurchase(purchase, call, client));

        ArgumentCaptor<AcknowledgePurchaseResponseListener> listener = ArgumentCaptor.forClass(AcknowledgePurchaseResponseListener.class);
        verify(client).acknowledgePurchase(any(AcknowledgePurchaseParams.class), listener.capture());
        listener.getValue().onAcknowledgePurchaseResponse(ok);
        verify(client).endConnection();
        assertNull(plugin.billingClient);

        BillingClient next = mock(BillingClient.class);
        plugin.billingClient = next;
        plugin.closeBillingClient();

        verify(next).endConnection();
    }

    @Test
    public void manualAcknowledge_resolvesAndLeavesClosingToTheCaller() {
        when(call.getBoolean("autoAcknowledgePurchases", true)).thenReturn(false);

        assertFalse(plugin.handlePurchase(purchase, call, client));

        verify(client, never()).acknowledgePurchase(any(), any());
        verify(call).resolve(any(JSObject.class));
    }
}
