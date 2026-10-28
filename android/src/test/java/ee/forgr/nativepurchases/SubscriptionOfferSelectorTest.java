package ee.forgr.nativepurchases;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import ee.forgr.nativepurchases.SubscriptionOfferSelector.Offer;
import ee.forgr.nativepurchases.SubscriptionOfferSelector.Selection;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class SubscriptionOfferSelectorTest {

    @Test
    public void selectPrefersAnExplicitOfferToken() {
        List<Offer> offers = offers(offer("monthly", "token-base"), offer("monthly", "token-intro"));

        Selection selection = SubscriptionOfferSelector.select(offers, "monthly", "token-intro");

        assertFalse(selection.isOfferTokenNotFound());
        assertEquals("token-intro", selection.offer().getOfferToken());
    }

    @Test
    public void selectPrefersTheOfferTokenOverThePlanIdentifier() {
        List<Offer> offers = offers(offer("monthly", "token-monthly"), offer("yearly", "token-yearly"));

        Selection selection = SubscriptionOfferSelector.select(offers, "monthly", "token-yearly");

        assertEquals("token-yearly", selection.offer().getOfferToken());
    }

    @Test
    public void selectReportsAnOfferTokenThatMatchesNothing() {
        List<Offer> offers = offers(offer("monthly", "token-base"));

        Selection selection = SubscriptionOfferSelector.select(offers, "monthly", "token-stale");

        assertTrue(selection.isOfferTokenNotFound());
        assertNull(selection.offer());
    }

    @Test
    public void selectFallsBackToThePlanIdentifierWhenNoOfferTokenIsGiven() {
        List<Offer> offers = offers(offer("monthly", "token-monthly"), offer("yearly", "token-yearly"));

        Selection selection = SubscriptionOfferSelector.select(offers, "yearly", null);

        assertEquals("token-yearly", selection.offer().getOfferToken());
    }

    @Test
    public void selectTreatsAnEmptyOfferTokenAsAbsent() {
        List<Offer> offers = offers(offer("monthly", "token-monthly"), offer("yearly", "token-yearly"));

        Selection selection = SubscriptionOfferSelector.select(offers, "yearly", "");

        assertFalse(selection.isOfferTokenNotFound());
        assertEquals("token-yearly", selection.offer().getOfferToken());
    }

    @Test
    public void selectFallsBackToTheFirstOfferWhenThePlanIdentifierMatchesNothing() {
        List<Offer> offers = offers(offer("monthly", "token-monthly"), offer("yearly", "token-yearly"));

        Selection selection = SubscriptionOfferSelector.select(offers, "quarterly", null);

        assertEquals("token-monthly", selection.offer().getOfferToken());
    }

    @Test
    public void selectUsesTheOfferTokenWhenNoPlanIdentifierIsGiven() {
        List<Offer> offers = offers(offer("monthly", "token-monthly"), offer("yearly", "token-yearly"));

        // A token identifies one offer on its own, which is why the plugin no
        // longer demands a planIdentifier alongside it for a subscription.
        Selection selection = SubscriptionOfferSelector.select(offers, null, "token-yearly");

        assertFalse(selection.isOfferTokenNotFound());
        assertEquals("token-yearly", selection.offer().getOfferToken());
    }

    @Test
    public void selectReportsAnUnknownOfferTokenEvenWithNoPlanIdentifier() {
        List<Offer> offers = offers(offer("monthly", "token-monthly"));

        Selection selection = SubscriptionOfferSelector.select(offers, null, "token-stale");

        assertTrue(selection.isOfferTokenNotFound());
        assertNull(selection.offer());
    }

    @Test
    public void selectFallsBackToTheFirstOfferWhenNoPlanIdentifierIsGiven() {
        List<Offer> offers = offers(offer("monthly", "token-monthly"), offer("yearly", "token-yearly"));

        Selection selection = SubscriptionOfferSelector.select(offers, null, null);

        assertEquals("token-monthly", selection.offer().getOfferToken());
    }

    @Test
    public void selectKeepsTheExistingBehaviourForABasePlanWithNoOffer() {
        List<Offer> offers = offers(offer("monthly", "token-monthly"));

        Selection selection = SubscriptionOfferSelector.select(offers, "monthly", null);

        assertEquals("token-monthly", selection.offer().getOfferToken());
    }

    @Test
    public void selectHandlesAnEmptyOfferList() {
        Selection selection = SubscriptionOfferSelector.select(new ArrayList<>(), "monthly", null);

        assertFalse(selection.isOfferTokenNotFound());
        assertNull(selection.offer());
    }

    @Test
    public void selectHandlesANullOfferList() {
        Selection selection = SubscriptionOfferSelector.select(null, "monthly", "token");

        assertFalse(selection.isOfferTokenNotFound());
        assertNull(selection.offer());
    }

    @Test
    public void unwrapReturnsNullForAnOfferItDidNotAdapt() {
        assertNull(SubscriptionOfferSelector.unwrap(offer("monthly", "token")));
    }

    @Test
    public void adaptHandlesANullList() {
        assertTrue(SubscriptionOfferSelector.adapt(null).isEmpty());
    }

    private static List<Offer> offers(Offer... values) {
        List<Offer> offers = new ArrayList<>();
        for (Offer value : values) {
            offers.add(value);
        }
        return offers;
    }

    private static Offer offer(String basePlanId, String offerToken) {
        return new Offer() {
            @Override
            public String getBasePlanId() {
                return basePlanId;
            }

            @Override
            public String getOfferToken() {
                return offerToken;
            }
        };
    }
}
