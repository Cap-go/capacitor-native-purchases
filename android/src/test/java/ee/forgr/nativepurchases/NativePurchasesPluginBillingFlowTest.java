package ee.forgr.nativepurchases;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;
import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.AcknowledgePurchaseResponseListener;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.Purchase;
import com.getcapacitor.JSObject;
import com.getcapacitor.PluginCall;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

public class NativePurchasesPluginBillingFlowTest {

    private MockedStatic<Log> log;
    private NativePurchasesPlugin plugin = new NativePurchasesPlugin();
    private final BillingClient client = mock(BillingClient.class);
    private final BillingClient replacement = mock(BillingClient.class);
    private final PluginCall call = mock(PluginCall.class);
    private final Purchase purchase = mock(Purchase.class);

    private static final BillingResult ok = BillingResult.newBuilder().setResponseCode(BillingClient.BillingResponseCode.OK).build();

    @Before
    public void setUp() {
        log = mockStatic(Log.class);
        plugin.setBillingClientForTest(client);
        when(client.isReady()).thenReturn(true);
        when(purchase.getPurchaseState()).thenReturn(Purchase.PurchaseState.PURCHASED);
        when(purchase.getPurchaseToken()).thenReturn("token");
        when(purchase.getProducts()).thenReturn(List.of("premium"));
        when(purchase.isAcknowledged()).thenReturn(true);
        when(purchase.getPurchaseTime()).thenReturn(1_700_000_000_000L);
        when(purchase.getQuantity()).thenReturn(1);
        when(purchase.getOrderId()).thenReturn("order");
        when(call.getBoolean("isConsumable", false)).thenReturn(false);
        when(call.getBoolean("autoAcknowledgePurchases", true)).thenReturn(true);
        when(call.getString("productType")).thenReturn("inapp");
    }

    @After
    public void tearDown() {
        log.close();
    }

    @Test
    public void openBillingFlow_laterCallDetachesWithoutEndingClient() {
        assertTrue(plugin.beginPendingBillingFlow(client));

        plugin.closeBillingClient();

        assertNull(plugin.getBillingClientForTest());
        verify(client, never()).endConnection();
    }

    @Test
    public void openBillingFlow_purchaseUpdateEndsClientAndResolvesCall() {
        assertTrue(plugin.beginPendingBillingFlow(client));

        plugin.handlePurchasesUpdated(ok, List.of(purchase), call, client);

        verify(client).endConnection();
        assertNull(plugin.getBillingClientForTest());
        verify(call).resolve(any(JSObject.class));
    }

    @Test
    public void openBillingFlow_afterDetach_purchaseStillDeliveredToOwner() {
        assertTrue(plugin.beginPendingBillingFlow(client));

        // initBillingClient detached the flow client and attached a new one.
        plugin.setBillingClientForTest(replacement);

        verify(client, never()).endConnection();
        assertSame(replacement, plugin.getBillingClientForTest());

        plugin.handlePurchasesUpdated(ok, List.of(purchase), call, client);

        verify(client).endConnection();
        verify(replacement, never()).endConnection();
        verify(call).resolve(any(JSObject.class));
    }

    /**
     * Models purchaseProduct: beginPending before queryProductDetailsAsync, another call replaces
     * {@link NativePurchasesPlugin#getBillingClientForTest()}, then the details callback launches on the captured client.
     */
    @Test
    public void queryToLaunchInterval_replacedBillingClient_launchesOnCapturedClient() {
        NativePurchasesPlugin pluginSpy = spy(plugin);
        AppCompatActivity activity = mock(AppCompatActivity.class);
        doReturn(activity).when(pluginSpy).getActivity();
        when(client.launchBillingFlow(any(), any())).thenReturn(ok);

        assertTrue(pluginSpy.beginPendingBillingFlow(client));
        pluginSpy.setBillingClientForTest(replacement);

        BillingFlowParams params = mock(BillingFlowParams.class);
        pluginSpy.launchPurchaseBillingFlow(client, call, params);

        verify(client).launchBillingFlow(eq(activity), eq(params));
        verify(replacement, never()).launchBillingFlow(any(), any());
    }

    @Test
    public void launchPurchaseBillingFlow_rejectsWhenCapturedClientClosed() {
        when(client.isReady()).thenReturn(false);

        BillingFlowParams params = mock(BillingFlowParams.class);
        plugin.launchPurchaseBillingFlow(client, call, params);

        verify(client, never()).launchBillingFlow(any(), any());
        verify(call).reject(eq("Purchase is not purchased"), eq("SERVICE_DISCONNECTED"));
    }

    @Test
    public void detachedClient_unacknowledgedPurchase_usesOwnerForAutoAcknowledge() {
        when(purchase.isAcknowledged()).thenReturn(false);
        assertTrue(plugin.beginPendingBillingFlow(client));
        plugin.setBillingClientForTest(replacement);

        doAnswer((invocation) -> {
            AcknowledgePurchaseResponseListener listener = invocation.getArgument(1);
            listener.onAcknowledgePurchaseResponse(ok);
            return null;
        })
            .when(client)
            .acknowledgePurchase(any(AcknowledgePurchaseParams.class), any());

        plugin.handlePurchasesUpdated(ok, List.of(purchase), call, client);

        verify(client).acknowledgePurchase(any(AcknowledgePurchaseParams.class), any());
        verify(replacement, never()).acknowledgePurchase(any(), any());
        verify(call).resolve(any(JSObject.class));
        verify(client).endConnection();
        verify(replacement, never()).endConnection();
    }

    @Test
    public void unacknowledgedPurchase_defersClientEndUntilAcknowledgeCompletes() {
        when(purchase.isAcknowledged()).thenReturn(false);
        assertTrue(plugin.beginPendingBillingFlow(client));

        final AcknowledgePurchaseResponseListener[] listenerHolder = new AcknowledgePurchaseResponseListener[1];
        doAnswer((invocation) -> {
            listenerHolder[0] = invocation.getArgument(1);
            return null;
        })
            .when(client)
            .acknowledgePurchase(any(AcknowledgePurchaseParams.class), any());

        plugin.handlePurchasesUpdated(ok, List.of(purchase), call, client);

        verify(client, never()).endConnection();
        listenerHolder[0].onAcknowledgePurchaseResponse(ok);
        verify(client).endConnection();
    }

    @Test
    public void secondPurchaseUpdate_doesNotEndClientWhileAckInFlight() {
        when(purchase.isAcknowledged()).thenReturn(false);
        assertTrue(plugin.beginPendingBillingFlow(client));

        final AcknowledgePurchaseResponseListener[] listenerHolder = new AcknowledgePurchaseResponseListener[1];
        doAnswer((invocation) -> {
            listenerHolder[0] = invocation.getArgument(1);
            return null;
        })
            .when(client)
            .acknowledgePurchase(any(AcknowledgePurchaseParams.class), any());

        plugin.handlePurchasesUpdated(ok, List.of(purchase), call, client);

        BillingResult canceled = BillingResult.newBuilder().setResponseCode(BillingClient.BillingResponseCode.USER_CANCELED).build();
        plugin.handlePurchasesUpdated(canceled, null, call, client);

        verify(client, never()).endConnection();
        listenerHolder[0].onAcknowledgePurchaseResponse(ok);
        verify(client).endConnection();
    }

    @Test
    public void launchFailure_repeatedEndPendingBillingFlow_endsConnectionOnce() {
        assertTrue(plugin.beginPendingBillingFlow(client));
        plugin.endPendingBillingFlow(client);
        plugin.endPendingBillingFlow(client);
        verify(client, times(1)).endConnection();
    }

    @Test
    public void launchFailureAndListenerReject_purchaseCallOnce() {
        NativePurchasesPlugin pluginSpy = spy(plugin);
        AppCompatActivity activity = mock(AppCompatActivity.class);
        doReturn(activity).when(pluginSpy).getActivity();
        pluginSpy.registerPurchaseCallSettlementForTest(call);
        BillingResult launchError = BillingResult.newBuilder().setResponseCode(BillingClient.BillingResponseCode.DEVELOPER_ERROR).build();
        when(client.launchBillingFlow(any(), any())).thenReturn(launchError);
        assertTrue(pluginSpy.beginPendingBillingFlow(client));

        pluginSpy.launchPurchaseBillingFlow(client, call, mock(BillingFlowParams.class));
        pluginSpy.handlePurchasesUpdated(launchError, null, call, client);

        verify(call, times(1)).reject(any(String.class), any(String.class));
    }

    @Test
    public void userCanceledBillingFlow_endsClientWithoutResolve() {
        assertTrue(plugin.beginPendingBillingFlow(client));
        BillingResult canceled = BillingResult.newBuilder().setResponseCode(BillingClient.BillingResponseCode.USER_CANCELED).build();

        plugin.handlePurchasesUpdated(canceled, null, call, client);

        verify(client).endConnection();
        verify(call).reject(any(String.class), any(String.class));
    }
}
