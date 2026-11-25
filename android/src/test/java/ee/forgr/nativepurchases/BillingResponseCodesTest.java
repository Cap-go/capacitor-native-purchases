package ee.forgr.nativepurchases;

import static org.junit.Assert.assertEquals;

import com.android.billingclient.api.BillingClient;
import org.junit.Test;

public class BillingResponseCodesTest {

    @Test
    public void name_mapsServiceTimeout() {
        assertEquals("SERVICE_TIMEOUT", BillingResponseCodes.name(BillingClient.BillingResponseCode.SERVICE_TIMEOUT));
    }

    @Test
    public void name_mapsOk() {
        assertEquals("OK", BillingResponseCodes.name(BillingClient.BillingResponseCode.OK));
    }

    @Test
    public void name_mapsUserCanceled() {
        assertEquals("USER_CANCELED", BillingResponseCodes.name(BillingClient.BillingResponseCode.USER_CANCELED));
    }

    @Test
    public void name_unknownCodeUsesPrefix() {
        assertEquals("UNKNOWN_999", BillingResponseCodes.name(999));
    }
}
