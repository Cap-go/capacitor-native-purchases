package ee.forgr.nativepurchases;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class ProductPayloadMapperTest {

    @Test
    public void parseIso8601Period_returnsNullForInvalidValue() {
        assertNull(ProductPayloadMapper.parseIso8601Period("invalid"));
    }

    @Test
    public void currencySymbol_returnsSymbolForKnownCurrency() {
        assertEquals("$", ProductPayloadMapper.currencySymbol("USD"));
    }
}
