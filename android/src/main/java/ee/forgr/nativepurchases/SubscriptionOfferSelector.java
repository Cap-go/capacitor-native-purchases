package ee.forgr.nativepurchases;

import com.android.billingclient.api.ProductDetails;
import java.util.ArrayList;
import java.util.List;

/**
 * Chooses which of a subscription's offers a purchase should use.
 *
 * Play returns one {@link ProductDetails.SubscriptionOfferDetails} per
 * (base plan, offer) pair, and the offer token the billing flow is given is
 * what decides the price actually charged. Selecting by base plan alone is
 * therefore ambiguous as soon as a base plan carries an offer: the base plan
 * and the offer are two entries with the same {@code basePlanId}, and taking
 * the first means the caller cannot say whether the offer applies.
 *
 * Order of preference:
 * <ol>
 *   <li>an explicit {@code offerToken}, matched exactly — the caller named a
 *       specific offer, which is the most specific thing they can say;</li>
 *   <li>the first offer whose base plan matches {@code planIdentifier};</li>
 *   <li>the first offer the store returned.</li>
 * </ol>
 *
 * An {@code offerToken} that matches nothing is reported rather than
 * silently falling through to (2) or (3): it means the caller asked for an
 * offer that is not on offer — usually a token cached past its query — and
 * charging a different price than the one they named is the worse outcome.
 * This mirrors how the one-time purchase path already treats an unknown
 * token.
 *
 * Extracted from the plugin so the choice is unit-testable without real
 * Billing objects, the same way {@link PurchaseActionDecider} is.
 */
public final class SubscriptionOfferSelector {

    private SubscriptionOfferSelector() {}

    /** The fields of an offer this choice depends on. */
    interface Offer {
        String getBasePlanId();

        String getOfferToken();
    }

    /** Either the chosen offer, or the reason there is none. */
    static final class Selection {

        private final Offer offer;
        private final boolean offerTokenNotFound;

        private Selection(Offer offer, boolean offerTokenNotFound) {
            this.offer = offer;
            this.offerTokenNotFound = offerTokenNotFound;
        }

        static Selection of(Offer offer) {
            return new Selection(offer, false);
        }

        static Selection offerTokenNotFound() {
            return new Selection(null, true);
        }

        static Selection none() {
            return new Selection(null, false);
        }

        Offer offer() {
            return offer;
        }

        /** True when an explicit offerToken was given and matched nothing. */
        boolean isOfferTokenNotFound() {
            return offerTokenNotFound;
        }
    }

    static Selection select(List<Offer> offers, String planIdentifier, String offerToken) {
        if (offers == null || offers.isEmpty()) {
            return Selection.none();
        }

        if (offerToken != null && !offerToken.isEmpty()) {
            for (Offer offer : offers) {
                if (offerToken.equals(offer.getOfferToken())) {
                    return Selection.of(offer);
                }
            }
            return Selection.offerTokenNotFound();
        }

        if (planIdentifier != null && !planIdentifier.isEmpty()) {
            for (Offer offer : offers) {
                if (planIdentifier.equals(offer.getBasePlanId())) {
                    return Selection.of(offer);
                }
            }
        }

        return Selection.of(offers.get(0));
    }

    /** Adapt Play's own offer details to {@link Offer}. */
    static List<Offer> adapt(List<ProductDetails.SubscriptionOfferDetails> offerDetailsList) {
        List<Offer> offers = new ArrayList<>();
        if (offerDetailsList == null) {
            return offers;
        }
        for (ProductDetails.SubscriptionOfferDetails offerDetails : offerDetailsList) {
            offers.add(new SubscriptionOfferDetailsAdapter(offerDetails));
        }
        return offers;
    }

    /** The chosen {@link Offer} back as Play's own type. */
    static ProductDetails.SubscriptionOfferDetails unwrap(Offer offer) {
        return offer instanceof SubscriptionOfferDetailsAdapter ? ((SubscriptionOfferDetailsAdapter) offer).details : null;
    }

    private static final class SubscriptionOfferDetailsAdapter implements Offer {

        private final ProductDetails.SubscriptionOfferDetails details;

        SubscriptionOfferDetailsAdapter(ProductDetails.SubscriptionOfferDetails details) {
            this.details = details;
        }

        @Override
        public String getBasePlanId() {
            return details.getBasePlanId();
        }

        @Override
        public String getOfferToken() {
            return details.getOfferToken();
        }
    }
}
