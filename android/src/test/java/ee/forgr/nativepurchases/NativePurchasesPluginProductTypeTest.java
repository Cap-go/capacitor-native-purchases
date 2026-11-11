package ee.forgr.nativepurchases;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class NativePurchasesPluginProductTypeTest {

    @Test
    public void responseProductType_followsWhatTheCallerBought() {
        assertEquals("subs", NativePurchasesPlugin.responseProductType("subs"));
        assertEquals("inapp", NativePurchasesPlugin.responseProductType("inapp"));
    }

    @Test
    public void responseProductType_defaultsToInappLikePurchaseProduct() {
        assertEquals("inapp", NativePurchasesPlugin.responseProductType(null));
    }
}
