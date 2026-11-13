package ee.forgr.nativepurchases;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import java.util.concurrent.atomic.AtomicBoolean;
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

    @Test
    public void twoFinishesOnOneClient_endTheClientOnlyAfterTheLast() {
        Purchase second = mock(Purchase.class);
        when(second.getPurchaseState()).thenReturn(Purchase.PurchaseState.PURCHASED);
        when(second.getPurchaseToken()).thenReturn("token2");
        when(second.getProducts()).thenReturn(List.of("premium"));
        when(second.isAcknowledged()).thenReturn(false);

        assertTrue(plugin.handlePurchase(purchase, call, client));
        assertTrue(plugin.handlePurchase(second, call, client));

        ArgumentCaptor<AcknowledgePurchaseResponseListener> listener = ArgumentCaptor.forClass(AcknowledgePurchaseResponseListener.class);
        verify(client, times(2)).acknowledgePurchase(any(AcknowledgePurchaseParams.class), listener.capture());

        listener.getAllValues().get(0).onAcknowledgePurchaseResponse(ok);
        verify(client, never()).endConnection();

        listener.getAllValues().get(1).onAcknowledgePurchaseResponse(ok);
        verify(client, times(1)).endConnection();
    }

    @Test
    public void failedAcknowledge_stillResolvesAndEndsTheClient() {
        BillingResult error = BillingResult.newBuilder().setResponseCode(BillingClient.BillingResponseCode.ERROR).build();

        assertTrue(plugin.handlePurchase(purchase, call, client));

        ArgumentCaptor<AcknowledgePurchaseResponseListener> listener = ArgumentCaptor.forClass(AcknowledgePurchaseResponseListener.class);
        verify(client).acknowledgePurchase(any(AcknowledgePurchaseParams.class), listener.capture());

        listener.getValue().onAcknowledgePurchaseResponse(error);

        verify(client).endConnection();
        verify(call).resolve(any(JSObject.class));
    }

    @Test
    public void purchasesUpdated_pendingAcknowledge_keepsTheOwnerAsTheCurrentClient() {
        plugin.handlePurchasesUpdated(ok, List.of(purchase), call, client);

        ArgumentCaptor<AcknowledgePurchaseResponseListener> listener = ArgumentCaptor.forClass(AcknowledgePurchaseResponseListener.class);
        verify(client).acknowledgePurchase(any(AcknowledgePurchaseParams.class), listener.capture());
        assertSame(client, plugin.billingClient);
        verify(client, never()).endConnection();
        verify(call, never()).resolve(any(JSObject.class));

        listener.getValue().onAcknowledgePurchaseResponse(ok);

        verify(client).endConnection();
        assertNull(plugin.billingClient);
        verify(call).resolve(any(JSObject.class));
    }

    @Test
    public void purchasesUpdated_nothingPending_closesTheOwner() {
        when(call.getBoolean("autoAcknowledgePurchases", true)).thenReturn(false);

        plugin.handlePurchasesUpdated(ok, List.of(purchase), call, client);

        verify(client).endConnection();
        assertNull(plugin.billingClient);
    }

    @Test
    public void nextBillingCall_waitsForTheFinishBeforeReplacingTheClient() throws InterruptedException {
        assertTrue(plugin.handlePurchase(purchase, call, client));
        ArgumentCaptor<AcknowledgePurchaseResponseListener> listener = ArgumentCaptor.forClass(AcknowledgePurchaseResponseListener.class);
        verify(client).acknowledgePurchase(any(AcknowledgePurchaseParams.class), listener.capture());

        AtomicBoolean returned = new AtomicBoolean(false);
        Thread nextCall = new Thread(() -> {
            try {
                // Runs the real entry point. Without a bridge, creating the new client fails after
                // the wait, which is fine: the test only checks what happens before that.
                plugin.initBillingClient(null);
            } catch (RuntimeException expected) {
                // No Android context in a JVM test.
            }
            returned.set(true);
        });
        nextCall.start();

        // Deterministic: the call is parked in wait() on the pending finish, not just slow to start.
        long deadline = System.currentTimeMillis() + 5000;
        while (nextCall.getState() != Thread.State.TIMED_WAITING && System.currentTimeMillis() < deadline) {
            Thread.sleep(5);
        }
        assertSame(Thread.State.TIMED_WAITING, nextCall.getState());
        assertFalse(returned.get());
        assertSame(client, plugin.billingClient);
        verify(client, never()).endConnection();

        listener.getValue().onAcknowledgePurchaseResponse(ok);
        nextCall.join(30000);

        assertTrue(returned.get());
        verify(client, times(1)).endConnection();
        verify(call).resolve(any(JSObject.class));
    }
}
