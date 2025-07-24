# native-purchases
 <a href="https://capgo.app/"><img src='https://raw.githubusercontent.com/Cap-go/capgo/main/assets/capgo_banner.png' alt='Capgo - Instant updates for capacitor'/></a>

<div align="center">
  <h2><a href="https://capgo.app/?ref=plugin"> ➡️ Get Instant updates for your App with Capgo</a></h2>
  <h2><a href="https://capgo.app/consulting/?ref=plugin"> Missing a feature? We’ll build the plugin for you 💪</a></h2>
</div>

## In-app Purchases Made Easy

This plugin allows you to implement in-app purchases and subscriptions in your Capacitor app using native APIs.

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

> 📖 **[Complete Android Testing Guide](./docs/Android_TESTING_GUIDE.md)** - Comprehensive guide covering Internal Testing, License Testing, and Internal App Sharing methods with step-by-step instructions, troubleshooting, and best practices.

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
import { NativePurchases } from '@capgo/native-purchases';
```

### Complete Example: Get Product Info and Purchase

Here's a complete example showing how to get product information and make a purchase:

```typescript
import { NativePurchases } from '@capgo/native-purchases';

class PurchaseManager {
  private productId = 'com.yourapp.premium.monthly';

  async initializeStore() {
    try {
      // 1. Check if billing is supported
      const { isBillingSupported } = await NativePurchases.isBillingSupported();
      if (!isBillingSupported) {
        throw new Error('Billing not supported on this device');
      }

      // 2. Get product information (REQUIRED by Apple - no hardcoded prices!)
      const product = await this.getProductInfo();
      
      // 3. Display product with dynamic info from store
      this.displayProduct(product);
      
    } catch (error) {
      console.error('Store initialization failed:', error);
    }
  }

  async getProductInfo() {
    try {
      const { product } = await NativePurchases.getProduct({
        productIdentifier: this.productId
      });
      
      console.log('Product loaded:', {
        id: product.identifier,
        title: product.title,           // Use this for display (required by Apple)
        price: product.priceString,     // Use this for display (required by Apple) 
        description: product.description
      });
      
      return product;
    } catch (error) {
      console.error('Failed to get product:', error);
      throw error;
    }
  }

  displayProduct(product: any) {
    // ✅ CORRECT: Use dynamic product info (required by Apple)
    document.getElementById('product-title')!.textContent = product.title;
    document.getElementById('product-price')!.textContent = product.priceString;
    document.getElementById('product-description')!.textContent = product.description;
    
    // ❌ WRONG: Never hardcode prices - Apple will reject your app
    // document.getElementById('product-price')!.textContent = '$9.99/month';
  }

  async purchaseProduct() {
    try {
      console.log('Starting purchase...');
      
      const result = await NativePurchases.purchaseProduct({
        productIdentifier: this.productId,
        quantity: 1
      });
      
      console.log('Purchase successful!', result.transactionId);
      
      // Handle successful purchase
      await this.handleSuccessfulPurchase(result.transactionId);
      
    } catch (error) {
      console.error('Purchase failed:', error);
      this.handlePurchaseError(error);
    }
  }

  async handleSuccessfulPurchase(transactionId: string) {
    // 1. Grant access to premium features
    localStorage.setItem('premium_active', 'true');
    
    // 2. Update UI
    document.getElementById('subscription-status')!.textContent = 'Premium Active';
    
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
}

// Usage in your app
const purchaseManager = new PurchaseManager();

// Initialize when app starts
purchaseManager.initializeStore();

// Attach to UI buttons
document.getElementById('buy-button')?.addEventListener('click', () => {
  purchaseManager.purchaseProduct();
});

document.getElementById('restore-button')?.addEventListener('click', () => {
  purchaseManager.restorePurchases();
});
```

### Quick Examples

#### Get Multiple Products

```typescript
// Get multiple products at once
const getProducts = async () => {
  try {
    const { products } = await NativePurchases.getProducts({
      productIdentifiers: [
        'com.yourapp.premium.monthly',
        'com.yourapp.premium.yearly',
        'com.yourapp.remove_ads'
      ]
    });
    
    products.forEach(product => {
      console.log(`${product.title}: ${product.priceString}`);
    });
    
    return products;
  } catch (error) {
    console.error('Error getting products:', error);
  }
};
```

#### Simple Purchase Flow

```typescript
// Simple one-function purchase
const buyPremium = async () => {
  try {
    // Check billing support
    const { isBillingSupported } = await NativePurchases.isBillingSupported();
    if (!isBillingSupported) {
      alert('Purchases not supported on this device');
      return;
    }

    // Get product (for price display)
    const { product } = await NativePurchases.getProduct({
      productIdentifier: 'com.yourapp.premium'
    });

    // Confirm with user (showing real price from store)
    const confirmed = confirm(`Purchase ${product.title} for ${product.priceString}?`);
    if (!confirmed) return;

    // Make purchase
    const result = await NativePurchases.purchaseProduct({
      productIdentifier: 'com.yourapp.premium',
      quantity: 1
    });

    alert('Purchase successful! Transaction ID: ' + result.transactionId);
    
  } catch (error) {
    alert('Purchase failed: ' + error.message);
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

// Get plugin version
await NativePurchases.getPluginVersion();
```

### Important Notes

- **Apple Requirement**: Always display product names and prices from StoreKit data, never hardcode them
- **Error Handling**: Implement proper error handling for network issues and user cancellations  
- **Server Verification**: Always verify purchases on your server for security
- **Testing**: Use the comprehensive testing guides for both iOS and Android platforms

## Backend Validation

It's crucial to validate receipts on your server to ensure the integrity of purchases. Here's an example of how to implement backend validation using a Cloudflare Worker:

Cloudflare Worker Setup
Create a new Cloudflare Worker and follow the instructions in folder (`validator`)[/validator/README.md]

Then in your app, modify the purchase function to validate the receipt on the server:

```typescript
import { Capacitor } from '@capacitor/core';
import { NativePurchases, PURCHASE_TYPE, Product, Transaction } from '@capgo/native-purchases';
import axios from 'axios'; // Make sure to install axios: npm install axios

class Store {
  // ... (previous code remains the same)

  async purchaseProduct(productId: string) {
    try {
      const transaction = await NativePurchases.purchaseProduct({
        productIdentifier: productId,
        productType: PURCHASE_TYPE.INAPP
      });
      console.log('Purchase successful:', transaction);
      
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

  private async grantAccess(productId: string) {
    // Implement logic to grant immediate access to the purchased content
    console.log(`Granting access to ${productId}`);
    // Update local app state, unlock features, etc.
  }

  private async validatePurchaseOnServer(transaction: Transaction) {
    const serverUrl = 'https://your-server-url.com/validate-purchase';
    try {
      const response = await axios.post(serverUrl, {
        transactionId: transaction.transactionId,
        platform: Capacitor.getPlatform(),
        // Include any other relevant information
      });

      console.log('Server validation response:', response.data);
      // The server will handle the actual validation with the Cloudflare Worker
    } catch (error) {
      console.error('Error in server-side validation:', error);
      // Implement retry logic or notify the user if necessary
    }
  }
}

// Usage remains the same
const store = new Store();
await store.initialize();

try {
  await store.purchaseProduct('premium_subscription');
  console.log('Purchase completed successfully');
} catch (error) {
  console.error('Purchase failed:', error);
}
```

Now, let's look at how the server-side (Node.js) code might handle the validation:

```typescript
import express from 'express';
import axios from 'axios';

const app = express();
app.use(express.json());

const CLOUDFLARE_WORKER_URL = 'https://your-cloudflare-worker-url.workers.dev';

app.post('/validate-purchase', async (req, res) => {
  const { transactionId, platform } = req.body;

  try {
    const endpoint = platform === 'ios' ? '/apple' : '/google';
    const validationResponse = await axios.post(`${CLOUDFLARE_WORKER_URL}${endpoint}`, {
      receipt: transactionId
    });

    const validationResult = validationResponse.data;

    // Process the validation result
    if (validationResult.isValid) {
      // Update user status in the database
      // await updateUserStatus(userId, 'paid');
      
      // Log the successful validation
      console.log(`Purchase validated for transaction ${transactionId}`);
      
      // You might want to store the validation result for future reference
      // await storeValidationResult(userId, transactionId, validationResult);
    } else {
      // Handle invalid purchase
      console.warn(`Invalid purchase detected for transaction ${transactionId}`);
      // You might want to flag this for further investigation
      // await flagSuspiciousPurchase(userId, transactionId);
    }

    // Always respond with a success to the app
    // This ensures the app doesn't block the user's access
    res.json({ success: true });
  } catch (error) {
    console.error('Error validating purchase:', error);
    // Still respond with success to the app
    res.json({ success: true });
    // You might want to log this error or retry the validation later
    // await logValidationError(userId, transactionId, error);
  }
});

// Start the server
app.listen(3000, () => console.log('Server running on port 3000'));
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
* [`purchaseProduct(...)`](#purchaseproduct)
* [`getProducts(...)`](#getproducts)
* [`getProduct(...)`](#getproduct)
* [`isBillingSupported()`](#isbillingsupported)
* [`getPluginVersion()`](#getpluginversion)
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


### purchaseProduct(...)

```typescript
purchaseProduct(options: { productIdentifier: string; planIdentifier?: string; productType?: PURCHASE_TYPE; quantity?: number; }) => Promise<Transaction>
```

Started purchase process for the given product.

| Param         | Type                                                                                                                                              | Description               |
| ------------- | ------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------- |
| **`options`** | <code>{ productIdentifier: string; planIdentifier?: string; productType?: <a href="#purchase_type">PURCHASE_TYPE</a>; quantity?: number; }</code> | - The product to purchase |

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


### Interfaces


#### Transaction

| Prop                | Type                | Description                                  |
| ------------------- | ------------------- | -------------------------------------------- |
| **`transactionId`** | <code>string</code> | RevenueCat Id associated to the transaction. |


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
| **`subscriptionPeriod`**          | <code><a href="#subscriptionperiod">SubscriptionPeriod</a></code>       | The <a href="#product">Product</a> subcription group identifier.         |
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


### Enums


#### PURCHASE_TYPE

| Members     | Value                | Description                        |
| ----------- | -------------------- | ---------------------------------- |
| **`INAPP`** | <code>"inapp"</code> | A type of SKU for in-app products. |
| **`SUBS`**  | <code>"subs"</code>  | A type of SKU for subscriptions.   |

</docgen-api>
