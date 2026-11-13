package ee.forgr.nativepurchases;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.annotation.NonNull;
import com.android.billingclient.api.AccountIdentifiers;
import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.AcknowledgePurchaseResponseListener;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingConfig;
import com.android.billingclient.api.BillingConfigResponseListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ConsumeParams;
import com.android.billingclient.api.ConsumeResponseListener;
import com.android.billingclient.api.GetBillingConfigParams;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.ProductDetailsResponseListener;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesResponseListener;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryProductDetailsResult;
import com.android.billingclient.api.QueryPurchasesParams;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.json.JSONArray;

@CapacitorPlugin(name = "NativePurchases")
public class NativePurchasesPlugin extends Plugin {

    /** Outcome of choosing a subscription offer token before launching the billing flow. */
    enum SubscriptionOfferPurchaseOutcome {
        /** An offer was selected; {@link SubscriptionOfferPurchaseResult#offerToken} is set. */
        OK,
        /** The product details contained no subscription offers. */
        NO_OFFERS,
        /** An explicit offer token matched nothing in the query result. */
        OFFER_TOKEN_NOT_FOUND
    }

    /** Result of {@link #resolveSubscriptionOfferForPurchase}. */
    static final class SubscriptionOfferPurchaseResult {

        final SubscriptionOfferPurchaseOutcome outcome;
        final String offerToken;
        final String basePlanId;

        private SubscriptionOfferPurchaseResult(SubscriptionOfferPurchaseOutcome outcome, String offerToken, String basePlanId) {
            this.outcome = outcome;
            this.offerToken = offerToken;
            this.basePlanId = basePlanId;
        }

        static SubscriptionOfferPurchaseResult ok(String offerToken, String basePlanId) {
            return new SubscriptionOfferPurchaseResult(SubscriptionOfferPurchaseOutcome.OK, offerToken, basePlanId);
        }

        static SubscriptionOfferPurchaseResult noOffers() {
            return new SubscriptionOfferPurchaseResult(SubscriptionOfferPurchaseOutcome.NO_OFFERS, null, null);
        }

        static SubscriptionOfferPurchaseResult offerTokenNotFound() {
            return new SubscriptionOfferPurchaseResult(SubscriptionOfferPurchaseOutcome.OFFER_TOKEN_NOT_FOUND, null, null);
        }
    }

    /**
     * Selects the subscription offer token for {@link #purchaseProduct}, using the same rules
     * as {@link SubscriptionOfferSelector}.
     */
    static SubscriptionOfferPurchaseResult resolveSubscriptionOfferForPurchase(
        List<ProductDetails.SubscriptionOfferDetails> subscriptionOffers,
        String planIdentifier,
        String offerToken
    ) {
        if (subscriptionOffers == null || subscriptionOffers.isEmpty()) {
            return SubscriptionOfferPurchaseResult.noOffers();
        }
        int selectedIndex = SubscriptionOfferSelector.selectIndex(
            SubscriptionOfferSelector.adapt(subscriptionOffers),
            planIdentifier,
            offerToken
        );
        if (selectedIndex == SubscriptionOfferSelector.NOT_FOUND) {
            return SubscriptionOfferPurchaseResult.offerTokenNotFound();
        }
        ProductDetails.SubscriptionOfferDetails selected = subscriptionOffers.get(selectedIndex);
        return SubscriptionOfferPurchaseResult.ok(selected.getOfferToken(), selected.getBasePlanId());
    }

    private final String pluginVersion = "8.9.13";
    public static final String TAG = "NativePurchases";
    private static final int BILLING_CONNECTION_MAX_ATTEMPTS = 2;
    private static final long BILLING_SETUP_TIMEOUT_SECONDS = 5;
    private static final long[] BILLING_CONNECTION_BACKOFF_MS = { 0, 1000 };
    private final Object billingClientLock = new Object();
    private final ExecutorService billingExecutor = Executors.newSingleThreadExecutor((r) -> {
        Thread thread = new Thread(r, "NativePurchases-Billing");
        thread.setDaemon(true);
        return thread;
    });
    BillingClient billingClient;
    /**
     * Open Play billing sheets per client. Guarded by {@link #billingClientLock}. A client with an open
     * flow must not be ended until Play calls {@code onPurchasesUpdated}; later calls detach it instead.
     */
    private final Map<BillingClient, Integer> pendingBillingFlows = new IdentityHashMap<>();
    /**
     * Number of auto acknowledge or consume operations in flight per client. Guarded by
     * {@link #billingClientLock}. endConnection() drops queued work, so such a client is ended only
     * by its last completion. A later call first waits for these to finish (see
     * {@link #awaitPendingFinishes()}) so only one client is normally active.
     */
    private final Map<BillingClient, Integer> pendingFinishes = new IdentityHashMap<>();
    /**
     * Run when a client's pending finishes are given up after the wait, so a purchase call waiting on
     * one still settles. Guarded by {@link #billingClientLock}.
     */
    private final Map<BillingClient, List<Runnable>> abandonedFinishHandlers = new IdentityHashMap<>();
    /**
     * Clients a later call stopped waiting on. Their counts in {@link #pendingFinishes} stay real, so
     * they still end only when their last finish answers; the wait just ignores them. Guarded by
     * {@link #billingClientLock}.
     */
    private final Set<BillingClient> setAsideClients = Collections.newSetFromMap(new IdentityHashMap<>());
    /** How long a new billing call waits for an in flight acknowledge or consume before it proceeds. */
    private static final long PENDING_FINISH_WAIT_MS = 10_000;
    /** Package-private so tests can shorten the wait. */
    long pendingFinishWaitMs = PENDING_FINISH_WAIT_MS;
    /** Ensures purchaseProduct settles its promise once even if Play reports the failure twice. */
    private final Map<PluginCall, AtomicBoolean> purchaseCallSettlement = new WeakHashMap<>();
    private volatile boolean autoFinishTransactions = true;

    @PluginMethod
    public void configure(PluginCall call) {
        Boolean value = call.getBoolean("autoFinishTransactions");
        if (value != null) {
            autoFinishTransactions = value;
            NativePurchasesLog.d(TAG, "autoFinishTransactions set to " + value);
        }
        call.resolve();
    }

    /**
     * Acknowledges the purchase whose token is passed as {@code transactionId}, matching finishTransaction on iOS.
     */
    @PluginMethod
    public void finishTransaction(PluginCall call) {
        String transactionId = call.getString("transactionId");
        if (transactionId == null || transactionId.isEmpty()) {
            call.reject("transactionId is required");
            return;
        }
        // Android transaction IDs are purchase tokens, and Play Billing has no client-side unverified state.
        call.getData().put("purchaseToken", transactionId);
        acknowledgePurchase(call);
    }

    @PluginMethod
    public void getUnfinishedTransactions(PluginCall call) {
        NativePurchasesLog.d(TAG, "getUnfinishedTransactions() called");
        withBillingClient(call, () -> {
            JSONArray unacknowledged = new JSONArray();
            AtomicInteger pendingQueries = new AtomicInteger(2);
            AtomicBoolean finished = new AtomicBoolean(false);
            AtomicReference<String> queryFailure = new AtomicReference<>(null);

            Runnable maybeFinish = () -> {
                if (pendingQueries.decrementAndGet() <= 0 && finished.compareAndSet(false, true)) {
                    closeBillingClient();
                    String failure = queryFailure.get();
                    if (failure != null) {
                        NativePurchasesLog.w(TAG, "Rejecting getUnfinishedTransactions: " + failure);
                        call.reject("Failed to query purchases: " + failure, "QUERY_PURCHASES_FAILED");
                        return;
                    }
                    JSObject result = new JSObject();
                    result.put("transactions", unacknowledged);
                    call.resolve(result);
                }
            };

            QueryPurchasesParams inAppParams = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build();
            billingClient.queryPurchasesAsync(inAppParams, (billingResult, purchases) -> {
                try {
                    if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && purchases != null) {
                        appendUnacknowledgedPurchases(billingResult, purchases, "inapp", unacknowledged);
                    } else {
                        queryFailure.compareAndSet(null, describeQueryFailure("inapp", billingResult));
                    }
                } catch (Exception ex) {
                    queryFailure.compareAndSet(null, describeQueryFailure("inapp", billingResult) + " / " + ex.getMessage());
                } finally {
                    maybeFinish.run();
                }
            });

            QueryPurchasesParams subsParams = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build();
            billingClient.queryPurchasesAsync(subsParams, (billingResult, purchases) -> {
                try {
                    if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && purchases != null) {
                        appendUnacknowledgedPurchases(billingResult, purchases, "subs", unacknowledged);
                    } else {
                        queryFailure.compareAndSet(null, describeQueryFailure("subs", billingResult));
                    }
                } catch (Exception ex) {
                    queryFailure.compareAndSet(null, describeQueryFailure("subs", billingResult) + " / " + ex.getMessage());
                } finally {
                    maybeFinish.run();
                }
            });
        });
    }

    private void appendUnacknowledgedPurchases(
        BillingResult billingResult,
        List<Purchase> purchases,
        String productType,
        JSONArray target
    ) {
        if (billingResult.getResponseCode() != BillingClient.BillingResponseCode.OK || purchases == null) {
            return;
        }
        for (Purchase purchase : purchases) {
            if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED && !purchase.isAcknowledged()) {
                target.put(buildPurchaseTransactionObject(purchase, productType));
            }
        }
    }

    /**
     * Maps the {@code productType} passed to purchaseProduct to the value it was bought as.
     * Matches purchaseProduct, which defaults to inapp and treats anything else as a subscription.
     */
    static String responseProductType(String requestedProductType) {
        return requestedProductType == null || requestedProductType.equals("inapp") ? "inapp" : "subs";
    }

    private static String formatUtcIso8601(long timeMillis) {
        java.text.SimpleDateFormat purchaseDateFormat = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US);
        purchaseDateFormat.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        return purchaseDateFormat.format(new java.util.Date(timeMillis));
    }

    private JSObject buildPurchaseTransactionObject(Purchase purchase, String productType) {
        AccountIdentifiers accountIdentifiers = purchase.getAccountIdentifiers();
        String purchaseAccountId = accountIdentifiers != null ? accountIdentifiers.getObfuscatedAccountId() : null;
        JSObject purchaseData = new JSObject();
        purchaseData.put("transactionId", purchase.getPurchaseToken());
        purchaseData.put("productIdentifier", purchase.getProducts().isEmpty() ? null : purchase.getProducts().get(0));
        purchaseData.put("purchaseDate", formatUtcIso8601(purchase.getPurchaseTime()));
        purchaseData.put("quantity", purchase.getQuantity());
        purchaseData.put("productType", productType);
        purchaseData.put("orderId", purchase.getOrderId());
        purchaseData.put("purchaseToken", purchase.getPurchaseToken());
        purchaseData.put("isAcknowledged", purchase.isAcknowledged());
        purchaseData.put("purchaseState", String.valueOf(purchase.getPurchaseState()));
        purchaseData.put("appAccountToken", purchaseAccountId);
        purchaseData.put("willCancel", null);
        return purchaseData;
    }

    @PluginMethod
    public void isBillingSupported(PluginCall call) {
        NativePurchasesLog.d(TAG, "isBillingSupported() called");
        billingExecutor.execute(() -> {
            try {
                // Pass null so initBillingClient doesn't reject the call - we'll handle the result ourselves
                this.initBillingClient(null);
                JSObject ret = new JSObject();
                ret.put("isBillingSupported", true);
                NativePurchasesLog.d(TAG, "isBillingSupported() returning true - billing client initialized successfully");
                closeBillingClient();
                call.resolve(ret);
            } catch (RuntimeException e) {
                NativePurchasesLog.e(TAG, "isBillingSupported() - billing client initialization failed: " + e.getMessage());
                closeBillingClient();
                JSObject ret = new JSObject();
                ret.put("isBillingSupported", false);
                NativePurchasesLog.d(TAG, "isBillingSupported() returning false - billing not available");
                call.resolve(ret);
            } catch (Exception e) {
                NativePurchasesLog.e(TAG, "isBillingSupported() - unexpected error: " + e.getMessage());
                closeBillingClient();
                JSObject ret = new JSObject();
                ret.put("isBillingSupported", false);
                NativePurchasesLog.d(TAG, "isBillingSupported() returning false - unexpected error");
                call.resolve(ret);
            }
        });
    }

    @PluginMethod
    public void getStorefront(PluginCall call) {
        NativePurchasesLog.d(TAG, "getStorefront() called");
        billingExecutor.execute(() -> {
            try {
                // Pass null so initBillingClient doesn't reject the call - getStorefront
                // always resolves and reports an empty countryCode when the storefront
                // can't be determined, mirroring isBillingSupported() and the iOS side.
                this.initBillingClient(null);
                billingClient.getBillingConfigAsync(
                    GetBillingConfigParams.newBuilder().build(),
                    new BillingConfigResponseListener() {
                        @Override
                        public void onBillingConfigResponse(@NonNull BillingResult billingResult, BillingConfig billingConfig) {
                            JSObject ret = new JSObject();
                            if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && billingConfig != null) {
                                String countryCode = billingConfig.getCountryCode();
                                NativePurchasesLog.d(TAG, "getBillingConfig success, countryCode: " + countryCode);
                                // JSObject.put(name, null) removes the key, so coalesce to "".
                                ret.put("countryCode", countryCode != null ? countryCode : "");
                            } else {
                                NativePurchasesLog.e(
                                    TAG,
                                    "getBillingConfig unavailable: " +
                                        billingResult.getResponseCode() +
                                        " - " +
                                        billingResult.getDebugMessage()
                                );
                                ret.put("countryCode", "");
                            }
                            closeBillingClient();
                            call.resolve(ret);
                        }
                    }
                );
            } catch (RuntimeException e) {
                NativePurchasesLog.e(TAG, "getStorefront() - billing client init failed: " + e.getMessage());
                closeBillingClient();
                call.resolve(emptyStorefront());
            } catch (Exception e) {
                NativePurchasesLog.e(TAG, "getBillingConfigAsync threw: " + e.getMessage());
                closeBillingClient();
                call.resolve(emptyStorefront());
            }
        });
    }

    private JSObject emptyStorefront() {
        JSObject ret = new JSObject();
        ret.put("countryCode", "");
        return ret;
    }

    @Override
    public void load() {
        autoFinishTransactions = getConfig().getBoolean("autoFinishTransactions", true);
        super.load();
        NativePurchasesLog.configure(getConfig().getBoolean("debugLogging", false));
        NativePurchasesLog.d(TAG, "Plugin load() called, autoFinishTransactions=" + autoFinishTransactions);
        NativePurchasesLog.i(NativePurchasesPlugin.TAG, "load");
        NativePurchasesLog.d(TAG, "Plugin load() completed");
    }

    @FunctionalInterface
    private interface BillingTask {
        void run() throws Exception;
    }

    private void withBillingClient(PluginCall call, BillingTask task) {
        billingExecutor.execute(() -> {
            try {
                initBillingClient(call);
                task.run();
            } catch (RuntimeException e) {
                NativePurchasesLog.e(TAG, "Failed to initialize billing client: " + e.getMessage());
                closeBillingClient();
            } catch (Exception e) {
                NativePurchasesLog.e(TAG, "Billing task failed: " + e.getMessage());
                closeBillingClient();
                if (call != null) {
                    call.reject(e.getMessage());
                }
            }
        });
    }

    @Override
    protected void handleOnDestroy() {
        billingExecutor.shutdownNow();
        List<BillingClient> clientsToEnd = new ArrayList<>();
        synchronized (billingClientLock) {
            if (billingClient != null) {
                clientsToEnd.add(billingClient);
            }
            for (BillingClient client : pendingBillingFlows.keySet()) {
                if (!clientsToEnd.contains(client)) {
                    clientsToEnd.add(client);
                }
            }
            for (BillingClient client : pendingFinishes.keySet()) {
                if (!clientsToEnd.contains(client)) {
                    clientsToEnd.add(client);
                }
            }
            billingClient = null;
            pendingBillingFlows.clear();
            pendingFinishes.clear();
            abandonedFinishHandlers.clear();
            setAsideClients.clear();
            purchaseCallSettlement.clear();
        }
        for (BillingClient client : clientsToEnd) {
            client.endConnection();
        }
        super.handleOnDestroy();
    }

    BillingClient getBillingClientForTest() {
        return billingClient;
    }

    void setBillingClientForTest(BillingClient client) {
        billingClient = client;
    }

    /** Package-private so tests can stand in for a later call that replaces the client. */
    void closeBillingClient() {
        synchronized (billingClientLock) {
            closeBillingClientLocked();
        }
    }

    /** Closes {@code client} only if no later call has replaced it, so a late callback cannot end another call's connection. */
    private void closeBillingClientIfCurrent(BillingClient client) {
        synchronized (billingClientLock) {
            if (billingClient == client) {
                closeBillingClientLocked();
            }
        }
    }

    /**
     * Marks {@code client} as finishing a purchase so later calls replace it without ending it.
     *
     * @return false when the client is already closed, in which case nothing can run on it.
     */
    private boolean beginFinishingPurchase(BillingClient client) {
        return beginFinishingPurchase(client, null);
    }

    /** @param onAbandon run if this finish is given up after the wait; may be null. */
    private boolean beginFinishingPurchase(BillingClient client, Runnable onAbandon) {
        synchronized (billingClientLock) {
            if (client == null || !client.isReady()) {
                return false;
            }
            pendingFinishes.merge(client, 1, Integer::sum);
            if (onAbandon != null) {
                abandonedFinishHandlers.computeIfAbsent(client, (key) -> new ArrayList<>()).add(onAbandon);
            }
            return true;
        }
    }

    /**
     * Records one finished acknowledge or consume. The last one for {@code client} ends it, whether or
     * not a later call has replaced it, and wakes calls waiting in {@link #awaitPendingFinishes()}.
     */
    private void endFinishingPurchase(BillingClient client) {
        boolean last;
        synchronized (billingClientLock) {
            Integer pending = pendingFinishes.get(client);
            last = pending == null || pending <= 1;
            if (last) {
                pendingFinishes.remove(client);
                abandonedFinishHandlers.remove(client);
                setAsideClients.remove(client);
                if (billingClient == client) {
                    billingClient = null;
                }
            } else {
                pendingFinishes.put(client, pending - 1);
            }
            billingClientLock.notifyAll();
        }
        if (last && client != null && !hasOpenBillingFlow(client)) {
            client.endConnection();
        }
    }

    private boolean hasOpenBillingFlow(BillingClient client) {
        synchronized (billingClientLock) {
            return pendingBillingFlows.containsKey(client);
        }
    }

    /**
     * Waits until no acknowledge or consume is in flight, so a new client is not created next to one
     * that can still receive purchase updates. After {@link #PENDING_FINISH_WAIT_MS} it gives up and
     * the caller detaches the finishing client instead. Safe to call while holding
     * {@link #billingClientLock}, since wait() releases it. Package-private for tests.
     */
    void awaitPendingFinishes() {
        synchronized (billingClientLock) {
            awaitPendingFinishesLocked();
        }
    }

    private void awaitPendingFinishesLocked() {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(pendingFinishWaitMs);
        while (hasAwaitedFinishesLocked()) {
            long remainingMs = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
            if (remainingMs <= 0) {
                NativePurchasesLog.w(TAG, "Purchase finish still pending, no longer waiting on it");
                for (BillingClient stale : new ArrayList<>(pendingFinishes.keySet())) {
                    if (!setAsideClients.add(stale)) {
                        continue;
                    }
                    if (billingClient == stale) {
                        billingClient = null;
                    }
                    List<Runnable> handlers = abandonedFinishHandlers.remove(stale);
                    if (handlers != null) {
                        for (Runnable handler : handlers) {
                            handler.run();
                        }
                    }
                }
                return;
            }
            try {
                billingClientLock.wait(remainingMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /** True while some client that is not set aside has a finish in flight and no open purchase sheet. */
    private boolean hasAwaitedFinishesLocked() {
        for (BillingClient client : pendingFinishes.keySet()) {
            if (setAsideClients.contains(client) || pendingBillingFlows.containsKey(client)) {
                continue;
            }
            return true;
        }
        return false;
    }

    /** Stands in for Play's answer when a Play call throws, so the counted finish is still undone. */
    private static BillingResult thrownResult(RuntimeException e) {
        return BillingResult.newBuilder()
            .setResponseCode(BillingClient.BillingResponseCode.ERROR)
            .setDebugMessage(String.valueOf(e.getMessage()))
            .build();
    }

    /**
     * Records an open billing flow on {@code client}. The flow ends in {@link #endPendingBillingFlow}.
     *
     * @return false when the client is already closed.
     */
    boolean beginPendingBillingFlow(BillingClient client) {
        synchronized (billingClientLock) {
            if (client == null || !client.isReady()) {
                return false;
            }
            pendingBillingFlows.merge(client, 1, Integer::sum);
            return true;
        }
    }

    /**
     * Records that Play answered the billing flow for {@code client}. The last one ends the client.
     */
    void endPendingBillingFlow(BillingClient client) {
        if (client == null) {
            return;
        }
        boolean last;
        boolean finishInFlight;
        synchronized (billingClientLock) {
            finishInFlight = pendingFinishes.getOrDefault(client, 0) > 0;
            Integer pending = pendingBillingFlows.get(client);
            if (pending == null) {
                return;
            }
            last = pending <= 1;
            if (last) {
                pendingBillingFlows.remove(client);
                if (billingClient == client) {
                    billingClient = null;
                }
            } else {
                pendingBillingFlows.put(client, pending - 1);
            }
            billingClientLock.notifyAll();
        }
        if (last && !finishInFlight) {
            client.endConnection();
        }
    }

    private void registerPurchaseCallSettlement(PluginCall call) {
        purchaseCallSettlement.put(call, new AtomicBoolean(false));
    }

    void registerPurchaseCallSettlementForTest(PluginCall call) {
        registerPurchaseCallSettlement(call);
    }

    private boolean trySettlePurchaseCall(PluginCall call) {
        if (call == null) {
            return false;
        }
        AtomicBoolean settled = purchaseCallSettlement.get(call);
        if (settled != null) {
            return settled.compareAndSet(false, true);
        }
        return true;
    }

    private void rejectPurchaseCallOnce(PluginCall call, String message, String code) {
        if (trySettlePurchaseCall(call)) {
            call.reject(message, code);
        }
    }

    private void rejectPurchaseCallOnce(PluginCall call, String message) {
        if (trySettlePurchaseCall(call)) {
            call.reject(message);
        }
    }

    private void resolvePurchaseCallOnce(PluginCall call, JSObject ret) {
        if (trySettlePurchaseCall(call)) {
            call.resolve(ret);
        }
    }

    private void abortPurchaseClientFlow(BillingClient purchaseClient, PluginCall call, String message) {
        endPendingBillingFlow(purchaseClient);
        closeBillingClientIfCurrent(purchaseClient);
        rejectPurchaseCallOnce(call, message);
    }

    private void abortPurchaseClientFlow(BillingClient purchaseClient, PluginCall call, String message, String code) {
        endPendingBillingFlow(purchaseClient);
        closeBillingClientIfCurrent(purchaseClient);
        rejectPurchaseCallOnce(call, message, code);
    }

    /**
     * Launches the Play sheet on {@code flowClient}, which must be the client captured for this purchase call,
     * not the shared {@link #billingClient} field.
     */
    void launchPurchaseBillingFlow(BillingClient flowClient, PluginCall call, BillingFlowParams billingFlowParams) {
        if (flowClient == null || !flowClient.isReady()) {
            NativePurchasesLog.w(TAG, "Purchase billing client closed before launch");
            abortPurchaseClientFlow(
                flowClient,
                call,
                "Purchase is not purchased",
                billingResponseCodeName(BillingClient.BillingResponseCode.SERVICE_DISCONNECTED)
            );
            return;
        }
        BillingResult billingResult2;
        try {
            billingResult2 = flowClient.launchBillingFlow(getActivity(), billingFlowParams);
        } catch (RuntimeException launchError) {
            endPendingBillingFlow(flowClient);
            throw launchError;
        }
        NativePurchasesLog.d(
            TAG,
            "Billing flow launch result: " + billingResult2.getResponseCode() + " - " + billingResult2.getDebugMessage()
        );
        NativePurchasesLog.i(NativePurchasesPlugin.TAG, "onProductDetailsResponse2" + billingResult2);
        if (billingResult2.getResponseCode() != BillingClient.BillingResponseCode.OK) {
            endPendingBillingFlow(flowClient);
            rejectPurchaseCallOnce(call, "Purchase is not purchased", billingResponseCodeName(billingResult2.getResponseCode()));
            closeBillingClientIfCurrent(flowClient);
        }
    }

    /**
     * Reject a purchase whose {@code offerToken} matched no current offer.
     *
     * Shared by the subscription and one-time paths because `definitions.ts`
     * documents that rejection as one rule covering both product types, so
     * the two must not drift apart in wording.
     */
    private void rejectOfferTokenNotFound(PluginCall call, String productIdentifier) {
        NativePurchasesLog.d(TAG, "Offer token not found for product: " + productIdentifier);
        closeBillingClient();
        call.reject("Offer token not found for product: " + productIdentifier);
    }

    private void rejectOfferTokenNotFoundForPurchase(PluginCall call, String productIdentifier, BillingClient purchaseClient) {
        NativePurchasesLog.d(TAG, "Offer token not found for product: " + productIdentifier);
        abortPurchaseClientFlow(purchaseClient, call, "Offer token not found for product: " + productIdentifier);
    }

    private void closeBillingClientLocked() {
        NativePurchasesLog.d(TAG, "closeBillingClient() called");
        if (billingClient != null && (pendingBillingFlows.containsKey(billingClient) || pendingFinishes.containsKey(billingClient))) {
            NativePurchasesLog.d(TAG, "Detaching billing client that has an open purchase flow or pending finish");
            billingClient = null;
        } else if (billingClient != null) {
            NativePurchasesLog.d(TAG, "Ending billing client connection");
            billingClient.endConnection();
            billingClient = null;
            NativePurchasesLog.d(TAG, "Billing client closed and set to null");
        } else {
            NativePurchasesLog.d(TAG, "Billing client was already null");
        }
    }

    /**
     * Maps a {@link BillingClient.BillingResponseCode} to a readable name so
     * consumers can tell why a purchase was refused - most importantly
     * USER_CANCELED (not an error) and ITEM_ALREADY_OWNED (the user already
     * paid and the app should restore instead of showing a failure).
     */
    private static String billingResponseCodeName(int code) {
        switch (code) {
            case BillingClient.BillingResponseCode.USER_CANCELED:
                return "USER_CANCELED";
            case BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED:
                return "ITEM_ALREADY_OWNED";
            case BillingClient.BillingResponseCode.ITEM_NOT_OWNED:
                return "ITEM_NOT_OWNED";
            case BillingClient.BillingResponseCode.ITEM_UNAVAILABLE:
                return "ITEM_UNAVAILABLE";
            case BillingClient.BillingResponseCode.BILLING_UNAVAILABLE:
                return "BILLING_UNAVAILABLE";
            case BillingClient.BillingResponseCode.SERVICE_DISCONNECTED:
                return "SERVICE_DISCONNECTED";
            case BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE:
                return "SERVICE_UNAVAILABLE";
            case BillingClient.BillingResponseCode.DEVELOPER_ERROR:
                return "DEVELOPER_ERROR";
            case BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED:
                return "FEATURE_NOT_SUPPORTED";
            case BillingClient.BillingResponseCode.NETWORK_ERROR:
                return "NETWORK_ERROR";
            case BillingClient.BillingResponseCode.ERROR:
                return "ERROR";
            default:
                return "UNKNOWN_" + code;
        }
    }

    private static String describeQueryFailure(String productType, BillingResult billingResult) {
        return (
            productType +
            ": " +
            billingResponseCodeName(billingResult.getResponseCode()) +
            " (" +
            billingResult.getResponseCode() +
            ") " +
            billingResult.getDebugMessage()
        );
    }

    private void rejectBillingSetupCall(PluginCall purchaseCall, AtomicBoolean callRejected, String code, String message) {
        if (purchaseCall != null && callRejected.compareAndSet(false, true)) {
            purchaseCall.reject(code, message);
        }
    }

    private String getBillingSetupErrorMessage(BillingResult billingResult) {
        switch (billingResult.getResponseCode()) {
            case BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE:
                return "Billing service unavailable. Please check your internet connection and Google Play Services.";
            case BillingClient.BillingResponseCode.BILLING_UNAVAILABLE:
                return "Billing is not available on this device.";
            case BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED:
                return "This billing feature is not supported.";
            case BillingClient.BillingResponseCode.SERVICE_DISCONNECTED:
                return "Billing service disconnected. Please try again.";
            default:
                return "Billing setup failed: " + billingResult.getDebugMessage();
        }
    }

    /**
     * @return true when an acknowledge or consume is in flight. Its callback then closes the billing
     *     client and resolves the call, because endConnection() cancels work still queued on the client.
     * @param purchaseClient the client whose listener delivered the purchase; another call may have
     *     replaced {@code billingClient} by the time this runs.
     */
    boolean handlePurchase(Purchase purchase, PluginCall purchaseCall, BillingClient purchaseClient) {
        NativePurchasesLog.d(TAG, "handlePurchase() called");
        NativePurchasesLog.d(TAG, NativePurchasesLog.purchaseDebugSummary(purchase));
        NativePurchasesLog.d(TAG, "Purchase state: " + purchase.getPurchaseState());
        NativePurchasesLog.d(TAG, "Is acknowledged: " + purchase.isAcknowledged());

        if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
            NativePurchasesLog.d(TAG, "Purchase state is PURCHASED");
            boolean isConsumable = purchaseCall != null && purchaseCall.getBoolean("isConsumable", false);
            boolean autoAcknowledge = purchaseCall != null ? purchaseCall.getBoolean("autoAcknowledgePurchases", true) : true;
            boolean shouldAutoAcknowledge = autoFinishTransactions && autoAcknowledge;
            NativePurchasesLog.d(TAG, "Auto-acknowledge enabled: " + shouldAutoAcknowledge);

            PurchaseAction action = PurchaseActionDecider.decide(isConsumable, purchase);

            AccountIdentifiers accountIdentifiers = purchase.getAccountIdentifiers();
            String purchaseAccountId = accountIdentifiers != null ? accountIdentifiers.getObfuscatedAccountId() : null;
            NativePurchasesLog.d(TAG, "Purchase account identifier present: " + (purchaseAccountId != null ? "[REDACTED]" : "none"));

            JSObject ret = new JSObject();
            ret.put("transactionId", purchase.getPurchaseToken());
            ret.put("productIdentifier", purchase.getProducts().get(0));
            ret.put("purchaseDate", formatUtcIso8601(purchase.getPurchaseTime()));
            ret.put("quantity", purchase.getQuantity());
            ret.put("productType", responseProductType(purchaseCall != null ? purchaseCall.getString("productType") : null));
            ret.put("orderId", purchase.getOrderId());
            ret.put("purchaseToken", purchase.getPurchaseToken());
            ret.put("isAcknowledged", purchase.isAcknowledged());
            ret.put("purchaseState", String.valueOf(purchase.getPurchaseState()));
            ret.put("appAccountToken", purchaseAccountId);

            // Add cancellation information - ALWAYS set willCancel
            // Note: Android doesn't provide direct cancellation information in the Purchase object
            // This would require additional Google Play API calls to get detailed subscription status
            ret.put("willCancel", null); // Default to null, would need API call to determine actual cancellation date

            NativePurchasesLog.d(TAG, "Resolving purchase call for product: " + purchase.getProducts().get(0));
            AtomicBoolean settled = new AtomicBoolean(false);
            Runnable onAbandon = () -> {
                if (settled.compareAndSet(false, true) && purchaseCall != null) {
                    NativePurchasesLog.w(TAG, "Purchase finish was never answered; resolving with isAcknowledged false");
                    if (purchaseCallSettlement.containsKey(purchaseCall)) {
                        resolvePurchaseCallOnce(purchaseCall, ret);
                    } else {
                        purchaseCall.resolve(ret);
                    }
                }
            };
            boolean canFinish = shouldAutoAcknowledge && action != PurchaseAction.NONE;
            if (canFinish && !beginFinishingPurchase(purchaseClient, onAbandon)) {
                NativePurchasesLog.w(TAG, "Billing client already closed, skipping auto acknowledge or consume");
                shouldAutoAcknowledge = false;
            }
            Consumer<BillingResult> completePurchase = (finishResult) -> {
                if (finishResult != null && finishResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    ret.put("isAcknowledged", true);
                } else {
                    NativePurchasesLog.w(TAG, "Automatic purchase finish failed; isAcknowledged stays false");
                }
                endFinishingPurchase(purchaseClient);
                endPendingBillingFlow(purchaseClient);
                if (settled.compareAndSet(false, true) && purchaseCall != null) {
                    if (purchaseCallSettlement.containsKey(purchaseCall)) {
                        resolvePurchaseCallOnce(purchaseCall, ret);
                    } else {
                        purchaseCall.resolve(ret);
                    }
                }
            };

            switch (action) {
                case CONSUME:
                    if (!shouldAutoAcknowledge) {
                        NativePurchasesLog.d(TAG, "Consumable purchase deferred until manual consumePurchase()");
                        break;
                    }
                    NativePurchasesLog.d(TAG, "Purchase flagged as consumable, consuming...");
                    ConsumeParams consumeParams = ConsumeParams.newBuilder().setPurchaseToken(purchase.getPurchaseToken()).build();
                    AtomicBoolean consumeAnswered = new AtomicBoolean(false);
                    try {
                        purchaseClient.consumeAsync(consumeParams, (billingResult, purchaseToken) -> {
                            if (consumeAnswered.compareAndSet(false, true)) {
                                onConsumeResponse(billingResult, purchaseToken);
                                completePurchase.accept(billingResult);
                            }
                        });
                    } catch (RuntimeException e) {
                        if (consumeAnswered.compareAndSet(false, true)) {
                            completePurchase.accept(thrownResult(e));
                        }
                    }
                    return true;
                case ACKNOWLEDGE:
                    if (shouldAutoAcknowledge) {
                        NativePurchasesLog.d(TAG, "Purchase not acknowledged, auto-acknowledging...");
                        acknowledgePurchase(purchaseClient, purchase.getPurchaseToken(), completePurchase);
                        return true;
                    } else {
                        NativePurchasesLog.d(
                            TAG,
                            "Purchase not acknowledged, but auto-acknowledge is disabled. Developer must manually acknowledge."
                        );
                    }
                    break;
                case NONE:
                default:
                    NativePurchasesLog.d(TAG, "No additional purchase handling required");
                    break;
            }

            if (purchaseCall != null) {
                if (purchaseCallSettlement.containsKey(purchaseCall)) {
                    resolvePurchaseCallOnce(purchaseCall, ret);
                } else {
                    purchaseCall.resolve(ret);
                }
            } else {
                NativePurchasesLog.d(TAG, "purchaseCall is null, cannot resolve");
            }
        } else if (purchase.getPurchaseState() == Purchase.PurchaseState.PENDING) {
            NativePurchasesLog.d(TAG, "Purchase state is PENDING");
            if (purchaseCall != null) {
                rejectPurchaseCallOnce(purchaseCall, "Purchase is pending");
            } else {
                NativePurchasesLog.d(TAG, "purchaseCall is null for pending purchase");
            }
        } else {
            NativePurchasesLog.d(TAG, "Purchase state is OTHER: " + purchase.getPurchaseState());
            if (purchaseCall != null) {
                rejectPurchaseCallOnce(purchaseCall, "Purchase is not purchased", "PURCHASE_STATE_" + purchase.getPurchaseState());
            } else {
                NativePurchasesLog.d(TAG, "purchaseCall is null for failed purchase");
            }
        }
        return false;
    }

    private void acknowledgePurchase(BillingClient client, String purchaseToken, Consumer<BillingResult> onComplete) {
        NativePurchasesLog.d(TAG, "acknowledgePurchase() called");
        if (client == null) {
            NativePurchasesLog.w(TAG, "Billing client unavailable, skipping acknowledge");
            if (onComplete != null) {
                onComplete.accept(
                    BillingResult.newBuilder()
                        .setResponseCode(BillingClient.BillingResponseCode.SERVICE_DISCONNECTED)
                        .setDebugMessage("Billing client unavailable")
                        .build()
                );
            }
            return;
        }
        AcknowledgePurchaseParams acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchaseToken)
            .build();
        AtomicBoolean answered = new AtomicBoolean(false);
        AcknowledgePurchaseResponseListener listener = (billingResult) -> {
            if (!answered.compareAndSet(false, true)) {
                return;
            }
            NativePurchasesLog.d(TAG, "onAcknowledgePurchaseResponse() called");
            NativePurchasesLog.d(TAG, "Acknowledge result: " + billingResult.getResponseCode() + " - " + billingResult.getDebugMessage());
            NativePurchasesLog.i(NativePurchasesPlugin.TAG, "onAcknowledgePurchaseResponse" + billingResult);
            if (onComplete != null) {
                onComplete.accept(billingResult);
            }
        };
        try {
            client.acknowledgePurchase(acknowledgePurchaseParams, listener);
        } catch (RuntimeException e) {
            listener.onAcknowledgePurchaseResponse(thrownResult(e));
        }
    }

    void initBillingClient(PluginCall purchaseCall) {
        NativePurchasesLog.d(TAG, "initBillingClient() called");
        NativePurchasesLog.d(TAG, "purchaseCall is null: " + (purchaseCall == null));

        synchronized (billingClientLock) {
            awaitPendingFinishesLocked();
            closeBillingClientLocked();
        }

        final AtomicBoolean setupCallRejected = new AtomicBoolean(false);
        RuntimeException lastFailure = null;

        for (int attempt = 1; attempt <= BILLING_CONNECTION_MAX_ATTEMPTS; attempt++) {
            if (attempt > 1) {
                long backoffMs = BILLING_CONNECTION_BACKOFF_MS[Math.min(attempt - 1, BILLING_CONNECTION_BACKOFF_MS.length - 1)];
                NativePurchasesLog.d(
                    TAG,
                    "Retrying billing client connection (attempt " +
                        attempt +
                        "/" +
                        BILLING_CONNECTION_MAX_ATTEMPTS +
                        ") after " +
                        backoffMs +
                        "ms"
                );
                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    rejectBillingSetupCall(purchaseCall, setupCallRejected, "BILLING_INTERRUPTED", "Billing setup was interrupted");
                    throw new RuntimeException("Billing setup was interrupted", e);
                }
                synchronized (billingClientLock) {
                    closeBillingClientLocked();
                }
            }

            try {
                connectBillingClient(purchaseCall, setupCallRejected, attempt);
                return;
            } catch (RuntimeException e) {
                lastFailure = e;
                boolean willRetry = attempt < BILLING_CONNECTION_MAX_ATTEMPTS && isRetriableBillingSetupFailure(e);
                NativePurchasesLog.w(
                    TAG,
                    "Billing client connection attempt " + attempt + " failed: " + e.getMessage() + (willRetry ? " (will retry)" : "")
                );
                if (!willRetry) {
                    break;
                }
            }
        }

        String failureMessage = lastFailure != null && lastFailure.getMessage() != null ? lastFailure.getMessage() : "Billing setup failed";
        String failureCode = failureMessage.contains("timed out") ? "BILLING_SETUP_TIMEOUT" : "BILLING_SETUP_FAILED";
        rejectBillingSetupCall(purchaseCall, setupCallRejected, failureCode, failureMessage);
        throw lastFailure != null ? lastFailure : new RuntimeException("Billing setup failed");
    }

    /**
     * Body of each client's PurchasesUpdatedListener. {@code owner} is the client the listener belongs
     * to; it stays open while a billing flow is open and ends when Play answers here.
     */
    void handlePurchasesUpdated(BillingResult billingResult, List<Purchase> purchases, PluginCall purchaseCall, BillingClient owner) {
        NativePurchasesLog.d(TAG, "onPurchasesUpdated() called");
        NativePurchasesLog.d(TAG, "Billing result: " + billingResult.getResponseCode() + " - " + billingResult.getDebugMessage());
        NativePurchasesLog.d(TAG, "Purchases count: " + (purchases != null ? purchases.size() : 0));
        NativePurchasesLog.i(NativePurchasesPlugin.TAG, "onPurchasesUpdated" + billingResult);
        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && purchases != null) {
            NativePurchasesLog.d(TAG, "Purchase update successful, processing first purchase");
            if (handlePurchase(purchases.get(0), purchaseCall, owner)) {
                endPendingBillingFlow(owner);
                return;
            }
        } else {
            NativePurchasesLog.d(TAG, "Purchase update failed or purchases is null");
            NativePurchasesLog.i(NativePurchasesPlugin.TAG, "onPurchasesUpdated" + billingResult);
            if (purchaseCall != null) {
                if (purchaseCallSettlement.containsKey(purchaseCall)) {
                    rejectPurchaseCallOnce(
                        purchaseCall,
                        "Purchase is not purchased",
                        billingResponseCodeName(billingResult.getResponseCode())
                    );
                } else {
                    purchaseCall.reject("Purchase is not purchased", billingResponseCodeName(billingResult.getResponseCode()));
                }
            }
        }
        endPendingBillingFlow(owner);
        closeBillingClientIfCurrent(owner);
    }

    private boolean isRetriableBillingSetupFailure(RuntimeException failure) {
        String message = failure.getMessage();
        if (message == null) {
            return true;
        }
        if (message.contains("timed out") || message.contains("disconnected") || message.contains("SERVICE_UNAVAILABLE")) {
            return true;
        }
        return !message.contains("BILLING_UNAVAILABLE") && !message.contains("FEATURE_NOT_SUPPORTED");
    }

    private void connectBillingClient(PluginCall purchaseCall, AtomicBoolean setupCallRejected, int attempt) {
        final CountDownLatch setupLatch = new CountDownLatch(1);
        final BillingResult[] setupError = new BillingResult[1];
        final AtomicBoolean setupSettled = new AtomicBoolean(false);

        NativePurchasesLog.d(TAG, "Creating new BillingClient (attempt " + attempt + "/" + BILLING_CONNECTION_MAX_ATTEMPTS + ")");
        final BillingClient[] listenerOwner = new BillingClient[1];
        final BillingClient client;
        synchronized (billingClientLock) {
            client = BillingClient.newBuilder(getContext())
                .setListener(
                    new PurchasesUpdatedListener() {
                        @Override
                        public void onPurchasesUpdated(@NonNull BillingResult billingResult, List<Purchase> purchases) {
                            handlePurchasesUpdated(billingResult, purchases, purchaseCall, listenerOwner[0]);
                        }
                    }
                )
                .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
                .build();
            listenerOwner[0] = client;
            billingClient = client;
        }

        NativePurchasesLog.d(TAG, "Starting billing client connection");
        client.startConnection(
            new BillingClientStateListener() {
                @Override
                public void onBillingSetupFinished(@NonNull BillingResult billingResult) {
                    if (!setupSettled.compareAndSet(false, true)) {
                        NativePurchasesLog.d(TAG, "Ignoring stale onBillingSetupFinished callback");
                        return;
                    }
                    NativePurchasesLog.d(TAG, "onBillingSetupFinished() called");
                    NativePurchasesLog.d(TAG, "Setup result: " + billingResult.getResponseCode() + " - " + billingResult.getDebugMessage());
                    if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                        NativePurchasesLog.d(TAG, "Billing setup successful, client is ready");
                    } else {
                        NativePurchasesLog.e(TAG, "Billing setup failed with code: " + billingResult.getResponseCode());
                        NativePurchasesLog.e(TAG, "Error message: " + billingResult.getDebugMessage());
                        setupError[0] = billingResult;
                    }
                    setupLatch.countDown();
                }

                @Override
                public void onBillingServiceDisconnected() {
                    NativePurchasesLog.d(TAG, "onBillingServiceDisconnected() called");
                    if (!setupSettled.compareAndSet(false, true)) {
                        NativePurchasesLog.d(TAG, "Billing service disconnected after setup settled");
                        return;
                    }
                    synchronized (billingClientLock) {
                        if (billingClient == client) {
                            billingClient = null;
                        }
                    }
                    setupError[0] = BillingResult.newBuilder()
                        .setResponseCode(BillingClient.BillingResponseCode.SERVICE_DISCONNECTED)
                        .setDebugMessage("Billing service disconnected during setup")
                        .build();
                    setupLatch.countDown();
                }
            }
        );

        try {
            NativePurchasesLog.d(TAG, "Waiting for billing client setup to finish");
            boolean setupCompleted = setupLatch.await(BILLING_SETUP_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (!setupCompleted) {
                setupSettled.compareAndSet(false, true);
                NativePurchasesLog.e(TAG, "Billing client setup timed out after " + BILLING_SETUP_TIMEOUT_SECONDS + " seconds");
                synchronized (billingClientLock) {
                    closeBillingClientLocked();
                }
                throw new RuntimeException("Billing setup timed out");
            }

            NativePurchasesLog.d(TAG, "Billing client setup wait completed");

            if (setupError[0] != null) {
                NativePurchasesLog.e(TAG, "Billing setup failed, throwing exception");
                synchronized (billingClientLock) {
                    closeBillingClientLocked();
                }
                throw new RuntimeException(getBillingSetupErrorMessage(setupError[0]));
            }

            synchronized (billingClientLock) {
                if (billingClient != client || !client.isReady()) {
                    closeBillingClientLocked();
                    throw new RuntimeException("Billing service disconnected. Please try again.");
                }
            }

            NativePurchasesLog.d(TAG, "Billing client setup completed successfully");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            setupSettled.compareAndSet(false, true);
            NativePurchasesLog.e(TAG, "InterruptedException while waiting for billing setup: " + e.getMessage());
            rejectBillingSetupCall(purchaseCall, setupCallRejected, "BILLING_INTERRUPTED", "Billing setup was interrupted");
            synchronized (billingClientLock) {
                closeBillingClientLocked();
            }
            throw new RuntimeException("Billing setup was interrupted", e);
        }
    }

    @PluginMethod
    public void getPluginVersion(final PluginCall call) {
        NativePurchasesLog.d(TAG, "getPluginVersion() called");
        try {
            final JSObject ret = new JSObject();
            ret.put("version", this.pluginVersion);
            NativePurchasesLog.d(TAG, "Returning plugin version: " + this.pluginVersion);
            call.resolve(ret);
        } catch (final Exception e) {
            NativePurchasesLog.d(TAG, "Error getting plugin version: " + e.getMessage());
            call.reject("Could not get plugin version", e);
        }
    }

    @PluginMethod
    public void purchaseProduct(PluginCall call) {
        NativePurchasesLog.d(TAG, "purchaseProduct() called");
        String productIdentifier = call.getString("productIdentifier");
        String planIdentifier = call.getString("planIdentifier");
        String offerToken = call.getString("offerToken");
        String productType = call.getString("productType", "inapp");
        Number quantity = call.getInt("quantity", 1);
        String appAccountToken = call.getString("appAccountToken");
        final String accountIdentifier = appAccountToken != null && !appAccountToken.isEmpty() ? appAccountToken : null;
        boolean isConsumable = call.getBoolean("isConsumable", false);
        boolean autoAcknowledgePurchases = call.getBoolean("autoAcknowledgePurchases", true);

        NativePurchasesLog.d(TAG, "Product identifier: " + productIdentifier);
        NativePurchasesLog.d(TAG, "Plan identifier: " + planIdentifier);
        NativePurchasesLog.d(TAG, "Offer token provided: " + (offerToken != null && !offerToken.isEmpty()));
        NativePurchasesLog.d(TAG, "Product type: " + productType);
        NativePurchasesLog.d(TAG, "Quantity: " + quantity);
        NativePurchasesLog.d(TAG, "Account identifier provided: " + (accountIdentifier != null ? "[REDACTED]" : "none"));
        NativePurchasesLog.d(TAG, "Is consumable: " + isConsumable);
        NativePurchasesLog.d(TAG, "Auto-acknowledge purchases: " + autoAcknowledgePurchases);

        // cannot use quantity, because it's done in native modal
        NativePurchasesLog.d("CapacitorPurchases", "purchaseProduct: " + productIdentifier);
        if (productIdentifier == null || productIdentifier.isEmpty()) {
            // Handle error: productIdentifier is empty
            NativePurchasesLog.d(TAG, "Error: productIdentifier is empty");
            call.reject("productIdentifier is empty");
            return;
        }
        if (productType == null || productType.isEmpty()) {
            // Handle error: productType is empty
            NativePurchasesLog.d(TAG, "Error: productType is empty");
            call.reject("productType is empty");
            return;
        }
        boolean hasOfferToken = offerToken != null && !offerToken.isEmpty();
        if (productType.equals("subs") && !SubscriptionOfferSelector.canSelect(planIdentifier, offerToken)) {
            // The caller named no offer at all. SubscriptionOfferSelector owns
            // this rule: see canSelect for why either value alone is enough,
            // and for what it deliberately does not catch.
            NativePurchasesLog.d(TAG, "Error: planIdentifier cannot be empty if productType is subs");
            call.reject("planIdentifier cannot be empty if productType is subs unless offerToken is provided");
            return;
        }
        assert quantity != null;
        if (quantity.intValue() < 1) {
            // Handle error: quantity is less than 1
            NativePurchasesLog.d(TAG, "Error: quantity is less than 1");
            call.reject("quantity is less than 1");
            return;
        }
        if (isConsumable && productType.equals("subs")) {
            NativePurchasesLog.d(TAG, "isConsumable is not supported for subscriptions, ignoring flag");
            isConsumable = false;
        }

        call.getData().put("isConsumable", isConsumable);
        call.getData().put("autoAcknowledgePurchases", autoAcknowledgePurchases);
        registerPurchaseCallSettlement(call);

        // For subscriptions, always use the productIdentifier (subscription ID) to query
        // The planIdentifier is used later when setting the offer token
        NativePurchasesLog.d(TAG, "Using product ID for query: " + productIdentifier);

        List<QueryProductDetailsParams.Product> productList = Collections.singletonList(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productIdentifier)
                .setProductType(productType.equals("inapp") ? BillingClient.ProductType.INAPP : BillingClient.ProductType.SUBS)
                .build()
        );
        QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder().setProductList(productList).build();
        NativePurchasesLog.d(TAG, "Initializing billing client for purchase");
        withBillingClient(call, () -> {
            final BillingClient purchaseClient = billingClient;
            if (!beginPendingBillingFlow(purchaseClient)) {
                abortPurchaseClientFlow(
                    purchaseClient,
                    call,
                    "Purchase is not purchased",
                    billingResponseCodeName(BillingClient.BillingResponseCode.SERVICE_DISCONNECTED)
                );
                return;
            }
            NativePurchasesLog.d(TAG, "Querying product details for purchase");
            purchaseClient.queryProductDetailsAsync(
                params,
                new ProductDetailsResponseListener() {
                    @Override
                    public void onProductDetailsResponse(
                        @NonNull BillingResult billingResult,
                        @NonNull QueryProductDetailsResult queryProductDetailsResult
                    ) {
                        List<ProductDetails> productDetailsList = queryProductDetailsResult.getProductDetailsList();
                        NativePurchasesLog.d(TAG, "onProductDetailsResponse() called for purchase");
                        NativePurchasesLog.d(
                            TAG,
                            "Query result: " + billingResult.getResponseCode() + " - " + billingResult.getDebugMessage()
                        );
                        NativePurchasesLog.d(TAG, "Product details count: " + productDetailsList.size());

                        if (billingResult.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                            abortPurchaseClientFlow(
                                purchaseClient,
                                call,
                                "Purchase is not purchased",
                                billingResponseCodeName(billingResult.getResponseCode())
                            );
                            return;
                        }

                        if (productDetailsList.isEmpty()) {
                            NativePurchasesLog.d(TAG, "No products found");
                            abortPurchaseClientFlow(purchaseClient, call, "Product not found");
                            return;
                        }
                        // Process the result
                        List<BillingFlowParams.ProductDetailsParams> productDetailsParamsList = new ArrayList<>();
                        for (ProductDetails productDetailsItem : productDetailsList) {
                            NativePurchasesLog.d(TAG, "Processing product: " + productDetailsItem.getProductId());
                            BillingFlowParams.ProductDetailsParams.Builder productDetailsParams =
                                BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(productDetailsItem);
                            if (productType.equals("subs")) {
                                NativePurchasesLog.d(TAG, "Processing subscription product");
                                // Prefer an explicit offerToken, then the planIdentifier's base
                                // plan, then the first offer. See SubscriptionOfferSelector.
                                List<ProductDetails.SubscriptionOfferDetails> subscriptionOffers =
                                    productDetailsItem.getSubscriptionOfferDetails();
                                NativePurchasesLog.d(
                                    TAG,
                                    "Available offer details count: " + (subscriptionOffers == null ? 0 : subscriptionOffers.size())
                                );

                                SubscriptionOfferPurchaseResult subscriptionSelection = resolveSubscriptionOfferForPurchase(
                                    subscriptionOffers,
                                    planIdentifier,
                                    offerToken
                                );
                                if (subscriptionSelection.outcome == SubscriptionOfferPurchaseOutcome.NO_OFFERS) {
                                    NativePurchasesLog.w(TAG, "No subscription offer details found for product: " + productIdentifier);
                                    abortPurchaseClientFlow(
                                        purchaseClient,
                                        call,
                                        "No subscription offer details found for product: " + productIdentifier
                                    );
                                    return;
                                }
                                if (subscriptionSelection.outcome == SubscriptionOfferPurchaseOutcome.OFFER_TOKEN_NOT_FOUND) {
                                    rejectOfferTokenNotFoundForPurchase(call, productIdentifier, purchaseClient);
                                    return;
                                }

                                NativePurchasesLog.d(TAG, "Selected base plan: " + subscriptionSelection.basePlanId);
                                productDetailsParams.setOfferToken(subscriptionSelection.offerToken);
                                NativePurchasesLog.d(
                                    TAG,
                                    "Set offer token: " + NativePurchasesLog.redactSensitive(subscriptionSelection.offerToken)
                                );
                            } else if (productType.equals("inapp")) {
                                NativePurchasesLog.d(TAG, "Processing in-app product");
                                List<ProductDetails.OneTimePurchaseOfferDetails> oneTimeOffers =
                                    ProductPayloadMapper.resolveOneTimePurchaseOffers(productDetailsItem);
                                if (hasOfferToken) {
                                    ProductDetails.OneTimePurchaseOfferDetails selectedOffer = null;
                                    for (ProductDetails.OneTimePurchaseOfferDetails offerDetails : oneTimeOffers) {
                                        if (offerToken.equals(offerDetails.getOfferToken())) {
                                            selectedOffer = offerDetails;
                                            break;
                                        }
                                    }
                                    if (selectedOffer == null) {
                                        rejectOfferTokenNotFoundForPurchase(call, productIdentifier, purchaseClient);
                                        return;
                                    }
                                    productDetailsParams.setOfferToken(selectedOffer.getOfferToken());
                                    NativePurchasesLog.d(
                                        TAG,
                                        "Set one-time offer token: " + NativePurchasesLog.redactSensitive(selectedOffer.getOfferToken())
                                    );
                                } else if (productDetailsItem.getOneTimePurchaseOfferDetails() == null && !oneTimeOffers.isEmpty()) {
                                    productDetailsParams.setOfferToken(oneTimeOffers.get(0).getOfferToken());
                                    NativePurchasesLog.d(
                                        TAG,
                                        "Set default one-time offer token: " +
                                            NativePurchasesLog.redactSensitive(oneTimeOffers.get(0).getOfferToken())
                                    );
                                }
                            }
                            productDetailsParamsList.add(productDetailsParams.build());
                        }
                        BillingFlowParams.Builder billingFlowBuilder = BillingFlowParams.newBuilder().setProductDetailsParamsList(
                            productDetailsParamsList
                        );
                        if (accountIdentifier != null && !accountIdentifier.isEmpty()) {
                            billingFlowBuilder.setObfuscatedAccountId(accountIdentifier);
                        }
                        BillingFlowParams billingFlowParams = billingFlowBuilder.build();
                        NativePurchasesLog.d(TAG, "Launching billing flow");
                        launchPurchaseBillingFlow(purchaseClient, call, billingFlowParams);
                    }
                }
            );
        });
    }

    /** The current client, read under the lock. May be null if another call already closed it. */
    private BillingClient currentBillingClient() {
        synchronized (billingClientLock) {
            return billingClient;
        }
    }

    /**
     * Queries unfinished purchases on {@code client} and acknowledges them. Each query and each
     * acknowledge counts as a pending finish on that client, so a later call waits for them or
     * detaches the client instead of ending it, and the last one ends it. Package-private for tests.
     *
     * @return false when the client is already closed and no query was sent.
     */
    boolean processUnfinishedPurchases(BillingClient client) {
        NativePurchasesLog.d(TAG, "processUnfinishedPurchases() called");
        String[] productTypes = { BillingClient.ProductType.INAPP, BillingClient.ProductType.SUBS };
        for (int registered = 0; registered < productTypes.length; registered++) {
            if (!beginFinishingPurchase(client)) {
                NativePurchasesLog.w(TAG, "Billing client already closed, skipping unfinished purchase recovery");
                for (int undo = 0; undo < registered; undo++) {
                    endFinishingPurchase(client);
                }
                return false;
            }
        }
        for (String productType : productTypes) {
            QueryPurchasesParams params = QueryPurchasesParams.newBuilder().setProductType(productType).build();
            NativePurchasesLog.d(TAG, "Querying unfinished purchases of type " + productType);
            AtomicBoolean answered = new AtomicBoolean(false);
            PurchasesResponseListener listener = (billingResult, purchases) -> {
                if (!answered.compareAndSet(false, true)) {
                    return;
                }
                try {
                    handlePurchases(billingResult, purchases, client);
                } finally {
                    endFinishingPurchase(client);
                }
            };
            try {
                client.queryPurchasesAsync(params, listener);
            } catch (RuntimeException e) {
                listener.onQueryPurchasesResponse(thrownResult(e), null);
            }
        }
        return true;
    }

    private void handlePurchases(BillingResult billingResult, List<Purchase> purchases, BillingClient client) {
        NativePurchasesLog.d(TAG, "handlePurchases() called");
        NativePurchasesLog.d(TAG, "Query purchases result: " + billingResult.getResponseCode() + " - " + billingResult.getDebugMessage());
        NativePurchasesLog.d(TAG, "Purchases count: " + (purchases != null ? purchases.size() : 0));

        if (billingResult.getResponseCode() != BillingClient.BillingResponseCode.OK || purchases == null) {
            NativePurchasesLog.d(TAG, "Query purchases failed");
            return;
        }
        if (!autoFinishTransactions) {
            NativePurchasesLog.d(TAG, "autoFinishTransactions disabled, skipping recovery acknowledgment");
            return;
        }
        for (Purchase purchase : purchases) {
            NativePurchasesLog.d(TAG, "Processing purchase for products: " + purchase.getProducts());
            NativePurchasesLog.d(TAG, "Purchase state: " + purchase.getPurchaseState());
            if (purchase.getPurchaseState() != Purchase.PurchaseState.PURCHASED) {
                continue;
            }
            PurchaseAction action = PurchaseActionDecider.decide(false, purchase);
            if (action != PurchaseAction.ACKNOWLEDGE) {
                NativePurchasesLog.d(TAG, "Purchase already acknowledged, skipping consume");
                continue;
            }
            if (!beginFinishingPurchase(client)) {
                NativePurchasesLog.w(TAG, "Billing client already closed, skipping recovery acknowledgment");
                continue;
            }
            NativePurchasesLog.d(TAG, "Purchase not acknowledged, acknowledging");
            acknowledgePurchase(client, purchase.getPurchaseToken(), (result) -> endFinishingPurchase(client));
        }
    }

    private void onConsumeResponse(BillingResult billingResult, String purchaseToken) {
        NativePurchasesLog.d(TAG, "onConsumeResponse() called");
        NativePurchasesLog.d(TAG, "Consume result: " + billingResult.getResponseCode() + " - " + billingResult.getDebugMessage());
        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
            // Handle the success of the consume operation.
            // For example, you can update the UI to reflect that the item has been consumed.
            NativePurchasesLog.d(TAG, "Consume operation successful");
            NativePurchasesLog.i(NativePurchasesPlugin.TAG, "onConsumeResponse OK " + billingResult.getResponseCode());
        } else {
            // Handle error responses.
            NativePurchasesLog.d(TAG, "Consume operation failed");
            NativePurchasesLog.i(NativePurchasesPlugin.TAG, "onConsumeResponse OTHER " + billingResult.getResponseCode());
        }
    }

    @PluginMethod
    public void restorePurchases(PluginCall call) {
        NativePurchasesLog.d(TAG, "restorePurchases() called");
        NativePurchasesLog.d(NativePurchasesPlugin.TAG, "restorePurchases");
        withBillingClient(call, () -> restoreWithClient(call, currentBillingClient()));
    }

    /** Body of restorePurchases once a client exists. Package-private for tests. */
    void restoreWithClient(PluginCall call, BillingClient client) {
        if (!processUnfinishedPurchases(client)) {
            call.reject("Billing client closed before restore could run. Please try again.");
            return;
        }
        call.resolve();
        NativePurchasesLog.d(TAG, "restorePurchases() completed");
    }

    private void querySingleProductDetails(String productIdentifier, String productType, PluginCall call) {
        NativePurchasesLog.d(TAG, "querySingleProductDetails() called");
        NativePurchasesLog.d(TAG, "Product identifier: " + productIdentifier);
        NativePurchasesLog.d(TAG, "Product type: " + productType);

        String productTypeForQuery = productType.equals("inapp") ? BillingClient.ProductType.INAPP : BillingClient.ProductType.SUBS;
        NativePurchasesLog.d(TAG, "Creating query product: ID='" + productIdentifier + "', Type='" + productTypeForQuery + "'");

        List<QueryProductDetailsParams.Product> productList = new ArrayList<>();
        productList.add(
            QueryProductDetailsParams.Product.newBuilder().setProductId(productIdentifier).setProductType(productTypeForQuery).build()
        );

        QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder().setProductList(productList).build();
        NativePurchasesLog.d(TAG, "Initializing billing client for single product query");
        withBillingClient(call, () -> {
            NativePurchasesLog.d(TAG, "Querying product details");
            billingClient.queryProductDetailsAsync(
                params,
                new ProductDetailsResponseListener() {
                    @Override
                    public void onProductDetailsResponse(
                        @NonNull BillingResult billingResult,
                        @NonNull QueryProductDetailsResult queryProductDetailsResult
                    ) {
                        List<ProductDetails> productDetailsList = queryProductDetailsResult.getProductDetailsList();
                        NativePurchasesLog.d(TAG, "onProductDetailsResponse() called for single product query");
                        NativePurchasesLog.d(
                            TAG,
                            "Query result: " + billingResult.getResponseCode() + " - " + billingResult.getDebugMessage()
                        );
                        NativePurchasesLog.d(TAG, "Product details count: " + productDetailsList.size());

                        if (productDetailsList.isEmpty()) {
                            NativePurchasesLog.d(TAG, "No product found in query");
                            NativePurchasesLog.d(TAG, "This usually means:");
                            NativePurchasesLog.d(TAG, "1. Product doesn't exist in Google Play Console");
                            NativePurchasesLog.d(TAG, "2. Product is not published/active");
                            NativePurchasesLog.d(TAG, "3. App is not properly configured for the product type");
                            NativePurchasesLog.d(TAG, "4. Wrong product ID or type");
                            closeBillingClient();
                            call.reject("Product not found");
                            return;
                        }

                        ProductDetails productDetails = productDetailsList.get(0);
                        NativePurchasesLog.d(TAG, "Processing product details: " + productDetails.getProductId());
                        JSObject product = new JSObject();
                        product.put("title", productDetails.getName());
                        product.put("description", productDetails.getDescription());
                        NativePurchasesLog.d(TAG, "Product title: " + productDetails.getName());
                        NativePurchasesLog.d(TAG, "Product description: " + productDetails.getDescription());

                        if (productType.equals("inapp")) {
                            NativePurchasesLog.d(TAG, "Processing as in-app product");
                            List<ProductDetails.OneTimePurchaseOfferDetails> oneTimeOffers =
                                ProductPayloadMapper.resolveOneTimePurchaseOffers(productDetails);
                            if (oneTimeOffers.isEmpty()) {
                                NativePurchasesLog.w(
                                    TAG,
                                    "No one-time purchase offer details found for product: " + productDetails.getProductId()
                                );
                                closeBillingClient();
                                call.reject("No one-time purchase offer details found for product: " + productDetails.getProductId());
                                return;
                            }

                            ProductDetails.OneTimePurchaseOfferDetails selectedOffer = oneTimeOffers.get(0);
                            product.put("identifier", productDetails.getProductId());
                            ProductPayloadMapper.applyOneTimePurchaseOfferPricing(product, selectedOffer);
                            NativePurchasesLog.d(TAG, "Price: " + product.optDouble("price", 0.0));
                            NativePurchasesLog.d(TAG, "Formatted price: " + product.getString("priceString"));
                            NativePurchasesLog.d(TAG, "Currency: " + product.getString("currencyCode"));
                            NativePurchasesLog.d(TAG, "One-time offers available: " + oneTimeOffers.size());
                        } else {
                            NativePurchasesLog.d(TAG, "Processing as subscription product");
                            List<ProductDetails.SubscriptionOfferDetails> offerDetailsList = productDetails.getSubscriptionOfferDetails();
                            if (offerDetailsList == null || offerDetailsList.isEmpty()) {
                                NativePurchasesLog.w(
                                    TAG,
                                    "No subscription offer details found for product: " + productDetails.getProductId()
                                );
                                closeBillingClient();
                                call.reject("No subscription offers found for product: " + productDetails.getProductId());
                                return;
                            }

                            ProductDetails.SubscriptionOfferDetails selectedOfferDetails = null;
                            for (ProductDetails.SubscriptionOfferDetails offerDetails : offerDetailsList) {
                                if (
                                    offerDetails.getPricingPhases() != null &&
                                    !offerDetails.getPricingPhases().getPricingPhaseList().isEmpty()
                                ) {
                                    selectedOfferDetails = offerDetails;
                                    break;
                                }
                            }

                            if (selectedOfferDetails == null) {
                                NativePurchasesLog.w(
                                    TAG,
                                    "No offers with pricing phases found for product: " + productDetails.getProductId()
                                );
                                closeBillingClient();
                                call.reject("No pricing phases found for product: " + productDetails.getProductId());
                                return;
                            }

                            product.put("planIdentifier", productDetails.getProductId());
                            product.put("identifier", selectedOfferDetails.getBasePlanId());
                            product.put("offerToken", selectedOfferDetails.getOfferToken());
                            product.put("offerId", selectedOfferDetails.getOfferId());
                            ProductPayloadMapper.applySubscriptionPricing(product, selectedOfferDetails, offerDetailsList);
                            double price = product.optDouble("price", 0.0);
                            NativePurchasesLog.d(TAG, "Plan identifier: " + productDetails.getProductId());
                            NativePurchasesLog.d(TAG, "Base plan ID: " + selectedOfferDetails.getBasePlanId());
                            NativePurchasesLog.d(
                                TAG,
                                "Offer token: " + NativePurchasesLog.redactSensitive(selectedOfferDetails.getOfferToken())
                            );
                            NativePurchasesLog.d(TAG, "Price: " + price);
                            NativePurchasesLog.d(TAG, "Formatted price: " + product.getString("priceString"));
                            NativePurchasesLog.d(TAG, "Currency: " + product.getString("currencyCode"));
                        }
                        product.put("isFamilyShareable", false);

                        JSObject ret = new JSObject();
                        ret.put("product", product);
                        NativePurchasesLog.d(TAG, "Returning single product");
                        closeBillingClient();
                        call.resolve(ret);
                    }
                }
            );
        });
    }

    private void queryProductDetails(List<String> productIdentifiers, String productType, PluginCall call) {
        NativePurchasesLog.d(TAG, "queryProductDetails() called");
        NativePurchasesLog.d(TAG, "Product identifiers count: " + productIdentifiers.size());
        NativePurchasesLog.d(TAG, "Product type: " + productType);
        for (String id : productIdentifiers) {
            NativePurchasesLog.d(TAG, "Product ID: " + id);
        }

        List<QueryProductDetailsParams.Product> productList = new ArrayList<>();
        for (String productIdentifier : productIdentifiers) {
            String productTypeForQuery = productType.equals("inapp") ? BillingClient.ProductType.INAPP : BillingClient.ProductType.SUBS;
            NativePurchasesLog.d(TAG, "Creating query product: ID='" + productIdentifier + "', Type='" + productTypeForQuery + "'");
            productList.add(
                QueryProductDetailsParams.Product.newBuilder().setProductId(productIdentifier).setProductType(productTypeForQuery).build()
            );
        }
        NativePurchasesLog.d(TAG, "Total products in query list: " + productList.size());
        QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder().setProductList(productList).build();
        NativePurchasesLog.d(TAG, "Initializing billing client for product query");
        withBillingClient(call, () -> {
            NativePurchasesLog.d(TAG, "Querying product details");
            billingClient.queryProductDetailsAsync(
                params,
                new ProductDetailsResponseListener() {
                    @Override
                    public void onProductDetailsResponse(
                        @NonNull BillingResult billingResult,
                        @NonNull QueryProductDetailsResult queryProductDetailsResult
                    ) {
                        List<ProductDetails> productDetailsList = queryProductDetailsResult.getProductDetailsList();
                        NativePurchasesLog.d(TAG, "onProductDetailsResponse() called for query");
                        NativePurchasesLog.d(
                            TAG,
                            "Query result: " + billingResult.getResponseCode() + " - " + billingResult.getDebugMessage()
                        );
                        NativePurchasesLog.d(TAG, "Product details count: " + productDetailsList.size());

                        if (productDetailsList.isEmpty()) {
                            NativePurchasesLog.d(TAG, "No products found in query");
                            NativePurchasesLog.d(TAG, "This usually means:");
                            NativePurchasesLog.d(TAG, "1. Product doesn't exist in Google Play Console");
                            NativePurchasesLog.d(TAG, "2. Product is not published/active");
                            NativePurchasesLog.d(TAG, "3. App is not properly configured for the product type");
                            NativePurchasesLog.d(TAG, "4. Wrong product ID or type");
                            closeBillingClient();
                            call.reject("Product not found");
                            return;
                        }
                        JSONArray products = new JSONArray();
                        for (ProductDetails productDetails : productDetailsList) {
                            NativePurchasesLog.d(TAG, "Processing product details: " + productDetails.getProductId());
                            NativePurchasesLog.d(TAG, "Product title: " + productDetails.getName());
                            NativePurchasesLog.d(TAG, "Product description: " + productDetails.getDescription());

                            if (productType.equals("inapp")) {
                                NativePurchasesLog.d(TAG, "Processing as in-app product");
                                List<ProductDetails.OneTimePurchaseOfferDetails> oneTimeOffers =
                                    ProductPayloadMapper.resolveOneTimePurchaseOffers(productDetails);
                                if (oneTimeOffers.isEmpty()) {
                                    NativePurchasesLog.w(
                                        TAG,
                                        "No one-time purchase offer details found for product: " + productDetails.getProductId()
                                    );
                                    continue;
                                }

                                int addedOffers = 0;
                                for (ProductDetails.OneTimePurchaseOfferDetails offerDetails : oneTimeOffers) {
                                    JSObject product = new JSObject();
                                    product.put("title", productDetails.getName());
                                    product.put("description", productDetails.getDescription());
                                    product.put("identifier", productDetails.getProductId());
                                    product.put("isFamilyShareable", false);
                                    ProductPayloadMapper.applyOneTimePurchaseOfferPricing(product, offerDetails);

                                    NativePurchasesLog.d(TAG, "Price: " + product.optDouble("price", 0.0));
                                    NativePurchasesLog.d(TAG, "Formatted price: " + product.getString("priceString"));
                                    NativePurchasesLog.d(TAG, "Currency: " + product.getString("currencyCode"));
                                    NativePurchasesLog.d(
                                        TAG,
                                        "Offer token: " + NativePurchasesLog.redactSensitive(product.getString("offerToken"))
                                    );

                                    products.put(product);
                                    addedOffers++;
                                }

                                if (addedOffers == 0) {
                                    NativePurchasesLog.w(
                                        TAG,
                                        "No one-time purchase offers mapped for product: " + productDetails.getProductId()
                                    );
                                }
                            } else {
                                NativePurchasesLog.d(TAG, "Processing as subscription product");
                                List<ProductDetails.SubscriptionOfferDetails> offerDetailsList =
                                    productDetails.getSubscriptionOfferDetails();
                                if (offerDetailsList == null || offerDetailsList.isEmpty()) {
                                    NativePurchasesLog.w(
                                        TAG,
                                        "No subscription offer details found for product: " + productDetails.getProductId()
                                    );
                                    continue;
                                }

                                int addedOffers = 0;
                                for (ProductDetails.SubscriptionOfferDetails offerDetails : offerDetailsList) {
                                    if (
                                        offerDetails.getPricingPhases() == null ||
                                        offerDetails.getPricingPhases().getPricingPhaseList().isEmpty()
                                    ) {
                                        NativePurchasesLog.w(TAG, "No pricing phases found for offer: " + offerDetails.getBasePlanId());
                                        continue;
                                    }

                                    JSObject product = new JSObject();
                                    product.put("title", productDetails.getName());
                                    product.put("description", productDetails.getDescription());
                                    product.put("planIdentifier", productDetails.getProductId());
                                    product.put("identifier", offerDetails.getBasePlanId());
                                    product.put("offerToken", offerDetails.getOfferToken());
                                    product.put("offerId", offerDetails.getOfferId());

                                    product.put("isFamilyShareable", false);
                                    ProductPayloadMapper.applySubscriptionPricing(product, offerDetails, offerDetailsList);
                                    double price = product.optDouble("price", 0.0);

                                    NativePurchasesLog.d(TAG, "Plan identifier: " + productDetails.getProductId());
                                    NativePurchasesLog.d(TAG, "Base plan ID: " + offerDetails.getBasePlanId());
                                    NativePurchasesLog.d(TAG, "Price: " + price);
                                    NativePurchasesLog.d(TAG, "Formatted price: " + product.getString("priceString"));
                                    NativePurchasesLog.d(TAG, "Currency: " + product.getString("currencyCode"));

                                    products.put(product);
                                    addedOffers++;
                                }

                                if (addedOffers == 0) {
                                    NativePurchasesLog.w(
                                        TAG,
                                        "All subscription offers missing pricing phases for product: " + productDetails.getProductId()
                                    );
                                }
                            }
                        }
                        JSObject ret = new JSObject();
                        ret.put("products", products);
                        NativePurchasesLog.d(TAG, "Returning " + products.length() + " products");
                        closeBillingClient();
                        call.resolve(ret);
                    }
                }
            );
        });
    }

    @PluginMethod
    public void getProducts(PluginCall call) {
        NativePurchasesLog.d(TAG, "getProducts() called");
        JSONArray productIdentifiersArray = call.getArray("productIdentifiers");
        String productType = call.getString("productType", "inapp");
        NativePurchasesLog.d(TAG, "Product type: " + productType);
        NativePurchasesLog.d(TAG, "Raw productIdentifiersArray: " + productIdentifiersArray);
        NativePurchasesLog.d(
            TAG,
            "productIdentifiersArray length: " + (productIdentifiersArray != null ? productIdentifiersArray.length() : "null")
        );

        if (productIdentifiersArray == null || productIdentifiersArray.length() == 0) {
            NativePurchasesLog.d(TAG, "Error: productIdentifiers array missing or empty");
            call.reject("productIdentifiers array missing");
            return;
        }

        List<String> productIdentifiers = new ArrayList<>();
        for (int i = 0; i < productIdentifiersArray.length(); i++) {
            String productId = productIdentifiersArray.optString(i, "");
            NativePurchasesLog.d(TAG, "Array index " + i + ": '" + productId + "'");
            productIdentifiers.add(productId);
            NativePurchasesLog.d(TAG, "Added product identifier: " + productId);
        }
        NativePurchasesLog.d(TAG, "Final productIdentifiers list: " + productIdentifiers.toString());
        queryProductDetails(productIdentifiers, productType, call);
    }

    @PluginMethod
    public void getProduct(PluginCall call) {
        NativePurchasesLog.d(TAG, "getProduct() called");
        String productIdentifier = call.getString("productIdentifier");
        String productType = call.getString("productType", "inapp");
        NativePurchasesLog.d(TAG, "Product identifier: " + productIdentifier);
        NativePurchasesLog.d(TAG, "Product type: " + productType);

        assert productIdentifier != null;
        if (productIdentifier.isEmpty()) {
            NativePurchasesLog.d(TAG, "Error: productIdentifier is empty");
            call.reject("productIdentifier is empty");
            return;
        }
        querySingleProductDetails(productIdentifier, productType, call);
    }

    @PluginMethod
    public void getPurchases(PluginCall call) {
        NativePurchasesLog.d(TAG, "getPurchases() called");
        String productType = call.getString("productType");
        NativePurchasesLog.d(TAG, "Product type filter: " + productType);
        String appAccountToken = call.getString("appAccountToken");
        final String accountFilter = appAccountToken != null && !appAccountToken.isEmpty() ? appAccountToken : null;
        final boolean hasAccountFilter = accountFilter != null && !accountFilter.isEmpty();
        NativePurchasesLog.d(TAG, "Account filter provided: " + (hasAccountFilter ? "[REDACTED]" : "none"));

        final boolean queryInApp = productType == null || productType.equals("inapp");
        final boolean querySubs = productType == null || productType.equals("subs");

        if (!queryInApp && !querySubs) {
            NativePurchasesLog.d(TAG, "Unknown product type filter provided, returning empty result");
            JSObject result = new JSObject();
            result.put("purchases", new JSONArray());
            call.resolve(result);
            return;
        }

        withBillingClient(call, () -> {
            JSONArray allPurchases = new JSONArray();
            AtomicInteger pendingQueries = new AtomicInteger((queryInApp ? 1 : 0) + (querySubs ? 1 : 0));
            AtomicBoolean finished = new AtomicBoolean(false);
            // A query that fails must not be reported as "no purchases": callers use an
            // empty list to revoke entitlements, so a transient Play error would strip
            // a paying user. Remember the first failure and reject instead.
            AtomicReference<String> queryFailure = new AtomicReference<>(null);

            Runnable maybeFinish = () -> {
                int remaining = pendingQueries.decrementAndGet();
                NativePurchasesLog.d(TAG, "Pending purchase queries remaining: " + remaining);
                if (remaining <= 0 && finished.compareAndSet(false, true)) {
                    closeBillingClient();
                    String failure = queryFailure.get();
                    if (failure != null) {
                        NativePurchasesLog.w(TAG, "Rejecting getPurchases: " + failure);
                        call.reject("Failed to query purchases: " + failure, "QUERY_PURCHASES_FAILED");
                        return;
                    }
                    JSObject result = new JSObject();
                    result.put("purchases", allPurchases);
                    NativePurchasesLog.d(TAG, "Returning " + allPurchases.length() + " purchases");
                    call.resolve(result);
                }
            };

            if (queryInApp) {
                NativePurchasesLog.d(TAG, "Querying in-app purchases");
                QueryPurchasesParams queryInAppParams = QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build();

                billingClient.queryPurchasesAsync(queryInAppParams, (billingResult, purchases) -> {
                    try {
                        NativePurchasesLog.d(TAG, "In-app purchases query result: " + billingResult.getResponseCode());
                        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && purchases != null) {
                            for (Purchase purchase : purchases) {
                                NativePurchasesLog.d(TAG, "Processing in-app purchase for products: " + purchase.getProducts());
                                AccountIdentifiers accountIdentifiers = purchase.getAccountIdentifiers();
                                String purchaseAccountId = accountIdentifiers != null ? accountIdentifiers.getObfuscatedAccountId() : null;
                                if (hasAccountFilter && (purchaseAccountId == null || !purchaseAccountId.equals(accountFilter))) {
                                    NativePurchasesLog.d(TAG, "Skipping in-app purchase due to account filter mismatch");
                                    continue;
                                }
                                JSObject purchaseData = new JSObject();
                                purchaseData.put("transactionId", purchase.getPurchaseToken());
                                purchaseData.put(
                                    "productIdentifier",
                                    purchase.getProducts().isEmpty() ? null : purchase.getProducts().get(0)
                                );
                                purchaseData.put("purchaseDate", formatUtcIso8601(purchase.getPurchaseTime()));
                                purchaseData.put("quantity", purchase.getQuantity());
                                purchaseData.put("productType", "inapp");
                                purchaseData.put("orderId", purchase.getOrderId());
                                purchaseData.put("purchaseToken", purchase.getPurchaseToken());
                                purchaseData.put("isAcknowledged", purchase.isAcknowledged());
                                purchaseData.put("purchaseState", String.valueOf(purchase.getPurchaseState()));
                                purchaseData.put("appAccountToken", purchaseAccountId);
                                purchaseData.put("willCancel", null);
                                synchronized (allPurchases) {
                                    allPurchases.put(purchaseData);
                                }
                            }
                        } else {
                            NativePurchasesLog.d(TAG, "In-app purchase query failed: " + billingResult.getDebugMessage());
                            queryFailure.compareAndSet(null, describeQueryFailure("inapp", billingResult));
                        }
                    } catch (Exception ex) {
                        NativePurchasesLog.d(TAG, "Error processing in-app purchase query: " + ex.getMessage());
                        queryFailure.compareAndSet(null, describeQueryFailure("inapp", billingResult) + " / " + ex.getMessage());
                    } finally {
                        maybeFinish.run();
                    }
                });
            }

            if (querySubs) {
                NativePurchasesLog.d(TAG, "Querying only subscription purchases");
                QueryPurchasesParams querySubsParams = QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build();

                billingClient.queryPurchasesAsync(querySubsParams, (billingResult, purchases) -> {
                    try {
                        NativePurchasesLog.d(TAG, "Subscription purchases query result: " + billingResult.getResponseCode());
                        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && purchases != null) {
                            for (Purchase purchase : purchases) {
                                NativePurchasesLog.d(TAG, "Processing subscription purchase for products: " + purchase.getProducts());
                                AccountIdentifiers accountIdentifiers = purchase.getAccountIdentifiers();
                                String purchaseAccountId = accountIdentifiers != null ? accountIdentifiers.getObfuscatedAccountId() : null;
                                if (hasAccountFilter && (purchaseAccountId == null || !purchaseAccountId.equals(accountFilter))) {
                                    NativePurchasesLog.d(TAG, "Skipping subscription purchase due to account filter mismatch");
                                    continue;
                                }
                                JSObject purchaseData = new JSObject();
                                purchaseData.put("transactionId", purchase.getPurchaseToken());
                                purchaseData.put(
                                    "productIdentifier",
                                    purchase.getProducts().isEmpty() ? null : purchase.getProducts().get(0)
                                );
                                purchaseData.put("purchaseDate", formatUtcIso8601(purchase.getPurchaseTime()));
                                purchaseData.put("quantity", purchase.getQuantity());
                                purchaseData.put("productType", "subs");
                                purchaseData.put("orderId", purchase.getOrderId());
                                purchaseData.put("purchaseToken", purchase.getPurchaseToken());
                                purchaseData.put("isAcknowledged", purchase.isAcknowledged());
                                purchaseData.put("purchaseState", String.valueOf(purchase.getPurchaseState()));
                                purchaseData.put("appAccountToken", purchaseAccountId);
                                purchaseData.put("willCancel", null);
                                synchronized (allPurchases) {
                                    allPurchases.put(purchaseData);
                                }
                            }
                        } else {
                            NativePurchasesLog.d(TAG, "Subscription purchase query failed: " + billingResult.getDebugMessage());
                            queryFailure.compareAndSet(null, describeQueryFailure("subs", billingResult));
                        }
                    } catch (Exception ex) {
                        NativePurchasesLog.d(TAG, "Error processing subscription purchase query: " + ex.getMessage());
                        queryFailure.compareAndSet(null, describeQueryFailure("subs", billingResult) + " / " + ex.getMessage());
                    } finally {
                        maybeFinish.run();
                    }
                });
            }
        });
    }

    @PluginMethod
    public void manageSubscriptions(PluginCall call) {
        NativePurchasesLog.d(TAG, "manageSubscriptions() called");
        try {
            // Open the Google Play subscription management page
            // This intent opens the subscription center for the app
            android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW);
            intent.setData(
                android.net.Uri.parse("https://play.google.com/store/account/subscriptions?package=" + getContext().getPackageName())
            );
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(intent);

            NativePurchasesLog.d(TAG, "manageSubscriptions() opened successfully");
            call.resolve();
        } catch (Exception e) {
            NativePurchasesLog.d(TAG, "manageSubscriptions() error: " + e.getMessage());
            call.reject("Failed to open subscription management page", e);
        }
    }

    @PluginMethod
    public void presentOfferCodeRedeemSheet(PluginCall call) {
        NativePurchasesLog.d(TAG, "presentOfferCodeRedeemSheet() called");
        call.reject("presentOfferCodeRedeemSheet is only available on iOS");
    }

    @PluginMethod
    public void acknowledgePurchase(PluginCall call) {
        NativePurchasesLog.d(TAG, "acknowledgePurchase() called");
        String purchaseToken = call.getString("purchaseToken");

        if (purchaseToken == null || purchaseToken.isEmpty()) {
            NativePurchasesLog.d(TAG, "Error: purchaseToken is empty");
            call.reject("purchaseToken is required");
            return;
        }

        NativePurchasesLog.d(TAG, "Manually acknowledging purchase");
        withBillingClient(call, () -> acknowledgeWithClient(call, currentBillingClient(), purchaseToken));
    }

    /**
     * Manual acknowledge on {@code client}, counted as a pending finish so a later call does not
     * end the client under it. The call settles once: from Play's answer, or with a FINISH_TIMEOUT
     * rejection if a later call stops waiting on it. Package-private for tests.
     */
    void acknowledgeWithClient(PluginCall call, BillingClient client, String purchaseToken) {
        AtomicBoolean settled = new AtomicBoolean(false);
        Runnable onAbandon = () -> {
            if (settled.compareAndSet(false, true)) {
                call.reject(
                    "Acknowledge was not answered in time. It may still complete; check getUnfinishedTransactions before retrying.",
                    "FINISH_TIMEOUT"
                );
            }
        };
        if (!beginFinishingPurchase(client, onAbandon)) {
            call.reject("Billing client closed before the acknowledge could run. Please try again.");
            return;
        }
        AcknowledgePurchaseParams acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchaseToken)
            .build();
        AtomicBoolean answered = new AtomicBoolean(false);
        AcknowledgePurchaseResponseListener listener = (billingResult) -> {
            if (!answered.compareAndSet(false, true)) {
                return;
            }
            NativePurchasesLog.d(TAG, "Acknowledge result: " + billingResult.getResponseCode() + " - " + billingResult.getDebugMessage());
            endFinishingPurchase(client);
            if (!settled.compareAndSet(false, true)) {
                return;
            }
            if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                NativePurchasesLog.d(TAG, "Purchase acknowledged successfully");
                call.resolve();
            } else {
                NativePurchasesLog.d(TAG, "Purchase acknowledgment failed");
                call.reject("Failed to acknowledge purchase: " + billingResult.getDebugMessage());
            }
        };
        try {
            client.acknowledgePurchase(acknowledgePurchaseParams, listener);
        } catch (RuntimeException e) {
            listener.onAcknowledgePurchaseResponse(thrownResult(e));
        }
    }

    @PluginMethod
    public void consumePurchase(PluginCall call) {
        NativePurchasesLog.d(TAG, "consumePurchase() called");
        String purchaseToken = call.getString("purchaseToken");

        if (purchaseToken == null || purchaseToken.isEmpty()) {
            NativePurchasesLog.d(TAG, "Error: purchaseToken is empty");
            call.reject("purchaseToken is required");
            return;
        }

        NativePurchasesLog.d(TAG, "Consuming purchase");
        withBillingClient(call, () -> consumeWithClient(call, currentBillingClient(), purchaseToken));
    }

    /** Manual consume on {@code client}, with the same pending finish counting as {@link #acknowledgeWithClient}. */
    void consumeWithClient(PluginCall call, BillingClient client, String purchaseToken) {
        AtomicBoolean settled = new AtomicBoolean(false);
        Runnable onAbandon = () -> {
            if (settled.compareAndSet(false, true)) {
                call.reject(
                    "Consume was not answered in time. It may still complete; check getUnfinishedTransactions before retrying.",
                    "FINISH_TIMEOUT"
                );
            }
        };
        if (!beginFinishingPurchase(client, onAbandon)) {
            call.reject("Billing client closed before the consume could run. Please try again.");
            return;
        }
        ConsumeParams consumeParams = ConsumeParams.newBuilder().setPurchaseToken(purchaseToken).build();
        AtomicBoolean answered = new AtomicBoolean(false);
        ConsumeResponseListener listener = (billingResult, consumedToken) -> {
            if (!answered.compareAndSet(false, true)) {
                return;
            }
            NativePurchasesLog.d(TAG, "Consume result: " + billingResult.getResponseCode() + " - " + billingResult.getDebugMessage());
            endFinishingPurchase(client);
            if (!settled.compareAndSet(false, true)) {
                return;
            }
            if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                NativePurchasesLog.d(TAG, "Purchase consumed successfully");
                call.resolve();
            } else {
                NativePurchasesLog.d(TAG, "Purchase consumption failed");
                call.reject("Failed to consume purchase: " + billingResult.getDebugMessage());
            }
        };
        try {
            client.consumeAsync(consumeParams, listener);
        } catch (RuntimeException e) {
            listener.onConsumeResponse(thrownResult(e), purchaseToken);
        }
    }

    @PluginMethod
    public void getAppTransaction(PluginCall call) {
        NativePurchasesLog.d(TAG, "getAppTransaction() called");
        try {
            PackageManager pm = getContext().getPackageManager();
            String packageName = getContext().getPackageName();

            PackageInfo packageInfo;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageInfo = pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0));
            } else {
                packageInfo = pm.getPackageInfo(packageName, 0);
            }

            JSObject appTransaction = new JSObject();

            // Get version name (e.g., "1.0.0")
            String versionName = packageInfo.versionName != null ? packageInfo.versionName : "1.0.0";
            appTransaction.put("appVersion", versionName);

            // For Android, we can't get the "original" version when the user first downloaded
            // from Google Play like iOS can. The firstInstallTime is per-device, not per-account.
            // We use firstInstallTime date and current version as "original" which is the best we can do.
            // For accurate original version tracking, developers should implement server-side tracking
            // using Google Play Developer API or their own backend.
            appTransaction.put("originalAppVersion", versionName);

            // First install time on this device (milliseconds since epoch)
            long firstInstallTime = packageInfo.firstInstallTime;
            String originalPurchaseDate = formatUtcIso8601(firstInstallTime);
            appTransaction.put("originalPurchaseDate", originalPurchaseDate);

            // Package name (bundle ID equivalent)
            appTransaction.put("bundleId", packageName);

            // Android doesn't have environment like iOS (Sandbox/Production)
            appTransaction.put("environment", null);

            // Android doesn't have JWS representation
            // jwsRepresentation is not set (will be undefined in JS)

            NativePurchasesLog.d(TAG, "App transaction - version: " + versionName + ", firstInstall: " + originalPurchaseDate);

            JSObject result = new JSObject();
            result.put("appTransaction", appTransaction);
            call.resolve(result);
        } catch (PackageManager.NameNotFoundException e) {
            NativePurchasesLog.d(TAG, "getAppTransaction() error: " + e.getMessage());
            call.reject("Failed to get package info: " + e.getMessage());
        } catch (Exception e) {
            NativePurchasesLog.d(TAG, "getAppTransaction() error: " + e.getMessage());
            call.reject("Failed to get app transaction: " + e.getMessage());
        }
    }

    @PluginMethod
    public void isEntitledToOldBusinessModel(PluginCall call) {
        NativePurchasesLog.d(TAG, "isEntitledToOldBusinessModel() called");
        String targetVersion = call.getString("targetVersion");

        if (targetVersion == null || targetVersion.isEmpty()) {
            NativePurchasesLog.d(TAG, "Error: targetVersion is empty");
            call.reject("targetVersion is required on Android");
            return;
        }

        try {
            PackageManager pm = getContext().getPackageManager();
            String packageName = getContext().getPackageName();

            PackageInfo packageInfo;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageInfo = pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0));
            } else {
                packageInfo = pm.getPackageInfo(packageName, 0);
            }

            // Get current version name as "original" version
            // Note: On Android, we can't get the actual original version from Google Play
            // This uses the current installed version which may not be accurate
            String originalVersion = packageInfo.versionName != null ? packageInfo.versionName : "1.0.0";

            // Compare versions
            boolean isOlder = compareVersions(originalVersion, targetVersion) < 0;

            NativePurchasesLog.d(
                TAG,
                "isEntitledToOldBusinessModel - original: " + originalVersion + ", target: " + targetVersion + ", isOlder: " + isOlder
            );

            JSObject result = new JSObject();
            result.put("isOlderVersion", isOlder);
            result.put("originalAppVersion", originalVersion);
            call.resolve(result);
        } catch (PackageManager.NameNotFoundException e) {
            NativePurchasesLog.d(TAG, "isEntitledToOldBusinessModel() error: " + e.getMessage());
            call.reject("Failed to get package info: " + e.getMessage());
        } catch (Exception e) {
            NativePurchasesLog.d(TAG, "isEntitledToOldBusinessModel() error: " + e.getMessage());
            call.reject("Failed to check business model entitlement: " + e.getMessage());
        }
    }

    /**
     * Compares two semantic version strings.
     * Returns: negative if v1 < v2, zero if v1 == v2, positive if v1 > v2
     */
    private int compareVersions(String version1, String version2) {
        String[] v1Parts = version1.split("\\.");
        String[] v2Parts = version2.split("\\.");

        int maxLength = Math.max(v1Parts.length, v2Parts.length);

        for (int i = 0; i < maxLength; i++) {
            int v1Value = 0;
            int v2Value = 0;

            if (i < v1Parts.length) {
                try {
                    // Handle versions with non-numeric suffixes like "1.0.0-beta"
                    String v1Part = v1Parts[i].replaceAll("[^0-9].*", "");
                    v1Value = v1Part.isEmpty() ? 0 : Integer.parseInt(v1Part);
                } catch (NumberFormatException e) {
                    v1Value = 0;
                }
            }

            if (i < v2Parts.length) {
                try {
                    String v2Part = v2Parts[i].replaceAll("[^0-9].*", "");
                    v2Value = v2Part.isEmpty() ? 0 : Integer.parseInt(v2Part);
                } catch (NumberFormatException e) {
                    v2Value = 0;
                }
            }

            if (v1Value < v2Value) {
                return -1;
            } else if (v1Value > v2Value) {
                return 1;
            }
        }

        return 0;
    }
}
