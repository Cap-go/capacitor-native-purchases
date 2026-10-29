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
 * An index is returned rather than an offer, so the caller indexes back into
 * Play's own list. The {@link Offer} seam exists only so the choice can be
 * unit-tested without constructing Billing objects, the same way
 * {@link PurchaseActionDecider} can be; handing the wrapper back would make
 * the caller unwrap to reach something it already holds.
 */
public final class SubscriptionOfferSelector {

    /** No offer to use — see {@link #selectIndex}. */
    static final int NOT_FOUND = -1;

    private SubscriptionOfferSelector() {}

    /** The fields of an offer this choice depends on. */
    interface Offer {
        String getBasePlanId();

        String getOfferToken();
    }

    /**
     * The index of the offer to purchase, or {@link #NOT_FOUND}.
     *
     * {@code NOT_FOUND} means either that there was nothing to choose from,
     * or that an explicit {@code offerToken} matched none of the offers. The
     * caller tells those apart by checking the list before calling, because
     * it is the one that can name the product that had no offers.
     */
    static int selectIndex(List<Offer> offers, String planIdentifier, String offerToken) {
        if (offers == null || offers.isEmpty()) {
            return NOT_FOUND;
        }

        if (isPresent(offerToken)) {
            for (int i = 0; i < offers.size(); i++) {
                if (offerToken.equals(offers.get(i).getOfferToken())) {
                    return i;
                }
            }
            return NOT_FOUND;
        }

        if (isPresent(planIdentifier)) {
            for (int i = 0; i < offers.size(); i++) {
                if (planIdentifier.equals(offers.get(i).getBasePlanId())) {
                    return i;
                }
            }
        }

        return 0;
    }

    /**
     * Whether a subscription purchase named an offer at all.
     *
     * Either value is enough on its own: an {@code offerToken} names one
     * specific offer, and a {@code planIdentifier} names the base plan whose
     * first offer {@link #selectIndex} then takes. Only when both are absent
     * has the caller said nothing to go on, and such a purchase is rejected
     * rather than charged whichever offer the store happened to return first.
     *
     * Saying something is not the same as saying something that matches: a
     * {@code planIdentifier} matching no base plan still falls back to the
     * first offer, as it did before this was checked.
     */
    static boolean canSelect(String planIdentifier, String offerToken) {
        return isPresent(offerToken) || isPresent(planIdentifier);
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isEmpty();
    }

    /** Adapt Play's own offer details to {@link Offer}. */
    static List<Offer> adapt(List<ProductDetails.SubscriptionOfferDetails> offerDetailsList) {
        if (offerDetailsList == null) {
            return new ArrayList<>();
        }
        List<Offer> offers = new ArrayList<>(offerDetailsList.size());
        for (ProductDetails.SubscriptionOfferDetails offerDetails : offerDetailsList) {
            offers.add(new SubscriptionOfferDetailsAdapter(offerDetails));
        }
        return offers;
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
