# native-purchases
 <a href="https://capgo.app/"><img src='https://raw.githubusercontent.com/Cap-go/capgo/main/assets/capgo_banner.png' alt='Capgo - Instant updates for capacitor'/></a>

<div align="center">
  <h2><a href="https://capgo.app/?ref=plugin_native_purchases"> ➡️ Get Instant updates for your App with Capgo</a></h2>
  <h2><a href="https://capgo.app/consulting/?ref=plugin_native_purchases"> Missing a feature? We’ll build the plugin for you 💪</a></h2>
</div>

## In-app Purchases Made Easy

This plugin allows you to implement in-app purchases and subscriptions in your Capacitor app using native APIs.

## Why Native Purchases?

The only **free**, **battle-tested** in-app purchase plugin for Capacitor with full feature parity:

- **StoreKit 2 (iOS)** - Uses Apple's latest purchase APIs for iOS 15+
- **Google Play Billing 7.x (Android)** - Implements the newest billing library
- **Complete feature set** - In-app products AND subscriptions with base plans
- **Same JavaScript API** - Compatible interface with paid alternatives
- **Comprehensive validation** - Built-in receipt/token validation examples
- **Modern package management** - Supports both Swift Package Manager (SPM) and CocoaPods (SPM-ready for Capacitor 8)
- **Production-ready** - Extensive documentation, testing guides, refund handling

Perfect for apps monetizing through one-time purchases or recurring subscriptions.

## Documentation

The most complete doc is available here: https://capgo.app/docs/plugins/native-purchases/

## Compatibility

| Plugin version | Capacitor compatibility | Maintained |
| -------------- | ----------------------- | ---------- |
| v8.\*.\*       | v8.\*.\*                | ✅          |
| v7.\*.\*       | v7.\*.\*                | On demand   |
| v6.\*.\*       | v6.\*.\*                | ❌          |
| v5.\*.\*       | v5.\*.\*                | ❌          |

> **Note:** The major version of this plugin follows the major version of Capacitor. Use the version that matches your Capacitor installation (e.g., plugin v8 for Capacitor 8). Only the latest major version is actively maintained.

## Install

```bash
npm install @capgo/native-purchases
npx cap sync
```

## 📚 Testing Guides

Complete visual testing guides for both platforms:

| Platform | Guide | Content |
|----------|-------|---------|
| 🍎 **iOS** | **[iOS Testing Guide](./docs/iOS_TESTING_GUIDE.md)** | StoreKit Local Testing, Sandbox Testing, Developer Mode setup |
| 🤖 **Android** | **[Android Testing Guide](./docs/ANDROID_TESTING_GUIDE.md)** | Internal Testing, License Testing, Internal App Sharing |

> 💡 **Quick Start**: Choose **StoreKit Local Testing** for iOS or **Internal Testing** for Android for the fastest development experience.

## Android

Add this to manifest

```xml
<uses-permission android:name="com.android.vending.BILLING" />
```

### Testing with Google Play Console

> 📖 **[Complete Android Testing Guide](./docs/ANDROID_TESTING_GUIDE.md)** - Comprehensive guide covering Internal Testing, License Testing, and Internal App Sharing methods with step-by-step instructions, troubleshooting, and best practices.

For testing in-app purchases on Android:

1. Upload your app to Google Play Console (internal testing track is sufficient)
2. Create test accounts in Google Play Console:
   - Go to Google Play Console
   - Navigate to "Setup" > "License testing"
   - Add Gmail accounts to "License testers" list
3. Install the app from Google Play Store on a device signed in with a test account
4. Test purchases will be free and won't charge real money

## iOS

Add the "In-App Purchase" capability to your Xcode project:

1. Open your project in Xcode
2. Select your app target
3. Go to "Signing & Capabilities" tab
4. Click the "+" button to add a capability
5. Search for and add "In-App Purchase"

> ⚠️ **App Store Requirement**: You MUST display product names and prices using data from the plugin (`product.title`, `product.priceString`). Hardcoded values will cause App Store rejection.

> 📖 **[Complete iOS Testing Guide](./docs/iOS_TESTING_GUIDE.md)** - Comprehensive guide covering both Sandbox and StoreKit local testing methods with step-by-step instructions, troubleshooting, and best practices.

### Testing with Sandbox

For testing in-app purchases on iOS:

1. Create a sandbox test user in App Store Connect:
   - Go to App Store Connect
   - Navigate to "Users and Access" > "Sandbox Testers"
   - Create a new sandbox tester account
2. On your iOS device, sign out of your regular Apple ID in Settings > App Store
3. Install and run your app
4. When prompted for Apple ID during purchase testing, use your sandbox account credentials

## Usage

Import the plugin in your TypeScript file:

```typescript
import { NativePurchases, PURCHASE_TYPE } from '@capgo/native-purchases';
```

### ⚠️ Important: In-App vs Subscription Purchases

There are two types of purchases with different requirements:

| Purchase Type | productType | planIdentifier | Use Case |
|---------------|-------------|----------------|----------|
| **In-App Purchase** | `PURCHASE_TYPE.INAPP` | ❌ Not needed | One-time purchases (premium features, remove ads, etc.) |
| **Subscription** | `PURCHASE_TYPE.SUBS` | ✅ **REQUIRED (Android only)** | Recurring purchases (monthly/yearly subscriptions) |

**Key Rules:**
- ✅ **In-App Products**: Use `productType: PURCHASE_TYPE.INAPP`, no `planIdentifier` needed on any platform
- ✅ **Subscriptions on Android**: Must use `productType: PURCHASE_TYPE.SUBS` AND `planIdentifier: "your-plan-id"` (the Base Plan ID from Google Play Console)
- ✅ **Subscriptions on iOS**: Use `productType: PURCHASE_TYPE.SUBS`, `planIdentifier` is optional and ignored
- ❌ **Missing planIdentifier** for Android subscriptions will cause purchase failures

**About planIdentifier (Android-specific):**
The `planIdentifier` parameter is **only required for Android subscriptions**. It should be set to the Base Plan ID that you configure in the Google Play Console when creating your subscription product. For example, if you create a monthly subscription with base plan ID "monthly-plan" in Google Play Console, you would use `planIdentifier: "monthly-plan"` when purchasing that subscription.

iOS does not use this parameter - subscriptions on iOS only require the product identifier.

### Complete Example: Get Product Info and Purchase

Here's a complete example showing how to get product information and make purchases for both in-app products and subscriptions:

```typescript
import { NativePurchases, PURCHASE_TYPE } from '@capgo/native-purchases';

class PurchaseManager {
  // In-app product (one-time purchase)
  private premiumProductId = 'com.yourapp.premium_features';
  
  // Subscription products (require planIdentifier on Android)
  private monthlySubId = 'com.yourapp.premium.monthly';
  private monthlyPlanId = 'monthly-plan';  // Base plan ID from Google Play Console (Android only)

  private yearlySubId = 'com.yourapp.premium.yearly';
  private yearlyPlanId = 'yearly-plan';    // Base plan ID from Google Play Console (Android only)

  async initializeStore() {
    try {
      // 1. Check if billing is supported
      const { isBillingSupported } = await NativePurchases.isBillingSupported();
      if (!isBillingSupported) {
        throw new Error('Billing not supported on this device');
      }

      // 2. Get product information (REQUIRED by Apple - no hardcoded prices!)
      await this.loadProducts();
      
    } catch (error) {
      console.error('Store initialization failed:', error);
    }
  }

  async loadProducts() {
    try {
      // Load in-app products
      const { product: premiumProduct } = await NativePurchases.getProduct({
        productIdentifier: this.premiumProductId,
        productType: PURCHASE_TYPE.INAPP
      });
      
      // Load subscription products  
      const { products: subscriptions } = await NativePurchases.getProducts({
        productIdentifiers: [this.monthlySubId, this.yearlySubId],
        productType: PURCHASE_TYPE.SUBS
      });
      
      console.log('Products loaded:', {
        premium: premiumProduct,
        subscriptions: subscriptions
      });
      
      // Display products with dynamic info from store
      this.displayProducts(premiumProduct, subscriptions);
      
    } catch (error) {
      console.error('Failed to load products:', error);
      throw error;
    }
  }

  displayProducts(premiumProduct: any, subscriptions: any[]) {
    // ✅ CORRECT: Use dynamic product info (required by Apple)
    
    // Display one-time purchase
    document.getElementById('premium-title')!.textContent = premiumProduct.title;
    document.getElementById('premium-price')!.textContent = premiumProduct.priceString;
    
    // Display subscriptions
    subscriptions.forEach(sub => {
      const element = document.getElementById(`sub-${sub.identifier}`);
      if (element) {
        element.textContent = `${sub.title} - ${sub.priceString}`;
      }
    });
    
    // ❌ WRONG: Never hardcode prices - Apple will reject your app
    // document.getElementById('premium-price')!.textContent = '$9.99';
  }

  // Purchase one-time product (no planIdentifier needed)
  async purchaseInAppProduct() {
    try {
      console.log('Starting in-app purchase...');
      
      const result = await NativePurchases.purchaseProduct({
        productIdentifier: this.premiumProductId,
        productType: PURCHASE_TYPE.INAPP,
        quantity: 1
      });
      
      console.log('In-app purchase successful!', result.transactionId);
      await this.handleSuccessfulPurchase(result.transactionId, 'premium');
      
    } catch (error) {
      console.error('In-app purchase failed:', error);
      this.handlePurchaseError(error);
    }
  }

  // Purchase subscription (planIdentifier REQUIRED for Android)
  async purchaseMonthlySubscription() {
    try {
      console.log('Starting subscription purchase...');

      const result = await NativePurchases.purchaseProduct({
        productIdentifier: this.monthlySubId,
        planIdentifier: this.monthlyPlanId,    // REQUIRED for Android subscriptions, ignored on iOS
        productType: PURCHASE_TYPE.SUBS,       // REQUIRED for subscriptions
        quantity: 1
      });

      console.log('Subscription purchase successful!', result.transactionId);
      await this.handleSuccessfulPurchase(result.transactionId, 'monthly');

    } catch (error) {
      console.error('Subscription purchase failed:', error);
      this.handlePurchaseError(error);
    }
  }

  // Purchase yearly subscription (planIdentifier REQUIRED for Android)
  async purchaseYearlySubscription() {
    try {
      console.log('Starting yearly subscription purchase...');

      const result = await NativePurchases.purchaseProduct({
        productIdentifier: this.yearlySubId,
        planIdentifier: this.yearlyPlanId,     // REQUIRED for Android subscriptions, ignored on iOS
        productType: PURCHASE_TYPE.SUBS,       // REQUIRED for subscriptions
        quantity: 1
      });

      console.log('Yearly subscription successful!', result.transactionId);
      await this.handleSuccessfulPurchase(result.transactionId, 'yearly');

    } catch (error) {
      console.error('Yearly subscription failed:', error);
      this.handlePurchaseError(error);
    }
  }

  async handleSuccessfulPurchase(transactionId: string, purchaseType: string) {
    // 1. Grant access to premium features
    localStorage.setItem('premium_active', 'true');
    localStorage.setItem('purchase_type', purchaseType);
    
    // 2. Update UI
    const statusText = purchaseType === 'premium' ? 'Premium Unlocked' : `${purchaseType} Subscription Active`;
    document.getElementById('subscription-status')!.textContent = statusText;
    
    // 3. Optional: Verify purchase on your server
    await this.verifyPurchaseOnServer(transactionId);
  }

  handlePurchaseError(error: any) {
    // Handle different error scenarios
    if (error.message.includes('User cancelled')) {
      console.log('User cancelled the purchase');
    } else if (error.message.includes('Network')) {
      alert('Network error. Please check your connection and try again.');
    } else {
      alert('Purchase failed. Please try again.');
    }
  }

  async verifyPurchaseOnServer(transactionId: string) {
    try {
      // Send transaction to your server for verification
      const response = await fetch('/api/verify-purchase', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ transactionId })
      });
      
      const result = await response.json();
      console.log('Server verification:', result);
    } catch (error) {
      console.error('Server verification failed:', error);
    }
  }

  async restorePurchases() {
    try {
      await NativePurchases.restorePurchases();
      console.log('Purchases restored successfully');

      // Check if user has active premium after restore
      const product = await this.getProductInfo();
      // Update UI based on restored purchases

    } catch (error) {
      console.error('Failed to restore purchases:', error);
    }
  }

  async openSubscriptionManagement() {
    try {
      await NativePurchases.manageSubscriptions();
      console.log('Opened subscription management page');
    } catch (error) {
      console.error('Failed to open subscription management:', error);
    }
  }
}

// Usage in your app
const purchaseManager = new PurchaseManager();

// Initialize when app starts
purchaseManager.initializeStore();

// Attach to UI buttons
document.getElementById('buy-premium-button')?.addEventListener('click', () => {
  purchaseManager.purchaseInAppProduct();
});

document.getElementById('buy-monthly-button')?.addEventListener('click', () => {
  purchaseManager.purchaseMonthlySubscription();
});

document.getElementById('buy-yearly-button')?.addEventListener('click', () => {
  purchaseManager.purchaseYearlySubscription();
});

document.getElementById('restore-button')?.addEventListener('click', () => {
  purchaseManager.restorePurchases();
});

document.getElementById('manage-subscriptions-button')?.addEventListener('click', () => {
  purchaseManager.openSubscriptionManagement();
});
```

### Quick Examples

#### Get Multiple Products

```typescript
import { NativePurchases, PURCHASE_TYPE } from '@capgo/native-purchases';

// Get in-app products (one-time purchases)
const getInAppProducts = async () => {
  try {
    const { products } = await NativePurchases.getProducts({
      productIdentifiers: [
        'com.yourapp.premium_features',
        'com.yourapp.remove_ads',
        'com.yourapp.extra_content'
      ],
      productType: PURCHASE_TYPE.INAPP
    });
    
    products.forEach(product => {
      console.log(`${product.title}: ${product.priceString}`);
    });
    
    return products;
  } catch (error) {
    console.error('Error getting in-app products:', error);
  }
};

// Get subscription products
const getSubscriptions = async () => {
  try {
    const { products } = await NativePurchases.getProducts({
      productIdentifiers: [
        'com.yourapp.premium.monthly',
        'com.yourapp.premium.yearly'
      ],
      productType: PURCHASE_TYPE.SUBS
    });
    
    products.forEach(product => {
      console.log(`${product.title}: ${product.priceString}`);
    });
    
    return products;
  } catch (error) {
    console.error('Error getting subscriptions:', error);
  }
};
```

#### Simple Purchase Flow

```typescript
import { NativePurchases, PURCHASE_TYPE } from '@capgo/native-purchases';

// Simple one-time purchase (in-app product)
const buyInAppProduct = async () => {
  try {
    // Check billing support
    const { isBillingSupported } = await NativePurchases.isBillingSupported();
    if (!isBillingSupported) {
      alert('Purchases not supported on this device');
      return;
    }

    // Get product (for price display)
    const { product } = await NativePurchases.getProduct({
      productIdentifier: 'com.yourapp.premium_features',
      productType: PURCHASE_TYPE.INAPP
    });

    // Confirm with user (showing real price from store)
    const confirmed = confirm(`Purchase ${product.title} for ${product.priceString}?`);
    if (!confirmed) return;

    // Make purchase (no planIdentifier needed for in-app)
    const result = await NativePurchases.purchaseProduct({
      productIdentifier: 'com.yourapp.premium_features',
      productType: PURCHASE_TYPE.INAPP,
      quantity: 1,
      appAccountToken: uuidToken // Optional: User identifier in UUID format
                                 // iOS: Must be valid UUID (required by StoreKit 2)
                                 // Android: UUID works, or any obfuscated string (max 64 chars)
                                 // RECOMMENDED: Use UUID v5 for cross-platform compatibility
                                 // Example: uuidv5(userId, APP_NAMESPACE)
    });

    alert('Purchase successful! Transaction ID: ' + result.transactionId);
    
    // Access the full receipt data for backend validation
    if (result.receipt) {
      // iOS: Base64-encoded StoreKit receipt - send this to your backend
      console.log('iOS Receipt (base64):', result.receipt);
      await validateReceipt(result.receipt);
    }
    
    if (result.jwsRepresentation) {
      // iOS: StoreKit 2 JWS representation - alternative to receipt
      console.log('iOS JWS:', result.jwsRepresentation);
    }
    
    if (result.purchaseToken) {
      // Android: Purchase token - send this to your backend
      console.log('Android Purchase Token:', result.purchaseToken);
      await validatePurchaseToken(result.purchaseToken, result.productIdentifier);
    }
    
  } catch (error) {
    alert('Purchase failed: ' + error.message);
  }
};

// Simple subscription purchase (requires planIdentifier)
const buySubscription = async () => {
  try {
    // Check billing support
    const { isBillingSupported } = await NativePurchases.isBillingSupported();
    if (!isBillingSupported) {
      alert('Purchases not supported on this device');
      return;
    }

    // Get subscription product (for price display)
    const { product } = await NativePurchases.getProduct({
      productIdentifier: 'com.yourapp.premium.monthly',
      productType: PURCHASE_TYPE.SUBS
    });

    // Confirm with user (showing real price from store)
    const confirmed = confirm(`Subscribe to ${product.title} for ${product.priceString}?`);
    if (!confirmed) return;

    // Make subscription purchase (planIdentifier REQUIRED for Android, ignored on iOS)
    const result = await NativePurchases.purchaseProduct({
      productIdentifier: 'com.yourapp.premium.monthly',
      planIdentifier: 'monthly-plan',           // REQUIRED for Android subscriptions, ignored on iOS
      productType: PURCHASE_TYPE.SUBS,          // REQUIRED for subscriptions
      quantity: 1,
      appAccountToken: uuidToken                // Optional: User identifier in UUID format
                                                // iOS: Must be valid UUID (required by StoreKit 2)
                                                // Android: UUID works, or any obfuscated string (max 64 chars)
                                                // RECOMMENDED: Use UUID v5 for cross-platform compatibility
                                                // Example: uuidv5(userId, APP_NAMESPACE)
    });

    alert('Subscription successful! Transaction ID: ' + result.transactionId);
    
    // Access the full receipt data for backend validation
    if (result.receipt) {
      // iOS: Base64-encoded StoreKit receipt - send this to your backend
      console.log('iOS Receipt (base64):', result.receipt);
      await validateReceipt(result.receipt);
    }
    
    if (result.jwsRepresentation) {
      // iOS: StoreKit 2 JWS representation - alternative to receipt
      console.log('iOS JWS:', result.jwsRepresentation);
    }
    
    if (result.purchaseToken) {
      // Android: Purchase token - send this to your backend
      console.log('Android Purchase Token:', result.purchaseToken);
      await validatePurchaseToken(result.purchaseToken, result.productIdentifier);
    }
    
  } catch (error) {
    alert('Subscription failed: ' + error.message);
  }
};
```

### Check if billing is supported

Before attempting to make purchases, check if billing is supported on the device:
We only support Storekit 2 on iOS (iOS 15+) and google play on Android

```typescript
const checkBillingSupport = async () => {
  try {
    const { isBillingSupported } = await NativePurchases.isBillingSupported();
    if (isBillingSupported) {
      console.log('Billing is supported on this device');
    } else {
      console.log('Billing is not supported on this device');
    }
  } catch (error) {
    console.error('Error checking billing support:', error);
  }
};
```

### Manage Subscriptions

Allow users to manage their subscriptions directly from your app. This opens the platform's native subscription management page:

```typescript
import { NativePurchases } from '@capgo/native-purchases';

const openSubscriptionSettings = async () => {
  try {
    await NativePurchases.manageSubscriptions();
    // On iOS: Opens the App Store subscription management page
    // On Android: Opens the Google Play subscription management page
  } catch (error) {
    console.error('Error opening subscription management:', error);
  }
};
```

This is particularly useful for:
- Allowing users to cancel or modify their subscriptions
- Viewing subscription renewal dates
- Changing subscription plans
- Managing billing information

### Using appAccountToken for Fraud Detection and User Linking

The `appAccountToken` parameter is an optional but highly recommended security feature that helps both you and the platform stores detect fraud and link purchases to specific users in your app.

#### What is appAccountToken?

An identifier (max 64 characters) that uniquely associates transactions with user accounts in your app. It serves two main purposes:

1. **Fraud Detection**: Google Play and Apple use this to detect irregular activity, such as many devices making purchases on the same account within a brief timeframe
2. **User Linking**: Links purchases to specific in-game characters, avatars, or in-app profiles that initiated the purchase

#### Platform-Specific Requirements

**IMPORTANT: iOS and Android have different format requirements:**

| Platform | Format Requirement | Maps To |
|----------|-------------------|---------|
| **iOS** | **Must be a valid UUID** (e.g., `"550e8400-e29b-41d4-a716-446655440000"`) | Apple StoreKit 2's `appAccountToken` parameter |
| **Android** | Any obfuscated string (max 64 chars) | Google Play's `ObfuscatedAccountId` |

**iOS Specific:**
- Apple's StoreKit 2 requires the `appAccountToken` to be in UUID format
- The plugin validates and converts the string to UUID before passing to StoreKit
- If the format is invalid, the token will be ignored

**Android Specific:**
- Google recommends using encryption or one-way hash
- Storing PII in cleartext will result in purchases being blocked by Google Play

#### Critical Security Requirements

**DO NOT use Personally Identifiable Information (PII) in cleartext:**
- ❌ WRONG: `appAccountToken: 'user@example.com'`
- ❌ WRONG: `appAccountToken: 'john.doe'`
- ✅ CORRECT (iOS & Android): `appAccountToken: uuidv5(userId, NAMESPACE)`
- ✅ CORRECT (Android only): `appAccountToken: hash(userId).substring(0, 64)`

**For cross-platform compatibility, using UUID format is recommended for both platforms.**

#### Implementation Example

```typescript
// RECOMMENDED: Use UUID v5 for cross-platform compatibility (works on both iOS and Android)
import { v5 as uuidv5 } from 'uuid'; // npm install uuid

// Generate a deterministic UUID from user ID
function generateAppAccountToken(userId: string): string {
  // Use a consistent namespace UUID for your app (generate once and keep constant)
  const APP_NAMESPACE = '6ba7b810-9dad-11d1-80b4-00c04fd430c8';

  // Generate deterministic UUID - same userId always produces same UUID
  const uuid = uuidv5(userId, APP_NAMESPACE);

  return uuid; // e.g., "550e8400-e29b-41d4-a716-446655440000"
}

// ALTERNATIVE: For Android-only apps (SHA-256 hash)
function generateAppAccountTokenAndroidOnly(userId: string): string {
  // This works on Android but will be ignored on iOS (not UUID format)
  const hash = crypto.createHash('sha256')
    .update(userId)
    .digest('hex')
    .substring(0, 64); // Ensure max 64 chars

  return hash;
}

// ALTERNATIVE: HMAC with secret key for Android-only apps
function generateSecureAppAccountTokenAndroidOnly(userId: string, secretKey: string): string {
  // This works on Android but will be ignored on iOS (not UUID format)
  const hmac = crypto.createHmac('sha256', secretKey)
    .update(userId)
    .digest('hex')
    .substring(0, 64);

  return hmac;
}

// Use in your purchase flow (cross-platform)
const userId = 'user-12345'; // Your internal user ID
const appAccountToken = generateAppAccountToken(userId);

await NativePurchases.purchaseProduct({
  productIdentifier: 'com.yourapp.premium',
  productType: PURCHASE_TYPE.INAPP,
  appAccountToken: appAccountToken // UUID format works on both iOS and Android
});

// Later, retrieve purchases for this user
const { purchases } = await NativePurchases.getPurchases({
  appAccountToken: appAccountToken
});
```

**Why UUID v5 is Recommended:**
- ✅ Works on both iOS (required) and Android (accepted)
- ✅ Deterministic: Same user ID always produces the same UUID
- ✅ Secure: No PII exposure
- ✅ Standard format: Widely supported
- ✅ Reversible mapping: You can store the mapping in your backend

#### Best Practices

1. **Use UUID v5 for cross-platform apps** - Works on both iOS (required) and Android (accepted)
2. **Keep your namespace UUID constant** - Generate once and hardcode it in your app
3. **Store the mapping** - Keep a record of userId → appAccountToken in your backend for reverse lookup
4. **Use during purchase** - Include it when calling `purchaseProduct()`
5. **Use for queries** - Use it when calling `getPurchases()` to filter by user
6. **Deterministic generation** - Same user should always get the same token
7. **Max 64 characters** - UUID format is 36 characters, well within the limit

#### Benefits

- **Fraud Prevention**: Platforms can detect suspicious patterns
- **Multi-device Support**: Link purchases across devices for the same user
- **User Management**: Query purchases for specific users
- **Analytics**: Better insights into user purchasing behavior

## Understanding Transaction Properties

When you inspect purchases using `getPurchases()` or `restorePurchases()`, you receive an array of `Transaction` objects. Understanding which properties are available and reliable for different scenarios is crucial for proper implementation.

### Transaction Properties by Platform & Product Type

Here's a comprehensive breakdown of which properties you can expect and rely on:

| Property | iOS IAP | iOS Subscription | Android IAP | Android Subscription | Notes |
|----------|---------|------------------|-------------|---------------------|-------|
| `transactionId` | ✅ Always | ✅ Always | ✅ Always | ✅ Always | Primary identifier for the transaction |
| `receipt` | ✅ Always | ✅ Always | ❌ Never | ❌ Never | iOS only - base64 receipt for validation |
| `productIdentifier` | ✅ Always | ✅ Always | ✅ Always | ✅ Always | Product ID purchased |
| `purchaseDate` | ✅ Always | ✅ Always | ✅ Always | ✅ Always | ISO 8601 format |
| `productType` | ✅ Always | ✅ Always | ✅ Always | ✅ Always | "inapp" or "subs" |
| `ownershipType` | ✅ Always | ✅ Always | ❌ Never | ❌ Never | **iOS only** - "purchased" or "familyShared" (iOS 15.0+, StoreKit 2) |
| `environment` | ✅ iOS 16+ | ✅ iOS 16+ | ❌ Never | ❌ Never | **iOS only** - "Sandbox", "Production", or "Xcode" (iOS 16.0+ only, not available on iOS 15) |
| `quantity` | ✅ Always | ✅ Always | ✅ Always 1 | ✅ Always 1 | iOS supports multiple, Android always 1 |
| `appAccountToken` | ✅ If provided | ✅ If provided | ✅ If provided | ✅ If provided | Set if passed during purchase |
| `isActive` | ❌ Not set | ✅ Always | ❌ Not set | ❌ Not set | **iOS subscriptions ONLY** - calculated as expiration > now |
| `willCancel` | ❌ Not set | ✅ Always | ✅ Always null | ✅ Always null | iOS: subscription renewal status; Android: always null |
| `originalPurchaseDate` | ❌ Not set | ✅ Always | ❌ Not set | ❌ Not set | **iOS subscriptions ONLY** |
| `expirationDate` | ❌ Not set | ✅ Always | ❌ Not set | ❌ Not set | **iOS subscriptions ONLY** |
| `purchaseState` | ❌ Not set | ❌ Not set | ✅ Always | ✅ Always | **Android ONLY** - "PURCHASED", "PENDING", "0" (numeric) |
| `orderId` | ❌ Not set | ❌ Not set | ✅ Always | ✅ Always | **Android ONLY** |
| `purchaseToken` | ❌ Not set | ❌ Not set | ✅ Always | ✅ Always | **Android ONLY** - for validation |
| `isAcknowledged` | ❌ Not set | ❌ Not set | ✅ Always | ✅ Always | **Android ONLY** |

### Validating Purchases: Platform-Specific Best Practices

#### iOS In-App Purchases (One-Time)

```typescript
const { purchases } = await NativePurchases.getPurchases({
  productType: PURCHASE_TYPE.INAPP
});

// Example response for iOS IAP:
// {
//   "transactionId": "2000001043762129",
//   "receipt": "base64EncodedReceiptData...",
//   "productIdentifier": "com.yourapp.premium",
//   "purchaseDate": "2025-10-28T06:03:19Z",
//   "productType": "inapp"
// }

purchases.forEach(purchase => {
  // For iOS IAP, the mere presence in the list generally indicates a valid purchase
  // However, for security, you should validate the receipt on your server

  if (purchase.productIdentifier === 'com.yourapp.premium') {
    // Option 1: Basic client-side check (not recommended for production)
    if (purchase.receipt && purchase.transactionId) {
      grantPremiumAccess();
    }

    // Option 2: Server-side validation (RECOMMENDED)
    validateReceiptOnServer(purchase.receipt).then(isValid => {
      if (isValid) {
        grantPremiumAccess();
      }
    });
  }
});
```

**Key Points for iOS IAP:**
- ✅ If a purchase appears in `getPurchases()`, it's generally valid
- ❌ `isActive` is **NOT set** for one-time IAP purchases (only for subscriptions)
- ❌ `expirationDate` and `originalPurchaseDate` are **NOT set** for IAP
- 🔒 **Always validate the receipt on your server for production apps**
- ⚠️ Refunded purchases may still appear but will fail server validation

#### Android In-App Purchases (One-Time)

```typescript
const { purchases } = await NativePurchases.getPurchases({
  productType: PURCHASE_TYPE.INAPP
});

// Example response for Android IAP:
// {
//   "transactionId": "GPA.1234-5678-9012-34567",
//   "productIdentifier": "com.yourapp.premium",
//   "purchaseDate": "2025-10-28T06:03:19Z",
//   "purchaseState": "PURCHASED",
//   "orderId": "GPA.1234-5678-9012-34567",
//   "purchaseToken": "long-token-string...",
//   "isAcknowledged": true,
//   "productType": "inapp"
// }

purchases.forEach(purchase => {
  // For Android IAP, ALWAYS check purchaseState
  const isValidPurchase =
    purchase.purchaseState === 'PURCHASED' &&
    purchase.isAcknowledged === true;

  if (purchase.productIdentifier === 'com.yourapp.premium' && isValidPurchase) {
    grantPremiumAccess();

    // For extra security, validate on server (RECOMMENDED)
    validatePurchaseTokenOnServer(purchase.purchaseToken);
  }
});
```

**Key Points for Android IAP:**
- ✅ **ALWAYS check** `purchaseState === "PURCHASED"` or `purchaseState === "1"` - this is critical
- ✅ Check `isAcknowledged === true` (this plugin auto-acknowledges)
- ❌ `isActive` is **NOT set** on Android (for either IAP or subscriptions)
- ❌ `expirationDate` and `originalPurchaseDate` are **NOT set** on Android
- 🔒 For production, validate `purchaseToken` on your server with Google Play API

#### iOS Subscriptions

```typescript
const { purchases } = await NativePurchases.getPurchases({
  productType: PURCHASE_TYPE.SUBS
});

// Example response for active iOS subscription:
// {
//   "transactionId": "2000001043762130",
//   "receipt": "base64EncodedReceiptData...",
//   "productIdentifier": "com.yourapp.premium.monthly",
//   "purchaseDate": "2025-10-28T06:03:19Z",
//   "originalPurchaseDate": "2025-09-28T06:03:19Z",
//   "expirationDate": "2025-11-28T06:03:19Z",
//   "isActive": true,
//   "willCancel": false,
//   "productType": "subs",
//   "isTrialPeriod": false,
//   "isInIntroPricePeriod": false
// }

purchases.forEach(purchase => {
  // Check if subscription is currently active
  const isSubscriptionActive = purchase.isActive === true;

  // Alternative: Check expiration date
  const expirationDate = new Date(purchase.expirationDate);
  const isActiveByDate = expirationDate > new Date();

  // Check if user has cancelled (still active until expiration)
  const willAutoRenew = purchase.willCancel === false;

  if (isSubscriptionActive) {
    grantSubscriptionAccess();

    if (willAutoRenew) {
      console.log('Subscription will renew on', purchase.expirationDate);
    } else {
      console.log('Subscription cancelled, expires on', purchase.expirationDate);
    }
  }
});
```

**Key Points for iOS Subscriptions:**
- ✅ `isActive` is reliable for subscriptions
- ✅ `expirationDate` can be used to check validity
- ✅ `willCancel` tells you if subscription will auto-renew
- ⚠️ Even cancelled subscriptions show `isActive: true` until expiration
- 🔒 Validate receipt on server to detect refunds/revocations

#### Android Subscriptions

```typescript
const { purchases } = await NativePurchases.getPurchases({
  productType: PURCHASE_TYPE.SUBS
});

// Example response for active Android subscription:
// {
//   "transactionId": "GPA.1234-5678-9012-34568",
//   "productIdentifier": "com.yourapp.premium.monthly",
//   "purchaseDate": "2025-10-28T06:03:19Z",
//   "originalPurchaseDate": "2025-09-28T06:03:19Z",
//   "expirationDate": "2025-11-28T06:03:19Z",
//   "isActive": true,
//   "purchaseState": "PURCHASED",
//   "orderId": "GPA.1234-5678-9012-34568",
//   "purchaseToken": "long-token-string...",
//   "isAcknowledged": true,
//   "productType": "subs",
//   "isTrialPeriod": false
// }

purchases.forEach(purchase => {
  // Check if subscription is active using multiple signals
  const isActiveSubscription =
    purchase.purchaseState === 'PURCHASED' &&
    purchase.isActive === true &&
    purchase.isAcknowledged === true;

  // Alternative: Check expiration date
  const expirationDate = new Date(purchase.expirationDate);
  const isActiveByDate = expirationDate > new Date();

  if (isActiveSubscription || isActiveByDate) {
    grantSubscriptionAccess();
  }
});
```

**Key Points for Android Subscriptions:**
- ✅ Check `purchaseState === "PURCHASED"` or `purchaseState === "1"`
- ❌ `isActive` is **NOT set** on Android (even for subscriptions)
- ❌ `expirationDate` is **NOT set** on Android - must query Google Play API
- ❌ `originalPurchaseDate` is **NOT set** on Android
- ✅ `willCancel` is ALWAYS set to `null` on Android
- 🔒 For subscription status and expiration, query Google Play Developer API on your server

### Handling Refunds and Cancellations

Understanding how refunds and cancellations affect your transaction data is critical for proper access control.

#### iOS Refund Behavior

**What happens when a user requests a refund:**

1. **For In-App Purchases (IAP):**
   - The transaction **may still appear** in `getPurchases()` and `restorePurchases()`
   - `isActive` is **NOT set** for IAP purchases (only for subscriptions)
   - The receipt will **NOT disappear** from the device
   - ✅ **SOLUTION:** Validate the receipt with Apple's servers - refunded transactions will be marked as invalid

2. **For Subscriptions:**
   - The transaction **will still appear** in purchase history
   - `isActive` **will be set to `false`** (subscriptions only set this field)
   - `expirationDate` will be set to the refund date (in the past)
   - ✅ **SOLUTION:** Check `isActive === false` OR `expirationDate < now` OR validate receipt on server

**Example: Detecting iOS refunds**

```typescript
async function checkIOSPurchaseValidity(purchase: Transaction) {
  // Client-side check (not foolproof)
  if (purchase.isActive === false) {
    console.log('Purchase appears to be refunded or expired');
    return false;
  }

  // Server-side validation (RECOMMENDED)
  const validationResult = await fetch('https://your-server.com/validate-receipt', {
    method: 'POST',
    body: JSON.stringify({
      receipt: purchase.receipt,
      productId: purchase.productIdentifier
    })
  }).then(r => r.json());

  if (!validationResult.isValid || validationResult.isRefunded) {
    console.log('Receipt validation failed - purchase refunded or invalid');
    return false;
  }

  return true;
}
```

**Sandbox vs Production Behavior:**
- ✅ Refund behavior is **consistent** between sandbox and production
- ⚠️ Sandbox refunds are processed instantly, production may take hours/days
- ✅ Receipt validation works the same in both environments

#### Android Refund Behavior

**What happens when a user requests a refund:**

1. **For In-App Purchases (IAP):**
   - The transaction **typically disappears entirely** from `getPurchases()`
   - Google Play removes refunded purchases from the purchase history
   - No receipt or transaction will be returned
   - ✅ **SOLUTION:** If a previously-seen purchase is no longer in the list, it was likely refunded

2. **For Subscriptions:**
   - The transaction **may disappear** OR
   - `isActive` will be set to `false`
   - `purchaseState` may be undefined or the transaction won't be returned at all
   - ✅ **SOLUTION:** Track purchases on your server and listen for Google Play real-time developer notifications

**Example: Detecting Android refunds**

```typescript
// Store previously seen purchases in local storage or your database
const previousPurchases = getPreviouslyStoredPurchases();

const { purchases } = await NativePurchases.getPurchases({
  productType: PURCHASE_TYPE.INAPP
});

// Check for missing purchases (likely refunded)
previousPurchases.forEach(oldPurchase => {
  const stillExists = purchases.find(
    p => p.transactionId === oldPurchase.transactionId
  );

  if (!stillExists) {
    console.log(`Purchase ${oldPurchase.productIdentifier} no longer exists - likely refunded`);
    revokePremiumAccess(oldPurchase.productIdentifier);
  }
});

// Check current purchases for validity
purchases.forEach(purchase => {
  const isValid =
    purchase.purchaseState === 'PURCHASED' &&
    purchase.isAcknowledged === true;

  if (!isValid) {
    console.log('Invalid purchase state detected');
    // Don't grant access
  }
});

// Store current purchases for next comparison
storePurchases(purchases);
```

**Sandbox vs Production Behavior:**
- ⚠️ Sandbox test accounts can make unlimited "purchases" without payment
- ⚠️ Sandbox refunds are **instant** - purchase disappears immediately
- ⚠️ Production refunds may take **several hours** before purchase disappears
- ✅ Testing refunds in production requires real money and real refund requests

**IMPORTANT: Without Server-Side Validation:**

If you're **not using a backend validator** (not recommended for production), here's what to expect:

| Scenario | iOS Behavior | Android Behavior |
|----------|-------------|------------------|
| User requests IAP refund | Transaction may still appear in `restorePurchases()`, check `isActive` | Transaction disappears from `getPurchases()` |
| User cancels subscription | `willCancel: true`, still active until expiration | Transaction remains, check `isActive` and `expirationDate` |
| Subscription expires naturally | `isActive: false`, `expirationDate` in past | Transaction disappears OR `isActive: false` |
| User refunds subscription | Transaction remains with `isActive: false` | Transaction may disappear |

**RECOMMENDATION: Always implement server-side validation**
- Listen to Apple's App Store Server Notifications (iOS)
- Listen to Google Play Real-Time Developer Notifications (Android)
- Validate receipts/tokens on your server before granting access
- See the [Backend Validation](#backend-validation) section for implementation

### Sandbox vs Production Differences

#### iOS: Sandbox vs Production

**Similarities:**
- ✅ Transaction structure is identical
- ✅ All properties return the same data format
- ✅ `receipt` validation works (use sandbox Apple endpoint)
- ✅ Refund behavior is consistent

**Differences:**

| Aspect | Sandbox | Production |
|--------|---------|-----------|
| Payment processing | Instant, no real money | Real payment, takes seconds |
| Receipt validation endpoint | `https://sandbox.itunes.apple.com/verifyReceipt` | `https://buy.itunes.apple.com/verifyReceipt` |
| Subscription duration | Compressed (1 week = 5 minutes) | Real duration (1 week = 7 days) |
| Refund processing | Instant (via StoreKit Testing) | Takes hours/days, must contact Apple |
| Test user requirements | Sandbox Apple ID required | Real Apple ID |
| Transaction IDs | Real format, unique per test | Real format, unique per purchase |
| `receipt` data | Valid test receipt | Valid production receipt |

**Testing Refunds in Sandbox:**
1. Use StoreKit Configuration file (local testing) for instant refunds
2. Or sandbox testing with sandbox Apple ID
3. Refunds are instant and can be tested repeatedly
4. Receipt validation will show refunded status immediately

#### Android: Sandbox vs Production

**Similarities:**
- ✅ Transaction structure is identical
- ✅ All properties return the same data format
- ✅ Purchase state values are the same

**Differences:**

| Aspect | License Testing (Sandbox) | Production |
|--------|--------------------------|-----------|
| Payment processing | No payment required | Real payment required |
| Purchase token validation | Works with Google Play API | Works with Google Play API |
| Transaction IDs | Test format: `GPA.1234-...` | Real format: `GPA.1234-...` |
| Refund processing | Instant (test account only) | Takes hours, appears as purchase disappearing |
| Test user requirements | Gmail added to license testers | Real Google account |
| `purchaseState` values | Same as production | Same as sandbox |
| Refund detection | Purchase disappears immediately | Purchase disappears after hours/days |

**Testing Refunds in Android Sandbox:**
1. **License testers** can make unlimited purchases without payment
2. Refunds are **instant** - purchase disappears from `getPurchases()` immediately
3. Use **Internal Testing** track for most realistic testing
4. Real refunds in production require real purchases and real refund requests via Google Play

**Key Difference:**
- In **sandbox/test**, refunded purchases disappear instantly
- In **production**, refunded purchases may remain visible for hours before disappearing
- Always implement server-side validation with Google Play Developer API to catch refunds reliably

### Recommended Access Control Logic

Based on the above, here's the recommended approach for each platform and product type:

```typescript
import { NativePurchases, PURCHASE_TYPE, Transaction } from '@capgo/native-purchases';
import { Capacitor } from '@capacitor/core';

async function checkUserAccess(productId: string, productType: PURCHASE_TYPE): Promise<boolean> {
  try {
    const { purchases } = await NativePurchases.getPurchases({ productType });
    const purchase = purchases.find(p => p.productIdentifier === productId);

    if (!purchase) {
      return false; // No purchase found
    }

    const platform = Capacitor.getPlatform();

    if (platform === 'ios') {
      // iOS Logic
      if (productType === PURCHASE_TYPE.INAPP) {
        // For IAP: presence in list + receipt validation
        // Note: isActive is NOT set for iOS IAP
        if (!purchase.receipt) return false;

        // IMPORTANT: Validate receipt on server for production
        const isValid = await validateReceiptOnServer(purchase.receipt);
        return isValid;

      } else {
        // For subscriptions: check isActive and expiration
        // iOS subscriptions ALWAYS have isActive and expirationDate
        if (purchase.isActive === false) return false;
        if (purchase.expirationDate) {
          const expiration = new Date(purchase.expirationDate);
          if (expiration < new Date()) return false;
        }
        return true;
      }

    } else if (platform === 'android') {
      // Android Logic
      if (productType === PURCHASE_TYPE.INAPP) {
        // For IAP: check purchase state and acknowledgment
        // Note: Android does NOT set isActive, expirationDate, or originalPurchaseDate
        const isValid =
          (purchase.purchaseState === 'PURCHASED' || purchase.purchaseState === '1') &&
          purchase.isAcknowledged === true;

        if (!isValid) return false;

        // IMPORTANT: Validate purchaseToken on server for production
        await validatePurchaseTokenOnServer(purchase.purchaseToken);
        return true;

      } else {
        // For subscriptions: check purchase state only
        // Android does NOT set isActive, expirationDate, or originalPurchaseDate
        // You MUST use Google Play Developer API on your server to get subscription details
        const isValidState =
          (purchase.purchaseState === 'PURCHASED' || purchase.purchaseState === '1') &&
          purchase.isAcknowledged === true;

        if (!isValidState) return false;

        // CRITICAL: Validate subscription status on server with Google Play API
        // The Purchase object doesn't include expiration dates
        const serverStatus = await validateAndGetSubscriptionStatus(purchase.purchaseToken);
        return serverStatus.isActive && serverStatus.expirationDate > new Date();
      }
    }

    return false;
  } catch (error) {
    console.error('Error checking user access:', error);
    return false;
  }
}

// Example usage
async function grantAccessBasedOnPurchase() {
  // Check for premium IAP
  const hasPremium = await checkUserAccess(
    'com.yourapp.premium',
    PURCHASE_TYPE.INAPP
  );

  // Check for active subscription
  const hasSubscription = await checkUserAccess(
    'com.yourapp.premium.monthly',
    PURCHASE_TYPE.SUBS
  );

  if (hasPremium || hasSubscription) {
    unlockPremiumFeatures();
  }
}
```

**Critical Takeaways:**
1. ✅ For **iOS IAP**: `isActive` is NOT set - validate receipt on server
2. ✅ For **iOS Subscriptions**: `isActive` and `expirationDate` ARE set - use them!
3. ✅ For **Android IAP**: Check `purchaseState === "PURCHASED"` (or "1")
4. ✅ For **Android Subscriptions**: `isActive` and `expirationDate` are NOT set - must use Google Play API on server
5. ✅ For **Refunds**: iOS purchases may linger (validate server-side), Android purchases disappear
6. 🔒 **Always implement server-side validation for production apps**

### API Reference

#### Core Methods

```typescript
// Check if in-app purchases are supported
await NativePurchases.isBillingSupported();

// Get single product information
await NativePurchases.getProduct({ productIdentifier: 'product_id' });

// Get multiple products
await NativePurchases.getProducts({ productIdentifiers: ['id1', 'id2'] });

// Purchase a product
await NativePurchases.purchaseProduct({
  productIdentifier: 'product_id',
  quantity: 1
});

// Restore previous purchases
await NativePurchases.restorePurchases();

// Open subscription management page
await NativePurchases.manageSubscriptions();

// Get plugin version
await NativePurchases.getPluginVersion();
```

### Important Notes

- **Apple Requirement**: Always display product names and prices from StoreKit data, never hardcode them
- **Error Handling**: Implement proper error handling for network issues and user cancellations  
- **Server Verification**: Always verify purchases on your server for security
- **Testing**: Use the comprehensive testing guides for both iOS and Android platforms

## Backend Validation

### ✅ Full Receipt Data Access

**This plugin provides complete access to verified receipt data for server-side validation.** You get all the information needed to validate purchases with Apple and Google servers.

**For iOS:**
- ✅ `transaction.receipt` - Complete base64-encoded StoreKit receipt (for Apple's receipt verification API)
- ✅ `transaction.jwsRepresentation` - StoreKit 2 JSON Web Signature (for App Store Server API v2)

**For Android:**
- ✅ `transaction.purchaseToken` - Google Play purchase token (for Google Play Developer API)
- ✅ `transaction.orderId` - Google Play order identifier

These fields contain the **full verified receipt payload** that you can send directly to your backend for validation with Apple's and Google's servers.

#### Migrating from cordova-plugin-purchase?

If you're coming from cordova-plugin-purchase, here's the mapping:

| cordova-plugin-purchase | @capgo/native-purchases | Platform | Notes |
|-------------------------|-------------------------|----------|-------|
| `transaction.transactionReceipt` | `transaction.receipt` (base64) | iOS | Legacy StoreKit receipt format (same value as Cordova) |
| — | `transaction.jwsRepresentation` (JWS) | iOS | StoreKit 2 JWS format (iOS 15+, additional field with no Cordova equivalent; Apple's recommended modern format for new implementations) |
| `transaction.purchaseToken` | `transaction.purchaseToken` | Android | Same field name |

**This plugin already exposes everything you need for backend verification!** The `receipt` and `purchaseToken` fields contain the complete verified receipt data, and `jwsRepresentation` provides an additional StoreKit 2 representation when available.

**Note:** On iOS, `jwsRepresentation` is only available for StoreKit 2 transactions (iOS 15+) and is Apple's recommended modern format. For maximum compatibility, use `receipt` which works on all iOS versions; when available, you can also send `jwsRepresentation` to backends that support App Store Server API v2.

### Why Backend Validation?

It's crucial to validate receipts on your server to ensure the integrity of purchases. Client-side data can be manipulated, but server-side validation with Apple/Google servers ensures purchases are legitimate.

### Receipt Data Available for Backend Verification

The `Transaction` object returned by `purchaseProduct()`, `getPurchases()`, and `restorePurchases()` includes all data needed for server-side validation:

**iOS Receipt Data:**
- **`receipt`** - Base64-encoded StoreKit receipt (legacy format, works with Apple's receipt verification API)
- **`jwsRepresentation`** - JSON Web Signature for StoreKit 2 (recommended for new implementations, works with App Store Server API)
- **`transactionId`** - Unique transaction identifier

**Android Receipt Data:**
- **`purchaseToken`** - Google Play purchase token (required for server-side validation)
- **`orderId`** - Google Play order identifier
- **`transactionId`** - Alias for purchaseToken

**All platforms include:**
- `productIdentifier` - The product that was purchased
- `purchaseDate` - When the purchase occurred
- Additional metadata like `appAccountToken`, `quantity`, etc.

### Complete Backend Validation Example

#### Cloudflare Worker Setup
Create a new Cloudflare Worker and follow the instructions in folder (`validator`)[/validator/README.md]

#### Client-Side Implementation

Here's how to access the receipt data and send it to your backend for validation:

```typescript
import { Capacitor } from '@capacitor/core';
import { NativePurchases, PURCHASE_TYPE, Product, Transaction } from '@capgo/native-purchases';
import axios from 'axios'; // Make sure to install axios: npm install axios

class Store {
  // ... (previous code remains the same)

  // Purchase in-app product
  async purchaseProduct(productId: string) {
    try {
      const transaction = await NativePurchases.purchaseProduct({
        productIdentifier: productId,
        productType: PURCHASE_TYPE.INAPP
      });
      console.log('In-app purchase successful:', transaction);
      
      // Immediately grant access to the purchased content
      await this.grantAccess(productId);
      
      // Initiate server-side validation asynchronously
      this.validatePurchaseOnServer(transaction).catch(console.error);
      
      return transaction;
    } catch (error) {
      console.error('Purchase failed:', error);
      throw error;
    }
  }

  // Purchase subscription (requires planIdentifier)
  async purchaseSubscription(productId: string, planId: string) {
    try {
      const transaction = await NativePurchases.purchaseProduct({
        productIdentifier: productId,
        planIdentifier: planId,              // REQUIRED for subscriptions
        productType: PURCHASE_TYPE.SUBS      // REQUIRED for subscriptions
      });
      console.log('Subscription purchase successful:', transaction);
      
      // Immediately grant access to the subscription content
      await this.grantAccess(productId);
      
      // Initiate server-side validation asynchronously
      this.validatePurchaseOnServer(transaction).catch(console.error);
      
      return transaction;
    } catch (error) {
      console.error('Subscription purchase failed:', error);
      throw error;
    }
  }

  private async grantAccess(productId: string) {
    // Implement logic to grant immediate access to the purchased content
    console.log(`Granting access to ${productId}`);
    // Update local app state, unlock features, etc.
  }

  private async validatePurchaseOnServer(transaction: Transaction) {
    const serverUrl = 'https://your-server-url.com/validate-purchase';
    const platform = Capacitor.getPlatform();
    
    try {
      // Prepare receipt data based on platform
      const receiptData = platform === 'ios' 
        ? {
            // iOS: Send the full receipt (base64 encoded) or JWS representation
            receipt: transaction.receipt,                    // StoreKit receipt (base64)
            jwsRepresentation: transaction.jwsRepresentation, // StoreKit 2 JWS (optional, recommended for new apps)
            transactionId: transaction.transactionId,
            platform: 'ios'
          }
        : {
            // Android: Send the purchase token and order ID
            purchaseToken: transaction.purchaseToken,        // Required for Google Play validation
            orderId: transaction.orderId,                    // Google Play order ID
            transactionId: transaction.transactionId,
            platform: 'android'
          };

      const response = await axios.post(serverUrl, {
        ...receiptData,
        productId: transaction.productIdentifier,
        purchaseDate: transaction.purchaseDate,
        // Include user ID or other app-specific data
        userId: 'your-user-id'
      });

      console.log('Server validation response:', response.data);
      return response.data;
    } catch (error) {
      console.error('Error in server-side validation:', error);
      // Implement retry logic or notify the user if necessary
      throw error;
    }
  }
}

// Usage examples
const store = new Store();
await store.initialize();

try {
  // Purchase in-app product (one-time purchase)
  await store.purchaseProduct('premium_features');
  console.log('In-app purchase completed successfully');
  
  // Purchase subscription (requires planIdentifier)
  await store.purchaseSubscription('premium_monthly', 'monthly-plan');
  console.log('Subscription completed successfully');
} catch (error) {
  console.error('Purchase failed:', error);
}
```

Now, let's look at how the server-side (Node.js) code handles the validation:

```typescript
import express from 'express';
import axios from 'axios';

const app = express();
app.use(express.json());

const CLOUDFLARE_WORKER_URL = 'https://your-cloudflare-worker-url.workers.dev';

app.post('/validate-purchase', async (req, res) => {
  const { platform, receipt, jwsRepresentation, purchaseToken, productId, userId } = req.body;

  try {
    let validationResponse;

    if (platform === 'ios') {
      // iOS: Validate using receipt or JWS representation
      if (!receipt && !jwsRepresentation) {
        return res.status(400).json({ 
          success: false, 
          error: 'Missing receipt data: either receipt or jwsRepresentation is required for iOS' 
        });
      }
      
      // Option 1: Use legacy receipt validation (recommended for compatibility)
      if (receipt) {
        validationResponse = await axios.post(`${CLOUDFLARE_WORKER_URL}/apple`, {
          receipt: receipt,  // Base64-encoded receipt from transaction.receipt
          password: 'your-app-shared-secret' // App-Specific Shared Secret from App Store Connect (required for auto-renewable subscriptions)
        });
      }
      // Option 2: Use StoreKit 2 App Store Server API (recommended for new implementations)
      else if (jwsRepresentation) {
        // Validate JWS token with App Store Server API
        // Note: JWS verification requires decoding and validating the signature
        // Implementation depends on your backend setup - see Apple's documentation:
        // https://developer.apple.com/documentation/appstoreserverapi/jwstransaction
        validationResponse = await axios.post(`${CLOUDFLARE_WORKER_URL}/apple-jws`, {
          jws: jwsRepresentation
        });
      }
    } else if (platform === 'android') {
      // Android: Validate using purchase token with Google Play Developer API
      if (!purchaseToken) {
        return res.status(400).json({ 
          success: false, 
          error: 'Missing purchaseToken for Android validation' 
        });
      }
      
      validationResponse = await axios.post(`${CLOUDFLARE_WORKER_URL}/google`, {
        purchaseToken: purchaseToken,  // From transaction.purchaseToken
        productId: productId,
        packageName: 'com.yourapp.package'
      });
    } else {
      return res.status(400).json({ 
        success: false, 
        error: 'Invalid platform' 
      });
    }

    const validationResult = validationResponse.data;

    // Process the validation result
    if (validationResult.isValid) {
      // Update user status in the database
      await updateUserPurchase(userId, {
        productId,
        platform,
        transactionId: req.body.transactionId,
        validated: true,
        validatedAt: new Date(),
        receiptData: validationResult
      });
      
      console.log(`Purchase validated for user ${userId}, product ${productId}`);
      
      res.json({ 
        success: true, 
        validated: true,
        message: 'Purchase successfully validated'
      });
    } else {
      // Handle invalid purchase
      console.warn(`Invalid purchase detected for user ${userId}`);
      
      // Flag for investigation but don't block the user immediately
      await flagSuspiciousPurchase(userId, req.body);
      
      res.json({ 
        success: true,  // Don't block the user
        validated: false,
        message: 'Purchase validation pending review'
      });
    }

  } catch (error) {
    console.error('Error validating purchase:', error);
    
    // Log the error for investigation
    await logValidationError(userId, req.body, error);
    
    // Still respond with success to the app
    // This ensures the app doesn't block the user's access
    res.json({ 
      success: true,
      validated: 'pending',
      message: 'Validation will be retried'
    });
  }
});

// Helper function to update user purchase status
async function updateUserPurchase(userId: string, purchaseData: any) {
  // Implement your database logic here
  console.log('Updating purchase for user:', userId);
}

// Helper function to flag suspicious purchases
async function flagSuspiciousPurchase(userId: string, purchaseData: any) {
  // Implement your logic to flag and review suspicious purchases
  console.log('Flagging suspicious purchase:', userId);
}

// Helper function to log validation errors
async function logValidationError(userId: string, purchaseData: any, error: any) {
  // Implement your error logging logic
  console.log('Logging validation error:', userId, error);
}

// Start the server
app.listen(3000, () => console.log('Server running on port 3000'));
```

### Alternative: Direct Store API Validation

Instead of using a Cloudflare Worker, you can validate directly with Apple and Google:

**iOS - Apple Receipt Verification API:**
```typescript
// Production: https://buy.itunes.apple.com/verifyReceipt
// Sandbox: https://sandbox.itunes.apple.com/verifyReceipt

async function validateAppleReceipt(receiptData: string) {
  const response = await axios.post('https://buy.itunes.apple.com/verifyReceipt', {
    'receipt-data': receiptData,
    'password': 'your-shared-secret', // App-Specific Shared Secret from App Store Connect (required for auto-renewable subscriptions)
    'exclude-old-transactions': true
  });
  
  return response.data;
}
```

**Android - Google Play Developer API:**
```typescript
// Requires Google Play Developer API credentials
// See: https://developers.google.com/android-publisher/getting_started

import { google } from 'googleapis';

async function validateGooglePurchase(packageName: string, productId: string, purchaseToken: string) {
  const androidPublisher = google.androidpublisher('v3');
  
  const auth = new google.auth.GoogleAuth({
    keyFile: 'path/to/service-account-key.json',
    scopes: ['https://www.googleapis.com/auth/androidpublisher'],
  });
  
  const authClient = await auth.getClient();
  
  const response = await androidPublisher.purchases.products.get({
    auth: authClient,
    packageName: packageName,
    productId: productId,
    token: purchaseToken
  });
  
  return response.data;
}
```

Key points about this approach:

1. The app immediately grants access after a successful purchase, ensuring a smooth user experience.
2. The app initiates server-side validation asynchronously, not blocking the user's access.
3. The server handles the actual validation by calling the Cloudflare Worker.
4. The server always responds with success to the app, even if validation fails or encounters an error.
5. The server can update the user's status in the database, log results, and handle any discrepancies without affecting the user's immediate experience.

Comments on best practices:

```typescript
// After successful validation:
// await updateUserStatus(userId, 'paid');

// It's crucial to not block or revoke access immediately if validation fails
// Instead, flag suspicious transactions for review:
// if (!validationResult.isValid) {
//   await flagSuspiciousPurchase(userId, transactionId);
// }

// Implement a system to periodically re-check flagged purchases
// This could be a separate process that runs daily/weekly

// Consider implementing a grace period for new purchases
// This allows for potential delays in server communication or store processing
// const GRACE_PERIOD_DAYS = 3;
// if (daysSincePurchase < GRACE_PERIOD_DAYS) {
//   grantAccess = true;
// }

// For subscriptions, regularly check their status with the stores
// This ensures you catch any cancelled or expired subscriptions
// setInterval(checkSubscriptionStatuses, 24 * 60 * 60 * 1000); // Daily check

// Implement proper error handling and retry logic for network failures
// This is especially important for the server-to-Cloudflare communication

// Consider caching validation results to reduce load on your server and the stores
// const cachedValidation = await getCachedValidation(transactionId);
// if (cachedValidation) return cachedValidation;
```

This approach balances immediate user gratification with proper server-side validation, adhering to Apple and Google's guidelines while still maintaining the integrity of your purchase system.

## API

<docgen-index>

* [`restorePurchases()`](#restorepurchases)
* [`getAppTransaction()`](#getapptransaction)
* [`isEntitledToOldBusinessModel(...)`](#isentitledtooldbusinessmodel)
* [`purchaseProduct(...)`](#purchaseproduct)
* [`getProducts(...)`](#getproducts)
* [`getProduct(...)`](#getproduct)
* [`isBillingSupported()`](#isbillingsupported)
* [`getPluginVersion()`](#getpluginversion)
* [`getPurchases(...)`](#getpurchases)
* [`manageSubscriptions()`](#managesubscriptions)
* [`acknowledgePurchase(...)`](#acknowledgepurchase)
* [`addListener('transactionUpdated', ...)`](#addlistenertransactionupdated-)
* [`addListener('transactionVerificationFailed', ...)`](#addlistenertransactionverificationfailed-)
* [`removeAllListeners()`](#removealllisteners)
* [Interfaces](#interfaces)
* [Enums](#enums)

</docgen-index>

<docgen-api>
<!--Update the source file JSDoc comments and rerun docgen to update the docs below-->

### restorePurchases()

```typescript
restorePurchases() => Promise<void>
```

Restores a user's previous  and links their appUserIDs to any user's also using those .

--------------------


### getAppTransaction()

```typescript
getAppTransaction() => Promise<{ appTransaction: AppTransaction; }>
```

Gets the App <a href="#transaction">Transaction</a> information, which provides details about when the user
originally downloaded or purchased the app.

This is useful for implementing business model changes where you want to
grandfather users who originally downloaded an earlier version of the app.

**Use Case Example:**
If your app was originally free but you're adding a subscription, you can use
`originalAppVersion` to check if users downloaded before the subscription was added
and give them free access.

**Platform Notes:**
- **iOS**: Requires iOS 16.0+. Uses StoreKit 2's <a href="#apptransaction">`AppTransaction</a>.shared`.
- **Android**: Uses Google Play's install referrer data when available.

**Returns:** <code>Promise&lt;{ appTransaction: <a href="#apptransaction">AppTransaction</a>; }&gt;</code>

**Since:** 7.16.0

--------------------


### isEntitledToOldBusinessModel(...)

```typescript
isEntitledToOldBusinessModel(options: { targetVersion?: string; targetBuildNumber?: string; }) => Promise<{ isOlderVersion: boolean; originalAppVersion: string; }>
```

Compares the original app version from the App <a href="#transaction">Transaction</a> against a target version
to determine if the user is entitled to features from an earlier business model.

This is a utility method that performs the version comparison natively, which can be
more reliable than JavaScript-based comparison for semantic versioning.

**Use Case:**
Check if the user's original download version is older than a specific version
to determine if they should be grandfathered into free features.

**Platform Differences:**
- iOS: Uses build number (CFBundleVersion) from <a href="#apptransaction">AppTransaction</a>. Requires iOS 16+.
- Android: Uses version name from PackageInfo (current installed version, not original).

| Param         | Type                                                                 | Description              |
| ------------- | -------------------------------------------------------------------- | ------------------------ |
| **`options`** | <code>{ targetVersion?: string; targetBuildNumber?: string; }</code> | - The comparison options |

**Returns:** <code>Promise&lt;{ isOlderVersion: boolean; originalAppVersion: string; }&gt;</code>

**Since:** 7.16.0

--------------------


### purchaseProduct(...)

```typescript
purchaseProduct(options: { productIdentifier: string; planIdentifier?: string; productType?: PURCHASE_TYPE; quantity?: number; appAccountToken?: string; isConsumable?: boolean; autoAcknowledgePurchases?: boolean; }) => Promise<Transaction>
```

Started purchase process for the given product.

| Param         | Type                                                                                                                                                                                                                                    | Description               |
| ------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------- |
| **`options`** | <code>{ productIdentifier: string; planIdentifier?: string; productType?: <a href="#purchase_type">PURCHASE_TYPE</a>; quantity?: number; appAccountToken?: string; isConsumable?: boolean; autoAcknowledgePurchases?: boolean; }</code> | - The product to purchase |

**Returns:** <code>Promise&lt;<a href="#transaction">Transaction</a>&gt;</code>

--------------------


### getProducts(...)

```typescript
getProducts(options: { productIdentifiers: string[]; productType?: PURCHASE_TYPE; }) => Promise<{ products: Product[]; }>
```

Gets the product info associated with a list of product identifiers.

| Param         | Type                                                                                                     | Description                                                    |
| ------------- | -------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------- |
| **`options`** | <code>{ productIdentifiers: string[]; productType?: <a href="#purchase_type">PURCHASE_TYPE</a>; }</code> | - The product identifiers you wish to retrieve information for |

**Returns:** <code>Promise&lt;{ products: Product[]; }&gt;</code>

--------------------


### getProduct(...)

```typescript
getProduct(options: { productIdentifier: string; productType?: PURCHASE_TYPE; }) => Promise<{ product: Product; }>
```

Gets the product info for a single product identifier.

| Param         | Type                                                                                                  | Description                                                   |
| ------------- | ----------------------------------------------------------------------------------------------------- | ------------------------------------------------------------- |
| **`options`** | <code>{ productIdentifier: string; productType?: <a href="#purchase_type">PURCHASE_TYPE</a>; }</code> | - The product identifier you wish to retrieve information for |

**Returns:** <code>Promise&lt;{ product: <a href="#product">Product</a>; }&gt;</code>

--------------------


### isBillingSupported()

```typescript
isBillingSupported() => Promise<{ isBillingSupported: boolean; }>
```

Check if billing is supported for the current device.

**Returns:** <code>Promise&lt;{ isBillingSupported: boolean; }&gt;</code>

--------------------


### getPluginVersion()

```typescript
getPluginVersion() => Promise<{ version: string; }>
```

Get the native Capacitor plugin version

**Returns:** <code>Promise&lt;{ version: string; }&gt;</code>

--------------------


### getPurchases(...)

```typescript
getPurchases(options?: { productType?: PURCHASE_TYPE | undefined; appAccountToken?: string | undefined; } | undefined) => Promise<{ purchases: Transaction[]; }>
```

Gets all the user's purchases (both in-app purchases and subscriptions).
This method queries the platform's purchase history for the current user.

| Param         | Type                                                                                                 | Description                                   |
| ------------- | ---------------------------------------------------------------------------------------------------- | --------------------------------------------- |
| **`options`** | <code>{ productType?: <a href="#purchase_type">PURCHASE_TYPE</a>; appAccountToken?: string; }</code> | - Optional parameters for filtering purchases |

**Returns:** <code>Promise&lt;{ purchases: Transaction[]; }&gt;</code>

**Since:** 7.2.0

--------------------


### manageSubscriptions()

```typescript
manageSubscriptions() => Promise<void>
```

Opens the platform's native subscription management page.
This allows users to view, modify, or cancel their subscriptions.

- iOS: Opens the App Store subscription management page for the current app
- Android: Opens the Google Play subscription management page

**Since:** 7.10.0

--------------------


### acknowledgePurchase(...)

```typescript
acknowledgePurchase(options: { purchaseToken: string; }) => Promise<void>
```

Manually acknowledge/finish a purchase transaction.

This method is only needed when you set `autoAcknowledgePurchases: false` in purchaseProduct().

**Platform Behavior:**
- **Android**: Acknowledges the purchase with Google Play. Must be called within 3 days or the purchase will be refunded.
- **iOS**: Finishes the transaction with StoreKit 2. Unfinished transactions remain in the queue and may block future purchases.

**Acknowledgment Options:**

**1. Client-side (this method)**: Call from your app after validation
```typescript
await NativePurchases.acknowledgePurchase({
  purchaseToken: transaction.purchaseToken  // Android: purchaseToken, iOS: transactionId
});
```

**2. Server-side (Android only, recommended for security)**: Use Google Play Developer API v3
- Endpoint: `POST https://androidpublisher.googleapis.com/androidpublisher/v3/applications/{packageName}/purchases/products/{productId}/tokens/{token}:acknowledge`
- Requires OAuth 2.0 authentication with appropriate scopes
- See: https://developers.google.com/android-publisher/api-ref/rest/v3/purchases.products/acknowledge
- For subscriptions: Use `/purchases/subscriptions/{subscriptionId}/tokens/{token}:acknowledge` instead
- Note: iOS has no server-side finish API

**When to use manual acknowledgment:**
- Server-side validation: Verify the purchase with your backend before acknowledging
- Entitlement delivery: Ensure user receives content/features before acknowledging
- Multi-step workflows: Complete all steps before final acknowledgment
- Security: Prevent client-side manipulation by handling acknowledgment server-side (Android only)

| Param         | Type                                    | Description                   |
| ------------- | --------------------------------------- | ----------------------------- |
| **`options`** | <code>{ purchaseToken: string; }</code> | - The purchase to acknowledge |

**Since:** 7.14.0

--------------------


### addListener('transactionUpdated', ...)

```typescript
addListener(eventName: 'transactionUpdated', listenerFunc: (transaction: Transaction) => void) => Promise<PluginListenerHandle>
```

Listen for StoreKit transaction updates delivered by Apple's <a href="#transaction">Transaction</a>.updates.
Fires on app launch if there are unfinished transactions, and for any updates afterward.
iOS only.

| Param              | Type                                                                          |
| ------------------ | ----------------------------------------------------------------------------- |
| **`eventName`**    | <code>'transactionUpdated'</code>                                             |
| **`listenerFunc`** | <code>(transaction: <a href="#transaction">Transaction</a>) =&gt; void</code> |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt;</code>

--------------------


### addListener('transactionVerificationFailed', ...)

```typescript
addListener(eventName: 'transactionVerificationFailed', listenerFunc: (payload: TransactionVerificationFailedEvent) => void) => Promise<PluginListenerHandle>
```

Listen for StoreKit transaction verification failures delivered by Apple's <a href="#transaction">Transaction</a>.updates.
Fires when the verification result is unverified.
iOS only.

| Param              | Type                                                                                                                    |
| ------------------ | ----------------------------------------------------------------------------------------------------------------------- |
| **`eventName`**    | <code>'transactionVerificationFailed'</code>                                                                            |
| **`listenerFunc`** | <code>(payload: <a href="#transactionverificationfailedevent">TransactionVerificationFailedEvent</a>) =&gt; void</code> |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt;</code>

--------------------


### removeAllListeners()

```typescript
removeAllListeners() => Promise<void>
```

Remove all registered listeners

--------------------


### Interfaces


#### AppTransaction

Represents the App <a href="#transaction">Transaction</a> information from StoreKit 2.
This provides details about when the user originally downloaded or purchased the app,
which is useful for determining if users are entitled to features from earlier business models.

| Prop                       | Type                                                      | Description                                                                                                                                                                                                                                                                                                                                                   | Since  |
| -------------------------- | --------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------ |
| **`originalAppVersion`**   | <code>string</code>                                       | The app version that the user originally purchased or downloaded. Use this to determine if users who originally downloaded an earlier version should be entitled to features that were previously free or included. For iOS: This is the `CFBundleShortVersionString` (e.g., "1.0.0") For Android: This is the `versionName` from Google Play (e.g., "1.0.0") | 7.16.0 |
| **`originalPurchaseDate`** | <code>string</code>                                       | The date when the user originally purchased or downloaded the app. ISO 8601 format.                                                                                                                                                                                                                                                                           | 7.16.0 |
| **`bundleId`**             | <code>string</code>                                       | The bundle identifier of the app.                                                                                                                                                                                                                                                                                                                             | 7.16.0 |
| **`appVersion`**           | <code>string</code>                                       | The current app version installed on the device.                                                                                                                                                                                                                                                                                                              | 7.16.0 |
| **`environment`**          | <code>'Sandbox' \| 'Production' \| 'Xcode' \| null</code> | The server environment where the app was originally purchased.                                                                                                                                                                                                                                                                                                | 7.16.0 |
| **`jwsRepresentation`**    | <code>string</code>                                       | The JWS (JSON Web Signature) representation of the app transaction. Can be sent to your backend for server-side verification.                                                                                                                                                                                                                                 | 7.16.0 |


#### Transaction

| Prop                       | Type                                                                                                          | Description                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          | Default           | Since  |
| -------------------------- | ------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------- | ------ |
| **`transactionId`**        | <code>string</code>                                                                                           | Unique identifier for the transaction.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               |                   | 1.0.0  |
| **`receipt`**              | <code>string</code>                                                                                           | Receipt data for validation (base64 encoded StoreKit receipt). **This is the full verified receipt payload from Apple StoreKit.** Send this to your backend for server-side validation with Apple's receipt verification API. The receipt remains available even after refund - server validation is required to detect refunded transactions. **For backend validation:** - Use Apple's receipt verification API: https://buy.itunes.apple.com/verifyReceipt (production) - Or sandbox: https://sandbox.itunes.apple.com/verifyReceipt - This contains all transaction data needed for validation **Note:** Apple recommends migrating to App Store Server API v2 with `jwsRepresentation` for new implementations. The legacy receipt verification API continues to work but may be deprecated in the future.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      |                   | 1.0.0  |
| **`jwsRepresentation`**    | <code>string</code>                                                                                           | StoreKit 2 JSON Web Signature (JWS) payload describing the verified transaction. **This is the full verified receipt in JWS format (StoreKit 2).** Send this to your backend when using Apple's App Store Server API v2 instead of raw receipts. Only available when the transaction originated from StoreKit 2 APIs (e.g. <a href="#transaction">Transaction</a>.updates). **For backend validation:** - Use Apple's App Store Server API v2 to decode and verify the JWS - This is the modern alternative to the legacy receipt format - Contains signed transaction information from Apple                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        |                   | 7.13.2 |
| **`appAccountToken`**      | <code>string \| null</code>                                                                                   | An optional obfuscated identifier that uniquely associates the transaction with a user account in your app. PURPOSE: - Fraud detection: Helps platforms detect irregular activity (e.g., many devices purchasing on the same account) - User linking: Links purchases to in-game characters, avatars, or in-app profiles PLATFORM DIFFERENCES: - iOS: Must be a valid UUID format (e.g., "550e8400-e29b-41d4-a716-446655440000") Apple's StoreKit 2 requires UUID format for the appAccountToken parameter - Android: Can be any obfuscated string (max 64 chars), maps to Google Play's ObfuscatedAccountId Google recommends using encryption or one-way hash SECURITY REQUIREMENTS (especially for Android): - DO NOT store Personally Identifiable Information (PII) like emails in cleartext - Use encryption or a one-way hash to generate an obfuscated identifier - Maximum length: 64 characters (both platforms) - Storing PII in cleartext will result in purchases being blocked by Google Play IMPLEMENTATION EXAMPLE: ```typescript // For iOS: Generate a deterministic UUID from user ID import { v5 as uuidv5 } from 'uuid'; const NAMESPACE = '6ba7b810-9dad-11d1-80b4-00c04fd430c8'; // Your app's namespace UUID const appAccountToken = uuidv5(userId, NAMESPACE); // For Android: Can also use UUID or any hashed value // The same UUID approach works for both platforms ``` |                   |        |
| **`productIdentifier`**    | <code>string</code>                                                                                           | <a href="#product">Product</a> identifier associated with the transaction.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           |                   | 1.0.0  |
| **`purchaseDate`**         | <code>string</code>                                                                                           | Purchase date of the transaction in ISO 8601 format.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 |                   | 1.0.0  |
| **`isUpgraded`**           | <code>boolean</code>                                                                                          | Indicates whether this transaction is the result of a subscription upgrade. Useful for understanding when StoreKit generated the transaction because the customer moved from a lower tier to a higher tier plan.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     |                   | 7.13.2 |
| **`originalPurchaseDate`** | <code>string</code>                                                                                           | Original purchase date of the transaction in ISO 8601 format. For subscription renewals, this shows the date of the original subscription purchase, while purchaseDate shows the date of the current renewal.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        |                   | 1.0.0  |
| **`expirationDate`**       | <code>string</code>                                                                                           | Expiration date of the transaction in ISO 8601 format. Check this date to determine if a subscription is still valid. Compare with current date: if expirationDate &gt; now, subscription is active.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 |                   | 1.0.0  |
| **`isActive`**             | <code>boolean</code>                                                                                          | Whether the subscription is still active/valid. For iOS subscriptions, check if isActive === true to verify an active subscription. For expired or refunded iOS subscriptions, this will be false.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |                   | 1.0.0  |
| **`revocationDate`**       | <code>string</code>                                                                                           | Date the transaction was revoked/refunded, in ISO 8601 format. Present when Apple revokes access due to an issue (e.g., refund or developer issue).                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  |                   | 7.13.2 |
| **`revocationReason`**     | <code>'developerIssue' \| 'other' \| 'unknown'</code>                                                         | Reason why Apple revoked the transaction. Possible values: - `"developerIssue"`: Developer-initiated refund or issue - `"other"`: Apple-initiated (customer refund, billing problem, etc.) - `"unknown"`: StoreKit didn't report a specific reason                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |                   | 7.13.2 |
| **`willCancel`**           | <code>boolean \| null</code>                                                                                  | Whether the subscription will be cancelled at the end of the billing cycle. - `true`: User has cancelled but subscription remains active until expiration - `false`: Subscription will auto-renew - `null`: Status unknown or not available                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          | <code>null</code> | 1.0.0  |
| **`subscriptionState`**    | <code>'unknown' \| 'subscribed' \| 'expired' \| 'revoked' \| 'inGracePeriod' \| 'inBillingRetryPeriod'</code> | Current subscription state reported by StoreKit. Possible values: - `"subscribed"`: Auto-renewing and in good standing - `"expired"`: Lapsed with no access - `"revoked"`: Access removed due to refund or issue - `"inGracePeriod"`: Payment issue but still in grace access window - `"inBillingRetryPeriod"`: StoreKit retrying failed billing - `"unknown"`: StoreKit did not report a state                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     |                   | 7.13.2 |
| **`purchaseState`**        | <code>string</code>                                                                                           | Purchase state of the transaction (numeric string value). **Android Values:** - `"1"`: Purchase completed and valid (PURCHASED state) - `"0"`: Payment pending (PENDING state, e.g., cash payment processing) - Other numeric values: Various other states Always check `purchaseState === "1"` on Android to verify a valid purchase. Refunded purchases typically disappear from getPurchases() rather than showing a different state.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             |                   | 1.0.0  |
| **`orderId`**              | <code>string</code>                                                                                           | Order ID associated with the transaction. Use this for server-side verification on Android. This is the Google Play order ID.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        |                   | 1.0.0  |
| **`purchaseToken`**        | <code>string</code>                                                                                           | Purchase token associated with the transaction. **This is the full verified purchase token from Google Play.** Send this to your backend for server-side validation with Google Play Developer API. This is the Android equivalent of iOS's receipt field. **For backend validation:** - Use Google Play Developer API v3 to verify the purchase - API endpoint: androidpublisher.purchases.products.get() or purchases.subscriptions.get() - This token contains all data needed for validation with Google servers - Can also be used for subscription status checks and cancellation detection                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    |                   | 1.0.0  |
| **`isAcknowledged`**       | <code>boolean</code>                                                                                          | Whether the purchase has been acknowledged. Purchases must be acknowledged within 3 days or they will be refunded. By default, this plugin automatically acknowledges purchases unless you set `autoAcknowledgePurchases: false` in purchaseProduct().                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               |                   | 1.0.0  |
| **`quantity`**             | <code>number</code>                                                                                           | Quantity purchased.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  | <code>1</code>    | 1.0.0  |
| **`productType`**          | <code>string</code>                                                                                           | <a href="#product">Product</a> type. - `"inapp"`: One-time in-app purchase - `"subs"`: Subscription                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  |                   | 1.0.0  |
| **`ownershipType`**        | <code>'purchased' \| 'familyShared'</code>                                                                    | Indicates how the user obtained access to the product. - `"purchased"`: The user purchased the product directly - `"familyShared"`: The user has access through Family Sharing (another family member purchased it) This property is useful for: - Detecting family sharing usage for analytics - Implementing different features/limits for family-shared vs. directly purchased products - Understanding your user acquisition channels                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            |                   | 7.12.8 |
| **`environment`**          | <code>'Sandbox' \| 'Production' \| 'Xcode'</code>                                                             | Indicates the server environment where the transaction was processed. - `"Sandbox"`: <a href="#transaction">Transaction</a> belongs to testing in the sandbox environment - `"Production"`: <a href="#transaction">Transaction</a> belongs to a customer in the production environment - `"Xcode"`: <a href="#transaction">Transaction</a> from StoreKit Testing in Xcode This property is useful for: - Debugging and identifying test vs. production purchases - Analytics and reporting (filtering out sandbox transactions) - Server-side validation (knowing which Apple endpoint to use) - Preventing test purchases from affecting production metrics                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         |                   | 7.12.8 |
| **`transactionReason`**    | <code>'unknown' \| 'purchase' \| 'renewal'</code>                                                             | Reason StoreKit generated the transaction. - `"purchase"`: Initial purchase that user made manually - `"renewal"`: Automatically generated renewal for an auto-renewable subscription - `"unknown"`: StoreKit did not return a reason                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                |                   | 7.13.2 |
| **`isTrialPeriod`**        | <code>boolean</code>                                                                                          | Whether the transaction is in a trial period. - `true`: Currently in free trial period - `false`: Not in trial period                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                |                   | 1.0.0  |
| **`isInIntroPricePeriod`** | <code>boolean</code>                                                                                          | Whether the transaction is in an introductory price period. Introductory pricing is a discounted rate, different from a free trial. - `true`: Currently using introductory pricing - `false`: Not in intro period                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    |                   | 1.0.0  |
| **`isInGracePeriod`**      | <code>boolean</code>                                                                                          | Whether the transaction is in a grace period. Grace period allows users to fix payment issues while maintaining access. You typically want to continue providing access during this time. - `true`: Subscription payment failed but user still has access - `false`: Not in grace period                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             |                   | 1.0.0  |


#### Product

| Prop                              | Type                                                                    | Description                                                              |
| --------------------------------- | ----------------------------------------------------------------------- | ------------------------------------------------------------------------ |
| **`identifier`**                  | <code>string</code>                                                     | <a href="#product">Product</a> Id.                                       |
| **`description`**                 | <code>string</code>                                                     | Description of the product.                                              |
| **`title`**                       | <code>string</code>                                                     | Title of the product.                                                    |
| **`price`**                       | <code>number</code>                                                     | Price of the product in the local currency.                              |
| **`priceString`**                 | <code>string</code>                                                     | Formatted price of the item, including its currency sign, such as €3.99. |
| **`currencyCode`**                | <code>string</code>                                                     | Currency code for price and original price.                              |
| **`currencySymbol`**              | <code>string</code>                                                     | Currency symbol for price and original price.                            |
| **`isFamilyShareable`**           | <code>boolean</code>                                                    | Boolean indicating if the product is sharable with family                |
| **`subscriptionGroupIdentifier`** | <code>string</code>                                                     | Group identifier for the product.                                        |
| **`subscriptionPeriod`**          | <code><a href="#subscriptionperiod">SubscriptionPeriod</a></code>       | The <a href="#product">Product</a> subscription group identifier.        |
| **`introductoryPrice`**           | <code><a href="#skproductdiscount">SKProductDiscount</a> \| null</code> | The <a href="#product">Product</a> introductory Price.                   |
| **`discounts`**                   | <code>SKProductDiscount[]</code>                                        | The <a href="#product">Product</a> discounts list.                       |


#### SubscriptionPeriod

| Prop                | Type                | Description                             |
| ------------------- | ------------------- | --------------------------------------- |
| **`numberOfUnits`** | <code>number</code> | The Subscription Period number of unit. |
| **`unit`**          | <code>number</code> | The Subscription Period unit.           |


#### SKProductDiscount

| Prop                     | Type                                                              | Description                                                              |
| ------------------------ | ----------------------------------------------------------------- | ------------------------------------------------------------------------ |
| **`identifier`**         | <code>string</code>                                               | The <a href="#product">Product</a> discount identifier.                  |
| **`type`**               | <code>number</code>                                               | The <a href="#product">Product</a> discount type.                        |
| **`price`**              | <code>number</code>                                               | The <a href="#product">Product</a> discount price.                       |
| **`priceString`**        | <code>string</code>                                               | Formatted price of the item, including its currency sign, such as €3.99. |
| **`currencySymbol`**     | <code>string</code>                                               | The <a href="#product">Product</a> discount currency symbol.             |
| **`currencyCode`**       | <code>string</code>                                               | The <a href="#product">Product</a> discount currency code.               |
| **`paymentMode`**        | <code>number</code>                                               | The <a href="#product">Product</a> discount paymentMode.                 |
| **`numberOfPeriods`**    | <code>number</code>                                               | The <a href="#product">Product</a> discount number Of Periods.           |
| **`subscriptionPeriod`** | <code><a href="#subscriptionperiod">SubscriptionPeriod</a></code> | The <a href="#product">Product</a> discount subscription period.         |


#### PluginListenerHandle

| Prop         | Type                                      |
| ------------ | ----------------------------------------- |
| **`remove`** | <code>() =&gt; Promise&lt;void&gt;</code> |


#### TransactionVerificationFailedEvent

| Prop                | Type                | Description                                                 | Since  |
| ------------------- | ------------------- | ----------------------------------------------------------- | ------ |
| **`transactionId`** | <code>string</code> | Identifier of the transaction that failed verification.     | 7.13.2 |
| **`error`**         | <code>string</code> | Localized error message describing why verification failed. | 7.13.2 |


### Enums


#### PURCHASE_TYPE

| Members     | Value                | Description                        |
| ----------- | -------------------- | ---------------------------------- |
| **`INAPP`** | <code>'inapp'</code> | A type of SKU for in-app products. |
| **`SUBS`**  | <code>'subs'</code>  | A type of SKU for subscriptions.   |

</docgen-api>

## App Store Compliance

- **Terms of Service**: https://capgo.app/terms
- **Privacy Policy**: https://capgo.app/privacy
- **Paywall layout example**: [docs/PAYWALL_COMPLIANCE_TEMPLATE.md](./docs/PAYWALL_COMPLIANCE_TEMPLATE.md)
