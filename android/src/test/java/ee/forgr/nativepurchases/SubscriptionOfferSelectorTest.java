package ee.forgr.nativepurchases;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import ee.forgr.nativepurchases.SubscriptionOfferSelector.Offer;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class SubscriptionOfferSelectorTest {

    @Test
    public void selectPrefersAnExplicitOfferToken() {
        List<Offer> offers = offers(offer("monthly", "token-base"), offer("monthly", "token-intro"));

        assertEquals(1, SubscriptionOfferSelector.selectIndex(offers, "monthly", "token-intro"));
    }

    @Test
    public void selectPrefersTheOfferTokenOverThePlanIdentifier() {
        List<Offer> offers = offers(offer("monthly", "token-monthly"), offer("yearly", "token-yearly"));

        assertEquals(1, SubscriptionOfferSelector.selectIndex(offers, "monthly", "token-yearly"));
    }

    @Test
    public void selectReportsAnOfferTokenThatMatchesNothing() {
        List<Offer> offers = offers(offer("monthly", "token-base"));

        assertEquals(SubscriptionOfferSelector.NOT_FOUND, SubscriptionOfferSelector.selectIndex(offers, "monthly", "token-stale"));
    }

    @Test
    public void selectFallsBackToThePlanIdentifierWhenNoOfferTokenIsGiven() {
        List<Offer> offers = offers(offer("monthly", "token-monthly"), offer("yearly", "token-yearly"));

        assertEquals(1, SubscriptionOfferSelector.selectIndex(offers, "yearly", null));
    }

    @Test
    public void selectTreatsAnEmptyOfferTokenAsAbsent() {
        List<Offer> offers = offers(offer("monthly", "token-monthly"), offer("yearly", "token-yearly"));

        assertEquals(1, SubscriptionOfferSelector.selectIndex(offers, "yearly", ""));
    }

    @Test
    public void selectFallsBackToTheFirstOfferWhenThePlanIdentifierMatchesNothing() {
        List<Offer> offers = offers(offer("monthly", "token-monthly"), offer("yearly", "token-yearly"));

        assertEquals(0, SubscriptionOfferSelector.selectIndex(offers, "quarterly", null));
    }

    @Test
    public void selectUsesTheOfferTokenWhenNoPlanIdentifierIsGiven() {
        List<Offer> offers = offers(offer("monthly", "token-monthly"), offer("yearly", "token-yearly"));

        // A token identifies one offer on its own, which is why the plugin no
        // longer demands a planIdentifier alongside it for a subscription.
        assertEquals(1, SubscriptionOfferSelector.selectIndex(offers, null, "token-yearly"));
    }

    @Test
    public void selectReportsAnUnknownOfferTokenEvenWithNoPlanIdentifier() {
        List<Offer> offers = offers(offer("monthly", "token-monthly"));

        // The other half of accepting a token without a planIdentifier: a
        // stale token must not fall through to the first-offer default.
        assertEquals(SubscriptionOfferSelector.NOT_FOUND, SubscriptionOfferSelector.selectIndex(offers, null, "token-stale"));
    }

    @Test
    public void selectFallsBackToTheFirstOfferWhenNoPlanIdentifierIsGiven() {
        List<Offer> offers = offers(offer("monthly", "token-monthly"), offer("yearly", "token-yearly"));

        assertEquals(0, SubscriptionOfferSelector.selectIndex(offers, null, null));
    }

    @Test
    public void selectByPlanIdentifierTakesTheFirstOfTwoEntriesOnOnePlan() {
        // The ambiguity this class exists for: one base plan carrying an offer
        // is two entries with the same basePlanId, so selecting by plan alone
        // is decided by the store's order. Pinned, because it is the previous
        // behaviour and an offerToken is the only way to override it.
        List<Offer> offers = offers(offer("monthly", "token-base"), offer("monthly", "token-intro"));

        assertEquals(0, SubscriptionOfferSelector.selectIndex(offers, "monthly", null));
    }

    @Test
    public void selectHandlesAnEmptyOfferList() {
        assertEquals(SubscriptionOfferSelector.NOT_FOUND, SubscriptionOfferSelector.selectIndex(new ArrayList<>(), "monthly", null));
    }

    @Test
    public void selectHandlesANullOfferList() {
        assertEquals(SubscriptionOfferSelector.NOT_FOUND, SubscriptionOfferSelector.selectIndex(null, "monthly", "token"));
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
