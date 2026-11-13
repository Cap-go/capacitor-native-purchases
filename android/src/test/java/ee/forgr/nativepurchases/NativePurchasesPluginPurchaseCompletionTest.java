package ee.forgr.nativepurchases;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.util.Log;
import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.AcknowledgePurchaseResponseListener;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ConsumeParams;
import com.android.billingclient.api.ConsumeResponseListener;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesResponseListener;
import com.android.billingclient.api.QueryPurchasesParams;
import com.getcapacitor.JSObject;
import com.getcapacitor.PluginCall;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

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
    // Debug unit tests log through android.util.Log, which is a stub on the JVM. Mock just that
    // instead of turning on returnDefaultValues for every unit test. Static mocks are per thread.
    private MockedStatic<Log> log;

    @Before
    public void setUp() {
        log = mockStatic(Log.class);
        plugin.billingClient = client;
        when(client.isReady()).thenReturn(true);
        when(purchase.getPurchaseState()).thenReturn(Purchase.PurchaseState.PURCHASED);
        when(purchase.getPurchaseToken()).thenReturn("token");
        when(purchase.getProducts()).thenReturn(List.of("premium"));
        when(purchase.isAcknowledged()).thenReturn(false);
        when(call.getBoolean("isConsumable", false)).thenReturn(false);
        when(call.getBoolean("autoAcknowledgePurchases", true)).thenReturn(true);
    }

    @After
    public void tearDown() {
        log.close();
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
            try (MockedStatic<Log> threadLog = mockStatic(Log.class)) {
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

    @Test
    public void restorePurchases_recoveryAcknowledgeKeepsTheClientUntilPlayAnswers() {
        plugin.processUnfinishedPurchases(client);

        ArgumentCaptor<PurchasesResponseListener> queries = ArgumentCaptor.forClass(PurchasesResponseListener.class);
        verify(client, times(2)).queryPurchasesAsync(any(QueryPurchasesParams.class), queries.capture());

        queries.getAllValues().get(0).onQueryPurchasesResponse(ok, List.of(purchase));
        ArgumentCaptor<AcknowledgePurchaseResponseListener> ack = ArgumentCaptor.forClass(AcknowledgePurchaseResponseListener.class);
        verify(client).acknowledgePurchase(any(AcknowledgePurchaseParams.class), ack.capture());

        // A later billing call closes the current client; the recovery acknowledge must survive it.
        plugin.closeBillingClient();
        assertNull(plugin.billingClient);
        verify(client, never()).endConnection();

        queries.getAllValues().get(1).onQueryPurchasesResponse(ok, List.of());
        verify(client, never()).endConnection();

        ack.getValue().onAcknowledgePurchaseResponse(ok);
        verify(client, times(1)).endConnection();
    }

    @Test
    public void restorePurchases_withAClosedClient_doesNotCrash() {
        assertFalse(plugin.processUnfinishedPurchases(null));

        when(client.isReady()).thenReturn(false);
        assertFalse(plugin.processUnfinishedPurchases(client));

        verify(client, never()).queryPurchasesAsync(any(QueryPurchasesParams.class), any());
    }

    @Test
    public void restorePurchases_fastFirstCallbackDoesNotStopTheSecondQuery() {
        // An ended client reports not ready, like the real one.
        AtomicBoolean ready = new AtomicBoolean(true);
        when(client.isReady()).thenAnswer((invocation) -> ready.get());
        org.mockito.Mockito.doAnswer((invocation) -> {
            ready.set(false);
            return null;
        })
            .when(client)
            .endConnection();
        // Play answers the in-app query synchronously, before the subscriptions query is sent.
        org.mockito.Mockito.doAnswer((invocation) -> {
            PurchasesResponseListener listener = invocation.getArgument(1);
            listener.onQueryPurchasesResponse(ok, List.of());
            return null;
        })
            .when(client)
            .queryPurchasesAsync(any(QueryPurchasesParams.class), any(PurchasesResponseListener.class));

        plugin.processUnfinishedPurchases(client);

        verify(client, times(2)).queryPurchasesAsync(any(QueryPurchasesParams.class), any(PurchasesResponseListener.class));
        verify(client, times(1)).endConnection();
    }

    @Test
    public void lostFinish_isGivenUpOnceSoLaterCallsDoNotWaitAgain() {
        plugin.pendingFinishWaitMs = 50;
        assertTrue(plugin.handlePurchase(purchase, call, client));

        ArgumentCaptor<AcknowledgePurchaseResponseListener> ack = ArgumentCaptor.forClass(AcknowledgePurchaseResponseListener.class);
        verify(client).acknowledgePurchase(any(AcknowledgePurchaseParams.class), ack.capture());

        // Play does not answer the acknowledge in time.
        plugin.awaitPendingFinishes();
        // A slow acknowledge is not a lost one: the client stays open so it can still complete.
        verify(client, never()).endConnection();
        assertNull(plugin.billingClient);
        // The waiting purchase call still settles, once.
        verify(call, times(1)).resolve(any(JSObject.class));

        plugin.pendingFinishWaitMs = 5000;
        long start = System.nanoTime();
        plugin.awaitPendingFinishes();
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        assertTrue("second call waited " + elapsedMs + " ms", elapsedMs < 1000);

        // The late answer ends the client and does not settle the call a second time.
        ack.getValue().onAcknowledgePurchaseResponse(ok);
        verify(client, times(1)).endConnection();
        verify(call, times(1)).resolve(any(JSObject.class));
    }

    @Test
    public void restorePurchases_withAClosedClient_rejectsInsteadOfResolving() {
        when(client.isReady()).thenReturn(false);

        plugin.restoreWithClient(call, client);
        plugin.restoreWithClient(call, null);

        verify(call, times(2)).reject("Billing client closed before restore could run. Please try again.");
        verify(call, never()).resolve();
        verify(client, never()).queryPurchasesAsync(any(QueryPurchasesParams.class), any());
    }

    @Test
    public void restorePurchases_withAReadyClient_sendsBothQueriesAndResolves() {
        plugin.restoreWithClient(call, client);

        verify(client, times(2)).queryPurchasesAsync(any(QueryPurchasesParams.class), any(PurchasesResponseListener.class));
        verify(call).resolve();
        verify(call, never()).reject(any(String.class));
    }

    @Test
    public void manualAcknowledge_survivesALaterCallAndSettlesOnce() {
        plugin.acknowledgeWithClient(call, client, "token");

        ArgumentCaptor<AcknowledgePurchaseResponseListener> ack = ArgumentCaptor.forClass(AcknowledgePurchaseResponseListener.class);
        verify(client).acknowledgePurchase(any(AcknowledgePurchaseParams.class), ack.capture());

        // A later billing call closes the current client; the manual acknowledge must survive it.
        plugin.closeBillingClient();
        verify(client, never()).endConnection();
        verify(call, never()).resolve();

        ack.getValue().onAcknowledgePurchaseResponse(ok);

        verify(client, times(1)).endConnection();
        verify(call, times(1)).resolve();
        verify(call, never()).reject(any(String.class));
    }

    @Test
    public void manualAcknowledge_givenUpRejectsOnceAndKeepsTheClientOpen() {
        plugin.pendingFinishWaitMs = 50;
        plugin.acknowledgeWithClient(call, client, "token");
        ArgumentCaptor<AcknowledgePurchaseResponseListener> ack = ArgumentCaptor.forClass(AcknowledgePurchaseResponseListener.class);
        verify(client).acknowledgePurchase(any(AcknowledgePurchaseParams.class), ack.capture());

        plugin.awaitPendingFinishes();

        verify(call, times(1)).reject(any(String.class), org.mockito.ArgumentMatchers.eq("FINISH_TIMEOUT"));
        verify(client, never()).endConnection();

        ack.getValue().onAcknowledgePurchaseResponse(ok);

        verify(client, times(1)).endConnection();
        verify(call, never()).resolve();
        // The late answer neither resolves nor rejects again.
        verify(call, times(1)).reject(any(String.class), org.mockito.ArgumentMatchers.eq("FINISH_TIMEOUT"));
        verify(call, never()).reject(any(String.class));
    }

    @Test
    public void manualAcknowledge_withAClosedClient_rejects() {
        when(client.isReady()).thenReturn(false);

        plugin.acknowledgeWithClient(call, client, "token");
        plugin.acknowledgeWithClient(call, null, "token");

        verify(call, times(2)).reject("Billing client closed before the acknowledge could run. Please try again.");
        verify(client, never()).acknowledgePurchase(any(), any());
    }

    @Test
    public void manualConsume_survivesALaterCallAndSettlesOnce() {
        plugin.consumeWithClient(call, client, "token");

        ArgumentCaptor<ConsumeResponseListener> consume = ArgumentCaptor.forClass(ConsumeResponseListener.class);
        verify(client).consumeAsync(any(ConsumeParams.class), consume.capture());

        plugin.closeBillingClient();
        verify(client, never()).endConnection();
        verify(call, never()).resolve();

        consume.getValue().onConsumeResponse(ok, "token");

        verify(client, times(1)).endConnection();
        verify(call, times(1)).resolve();
        verify(call, never()).reject(any(String.class));
    }

    @Test
    public void manualConsume_givenUpRejectsOnceAndKeepsTheClientOpen() {
        plugin.pendingFinishWaitMs = 50;
        plugin.consumeWithClient(call, client, "token");
        ArgumentCaptor<ConsumeResponseListener> consume = ArgumentCaptor.forClass(ConsumeResponseListener.class);
        verify(client).consumeAsync(any(ConsumeParams.class), consume.capture());

        plugin.awaitPendingFinishes();

        verify(call, times(1)).reject(any(String.class), org.mockito.ArgumentMatchers.eq("FINISH_TIMEOUT"));
        verify(client, never()).endConnection();

        consume.getValue().onConsumeResponse(ok, "token");

        verify(client, times(1)).endConnection();
        verify(call, never()).resolve();
        // The late answer neither resolves nor rejects again.
        verify(call, times(1)).reject(any(String.class), org.mockito.ArgumentMatchers.eq("FINISH_TIMEOUT"));
        verify(call, never()).reject(any(String.class));
    }

    @Test
    public void givenUpRestoreClient_endsOnlyWhenItsRealCountReachesZero() {
        plugin.pendingFinishWaitMs = 50;
        // 1. Both restore queries are pending.
        plugin.processUnfinishedPurchases(client);
        ArgumentCaptor<PurchasesResponseListener> queries = ArgumentCaptor.forClass(PurchasesResponseListener.class);
        verify(client, times(2)).queryPurchasesAsync(any(QueryPurchasesParams.class), queries.capture());

        // 2. A later call gives up waiting on them.
        plugin.awaitPendingFinishes();
        verify(client, never()).endConnection();
        assertNull(plugin.billingClient);

        // 3. The in-app query answers with an unacknowledged purchase and starts a recovery acknowledge.
        queries.getAllValues().get(0).onQueryPurchasesResponse(ok, List.of(purchase));
        ArgumentCaptor<AcknowledgePurchaseResponseListener> ack = ArgumentCaptor.forClass(AcknowledgePurchaseResponseListener.class);
        verify(client).acknowledgePurchase(any(AcknowledgePurchaseParams.class), ack.capture());
        verify(client, never()).endConnection();

        // 4. The acknowledge answers; the subscriptions query is still in flight.
        ack.getValue().onAcknowledgePurchaseResponse(ok);
        verify(client, never()).endConnection();

        queries.getAllValues().get(1).onQueryPurchasesResponse(ok, List.of());
        verify(client, times(1)).endConnection();

        // A set aside client never makes a later call wait.
        long start = System.nanoTime();
        plugin.pendingFinishWaitMs = 5000;
        plugin.awaitPendingFinishes();
        assertTrue((System.nanoTime() - start) / 1_000_000 < 1000);
    }

    @Test
    public void manualAcknowledge_thatThrows_undoesItsCountAndRejectsOnce() {
        org.mockito.Mockito.doThrow(new IllegalStateException("boom"))
            .when(client)
            .acknowledgePurchase(any(AcknowledgePurchaseParams.class), any(AcknowledgePurchaseResponseListener.class));

        plugin.acknowledgeWithClient(call, client, "token");

        verify(call, times(1)).reject("Failed to acknowledge purchase: boom");
        verify(call, never()).resolve();
        verify(client, times(1)).endConnection();
        assertNoPendingFinish();
    }

    @Test
    public void manualConsume_thatThrows_undoesItsCountAndRejectsOnce() {
        org.mockito.Mockito.doThrow(new IllegalStateException("boom"))
            .when(client)
            .consumeAsync(any(ConsumeParams.class), any(ConsumeResponseListener.class));

        plugin.consumeWithClient(call, client, "token");

        verify(call, times(1)).reject("Failed to consume purchase: boom");
        verify(client, times(1)).endConnection();
        assertNoPendingFinish();
    }

    @Test
    public void autoAcknowledge_thatThrows_resolvesOnceWithoutLeakingTheCount() {
        org.mockito.Mockito.doThrow(new IllegalStateException("boom"))
            .when(client)
            .acknowledgePurchase(any(AcknowledgePurchaseParams.class), any(AcknowledgePurchaseResponseListener.class));

        assertTrue(plugin.handlePurchase(purchase, call, client));

        verify(call, times(1)).resolve(any(JSObject.class));
        verify(client, times(1)).endConnection();
        assertNoPendingFinish();
    }

    @Test
    public void restoreQueries_thatThrow_undoTheirCounts() {
        org.mockito.Mockito.doThrow(new IllegalStateException("boom"))
            .when(client)
            .queryPurchasesAsync(any(QueryPurchasesParams.class), any(PurchasesResponseListener.class));

        plugin.processUnfinishedPurchases(client);

        verify(client, times(1)).endConnection();
        assertNoPendingFinish();
    }

    /** With nothing counted, a later call returns at once instead of waiting. */
    private void assertNoPendingFinish() {
        plugin.pendingFinishWaitMs = 5000;
        long start = System.nanoTime();
        plugin.awaitPendingFinishes();
        assertTrue((System.nanoTime() - start) / 1_000_000 < 1000);
    }
}
