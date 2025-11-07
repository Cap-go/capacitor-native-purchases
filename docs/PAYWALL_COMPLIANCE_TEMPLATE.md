# Paywall Compliance Template

This template shows how to present your purchase call-to-action with the required legal links Apple expects to see during app review. Adapt it to match your Capacitor application's UI framework.

## Key Requirements

- Pull product metadata (title, price, billing period) from StoreKit using `NativePurchases` — never hardcode it.
- Place the primary purchase button alongside disclosure text that references your Terms of Service and Privacy Policy links.
- Reuse the same Terms of Service and Privacy Policy URLs you publish in App Store Connect.

## Example (Ionic React)

```tsx
import { useEffect, useState } from "react";
import { NativePurchases, PURCHASE_TYPE, Product } from "@capgo/native-purchases";
import { IonButton, IonContent, IonHeader, IonPage, IonText, IonTitle, IonToolbar } from "@ionic/react";

const TERMS_URL = "https://capgo.app/terms";
const PRIVACY_URL = "https://capgo.app/privacy";
const MONTHLY_PRODUCT_ID = "com.example.app.pro.monthly";

export function PaywallScreen() {
  const [product, setProduct] = useState<Product | null>(null);
  const [isPurchasing, setIsPurchasing] = useState(false);

  useEffect(() => {
    NativePurchases.getProduct({
      productIdentifier: MONTHLY_PRODUCT_ID,
      productType: PURCHASE_TYPE.SUBS,
    })
      .then(({ product }) => setProduct(product))
      .catch((error) => console.error("Failed to load product", error));
  }, []);

  async function handlePurchase() {
    if (!product) {
      return;
    }
    try {
      setIsPurchasing(true);
      await NativePurchases.purchaseProduct({
        productIdentifier: product.identifier,
        productType: PURCHASE_TYPE.SUBS,
        planIdentifier: "monthly-plan",
      });
      // TODO: unlock premium content
    } catch (error) {
      console.error("Purchase failed", error);
    } finally {
      setIsPurchasing(false);
    }
  }

  return (
    <IonPage>
      <IonHeader>
        <IonToolbar>
          <IonTitle>Upgrade to Pro</IonTitle>
        </IonToolbar>
      </IonHeader>
      <IonContent className="ion-padding">
        <IonText>
          <h2>{product?.title ?? "Loading product..."}</h2>
          <p>{product?.description ?? "Fetching the latest price"}</p>
        </IonText>

        <IonButton expand="block" disabled={!product || isPurchasing} onClick={handlePurchase}>
          {product ? `Continue for ${product.priceString}` : "Loading"}
        </IonButton>

        <p className="legal">
          By continuing, you agree to our
          {" "}
          <a href={TERMS_URL} target="_blank" rel="noopener noreferrer">
            Terms of Service
          </a>
          {" "}
          and
          {" "}
          <a href={PRIVACY_URL} target="_blank" rel="noopener noreferrer">
            Privacy Policy
          </a>.
        </p>
      </IonContent>
    </IonPage>
  );
}
```

> Replace `MONTHLY_PRODUCT_ID`, `planIdentifier`, and the legal URLs with the values that match your product setup.

### Styling notes

- Ensure the legal text remains visible on all screen sizes (avoid placing it behind scrollable dialogs).
- If you use a different UI toolkit, preserve the same structure: headline, dynamic pricing, purchase button, and legal links.
- Mirror this page in any alternate purchase funnels (web views, modal variants) so reviewers can find the required information everywhere a purchase is offered.

### Linking in App Store Connect

Add the same Terms of Service and Privacy Policy URLs to **App Store Connect → App Information → Additional Information**. Apple verifies that the URLs in your marketing metadata match what you display in-app.
