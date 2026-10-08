package ee.forgr.nativepurchases;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

public class NativePurchasesLogTest {

    @After
    public void tearDown() {
        NativePurchasesLog.configure(false);
    }

    @Test
    public void redactSensitive_neverReturnsOriginalValue() {
        String secret = "gpa.secret-token-value";
        assertEquals("[REDACTED]", NativePurchasesLog.redactSensitive(secret));
        assertEquals("[REDACTED]", NativePurchasesLog.redactSensitive(null));
        assertEquals("[REDACTED]", NativePurchasesLog.redactSensitive(""));
    }

    @Test
    public void isLoggingEnabled_reflectsExplicitDebugFlag() {
        NativePurchasesLog.configure(true);
        assertTrue(NativePurchasesLog.isLoggingEnabled() || BuildConfig.DEBUG);

        NativePurchasesLog.configure(false);
        if (!BuildConfig.DEBUG) {
            assertFalse(NativePurchasesLog.isLoggingEnabled());
        }
    }
}
