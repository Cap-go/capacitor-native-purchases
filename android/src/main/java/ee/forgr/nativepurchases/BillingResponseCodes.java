package ee.forgr.nativepurchases;

import com.android.billingclient.api.BillingClient;

/**
 * Stable rejection {@code code} strings for Android {@link com.getcapacitor.PluginCall#reject}.
 * Play {@link BillingClient.BillingResponseCode} values are mapped in {@link #name(int)}.
 */
public final class BillingResponseCodes {

    public static final String PURCHASE_PENDING = "PURCHASE_PENDING";
    public static final String PRODUCT_NOT_FOUND = "PRODUCT_NOT_FOUND";
    public static final String BILLING_CLIENT_CLOSED = "BILLING_CLIENT_CLOSED";
    public static final String PURCHASE_NOT_FOUND = "PURCHASE_NOT_FOUND";

    private BillingResponseCodes() {}

    /**
     * Maps a Play billing response code to a stable plugin error code.
     */
    public static String name(int code) {
        switch (code) {
            case BillingClient.BillingResponseCode.OK:
                return "OK";
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
            case BillingClient.BillingResponseCode.SERVICE_TIMEOUT:
                return "SERVICE_TIMEOUT";
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
}
