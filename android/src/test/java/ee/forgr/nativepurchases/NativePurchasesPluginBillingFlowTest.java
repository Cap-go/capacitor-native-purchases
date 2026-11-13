package ee.forgr.nativepurchases;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.util.Log;
import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.BillingClient;
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
    private final NativePurchasesPlugin plugin = new NativePurchasesPlugin();
    private final BillingClient client = mock(BillingClient.class);
    private final BillingClient replacement = mock(BillingClient.class);
    private final PluginCall call = mock(PluginCall.class);
    private final Purchase purchase = mock(Purchase.class);

    private static final BillingResult ok = BillingResult.newBuilder().setResponseCode(BillingClient.BillingResponseCode.OK).build();

    @Before
    public void setUp() {
        log = mockStatic(Log.class);
        plugin.billingClient = client;
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

        assertNull(plugin.billingClient);
        verify(client, never()).endConnection();
    }

    @Test
    public void openBillingFlow_purchaseUpdateEndsClientAndResolvesCall() {
        assertTrue(plugin.beginPendingBillingFlow(client));

        plugin.handlePurchasesUpdated(ok, List.of(purchase), call, client);

        verify(client).endConnection();
        assertNull(plugin.billingClient);
        verify(call).resolve(any(JSObject.class));
    }

    @Test
    public void openBillingFlow_afterDetach_purchaseStillDeliveredToOwner() {
        assertTrue(plugin.beginPendingBillingFlow(client));

        // initBillingClient detached the flow client and attached a new one.
        plugin.billingClient = replacement;

        verify(client, never()).endConnection();
        assertSame(replacement, plugin.billingClient);

        plugin.handlePurchasesUpdated(ok, List.of(purchase), call, client);

        verify(client).endConnection();
        verify(replacement, never()).endConnection();
        verify(call).resolve(any(JSObject.class));
    }

    @Test
    public void detachedClient_unacknowledgedPurchase_usesOwnerForAutoAcknowledge() {
        when(purchase.isAcknowledged()).thenReturn(false);
        assertTrue(plugin.beginPendingBillingFlow(client));
        plugin.billingClient = replacement;

        plugin.handlePurchasesUpdated(ok, List.of(purchase), call, client);

        verify(client).acknowledgePurchase(any(AcknowledgePurchaseParams.class), any());
        verify(replacement, never()).acknowledgePurchase(any(), any());
        verify(call).resolve(any(JSObject.class));
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
