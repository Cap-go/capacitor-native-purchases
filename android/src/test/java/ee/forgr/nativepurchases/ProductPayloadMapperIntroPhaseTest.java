package ee.forgr.nativepurchases;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.when;

import com.android.billingclient.api.ProductDetails;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Unit tests for {@link ProductPayloadMapper#findLeadingIntroductoryPhase}, which decides whether an
 * offer's first pricing phase is introductory pricing (a free trial or a reduced first period) or
 * simply the ongoing price.
 *
 * <p>Separate from {@link ProductPayloadMapperTest} because these need Mockito for
 * {@link ProductDetails.PricingPhase}.
 */
@RunWith(MockitoJUnitRunner.class)
public class ProductPayloadMapperIntroPhaseTest {

    private static final int RECURRENCE_MODE_INFINITE_RECURRING = 1;
    private static final int RECURRENCE_MODE_FINITE_RECURRING = 2;
    private static final int RECURRENCE_MODE_NON_RECURRING = 3;

    @Mock
    private ProductDetails.PricingPhase leadingPhase;

    @Mock
    private ProductDetails.PricingPhase recurringPhase;

    @Test
    public void findLeadingIntroductoryPhase_returnsNullForNoPhases() {
        assertNull(ProductPayloadMapper.findLeadingIntroductoryPhase(null));
        assertNull(ProductPayloadMapper.findLeadingIntroductoryPhase(Collections.emptyList()));
    }

    @Test
    public void findLeadingIntroductoryPhase_returnsNullWhenTheOfferOnlyRecurs() {
        when(recurringPhase.getRecurrenceMode()).thenReturn(RECURRENCE_MODE_INFINITE_RECURRING);

        assertNull(ProductPayloadMapper.findLeadingIntroductoryPhase(List.of(recurringPhase)));
    }

    @Test
    public void findLeadingIntroductoryPhase_returnsATrialOnlyPhase() {
        // A free trial with nothing after it is a single non-recurring phase, so a rule that needs
        // two phases would miss it.
        when(leadingPhase.getRecurrenceMode()).thenReturn(RECURRENCE_MODE_NON_RECURRING);

        assertSame(leadingPhase, ProductPayloadMapper.findLeadingIntroductoryPhase(List.of(leadingPhase)));
    }

    @Test
    public void findLeadingIntroductoryPhase_returnsTheTrialAheadOfTheRecurringPhase() {
        when(leadingPhase.getRecurrenceMode()).thenReturn(RECURRENCE_MODE_NON_RECURRING);

        assertSame(leadingPhase, ProductPayloadMapper.findLeadingIntroductoryPhase(List.of(leadingPhase, recurringPhase)));
    }

    @Test
    public void findLeadingIntroductoryPhase_returnsAReducedFirstPeriod() {
        // An intro price (say three months at a lower rate) before the ongoing price.
        when(leadingPhase.getRecurrenceMode()).thenReturn(RECURRENCE_MODE_FINITE_RECURRING);

        assertSame(leadingPhase, ProductPayloadMapper.findLeadingIntroductoryPhase(List.of(leadingPhase, recurringPhase)));
    }
}
