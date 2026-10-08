package ee.forgr.nativepurchases;

import android.util.Log;
import com.android.billingclient.api.Purchase;

/**
 * Centralized logging for the plugin. Debug logs are emitted only when the app is a debug build
 * or {@code debugLogging} is enabled in the plugin config. Sensitive purchase fields are never logged.
 */
public final class NativePurchasesLog {

    private static volatile boolean explicitDebugLogging;

    private NativePurchasesLog() {}

    public static void configure(boolean debugLogging) {
        explicitDebugLogging = debugLogging;
    }

    public static boolean isLoggingEnabled() {
        return BuildConfig.DEBUG || explicitDebugLogging;
    }

    public static void d(String tag, String message) {
        if (isLoggingEnabled()) {
            Log.d(tag, message);
        }
    }

    public static void i(String tag, String message) {
        if (isLoggingEnabled()) {
            Log.i(tag, message);
        }
    }

    public static void w(String tag, String message) {
        if (isLoggingEnabled()) {
            Log.w(tag, message);
        }
    }

    public static void e(String tag, String message) {
        if (isLoggingEnabled()) {
            Log.e(tag, message);
        }
    }

    public static String redactSensitive(String value) {
        if (value == null || value.isEmpty()) {
            return "[REDACTED]";
        }
        return "[REDACTED]";
    }

    public static String purchaseDebugSummary(Purchase purchase) {
        if (purchase == null) {
            return "purchase=null";
        }
        return (
            "purchase{productIds=" +
            purchase.getProducts() +
            ", state=" +
            purchase.getPurchaseState() +
            ", acknowledged=" +
            purchase.isAcknowledged() +
            "}"
        );
    }
}
