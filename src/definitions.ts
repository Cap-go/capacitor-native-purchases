import type { PluginListenerHandle } from '@capacitor/core';

export enum ATTRIBUTION_NETWORK {
  APPLE_SEARCH_ADS = 0,
  ADJUST = 1,
  APPSFLYER = 2,
  BRANCH = 3,
  TENJIN = 4,
  FACEBOOK = 5,
}

export enum PURCHASE_TYPE {
  /**
   * A type of SKU for in-app products.
   */
  INAPP = 'inapp',

  /**
   * A type of SKU for subscriptions.
   */
  SUBS = 'subs',
}

/**
 * Enum for billing features.
 * Currently, these are only relevant for Google Play Android users:
 * https://developer.android.com/reference/com/android/billingclient/api/BillingClient.FeatureType
 */
export enum BILLING_FEATURE {
  /**
   * Purchase/query for subscriptions.
   */
  SUBSCRIPTIONS,

  /**
   * Subscriptions update/replace.
   */
  SUBSCRIPTIONS_UPDATE,

  /**
   * Purchase/query for in-app items on VR.
   */
  IN_APP_ITEMS_ON_VR,

  /**
   * Purchase/query for subscriptions on VR.
   */
  SUBSCRIPTIONS_ON_VR,

  /**
   * Launch a price change confirmation flow.
   */
  PRICE_CHANGE_CONFIRMATION,
}
export enum PRORATION_MODE {
  UNKNOWN_SUBSCRIPTION_UPGRADE_DOWNGRADE_POLICY = 0,

  /**
   * Replacement takes effect immediately, and the remaining time will be
   * prorated and credited to the user. This is the current default behavior.
   */
  IMMEDIATE_WITH_TIME_PRORATION = 1,

  /**
   * Replacement takes effect immediately, and the billing cycle remains the
   * same. The price for the remaining period will be charged. This option is
   * only available for subscription upgrade.
   */
  IMMEDIATE_AND_CHARGE_PRORATED_PRICE = 2,

  /**
   * Replacement takes effect immediately, and the new price will be charged on
   * next recurrence time. The billing cycle stays the same.
   */
  IMMEDIATE_WITHOUT_PRORATION = 3,

  /**
   * Replacement takes effect when the old plan expires, and the new price will
   * be charged at the same time.
   */
  DEFERRED = 4,
}

export enum PACKAGE_TYPE {
  /**
   * A package that was defined with a custom identifier.
   */
  UNKNOWN = 'UNKNOWN',

  /**
   * A package that was defined with a custom identifier.
   */
  CUSTOM = 'CUSTOM',

  /**
   * A package configured with the predefined lifetime identifier.
   */
  LIFETIME = 'LIFETIME',

  /**
   * A package configured with the predefined annual identifier.
   */
  ANNUAL = 'ANNUAL',

  /**
   * A package configured with the predefined six month identifier.
   */
  SIX_MONTH = 'SIX_MONTH',

  /**
   * A package configured with the predefined three month identifier.
   */
  THREE_MONTH = 'THREE_MONTH',

  /**
   * A package configured with the predefined two month identifier.
   */
  TWO_MONTH = 'TWO_MONTH',

  /**
   * A package configured with the predefined monthly identifier.
   */
  MONTHLY = 'MONTHLY',

  /**
   * A package configured with the predefined weekly identifier.
   */
  WEEKLY = 'WEEKLY',
}

export enum INTRO_ELIGIBILITY_STATUS {
  /**
   * doesn't have enough information to determine eligibility.
   */
  INTRO_ELIGIBILITY_STATUS_UNKNOWN = 0,
  /**
   * The user is not eligible for a free trial or intro pricing for this product.
   */
  INTRO_ELIGIBILITY_STATUS_INELIGIBLE,
  /**
   * The user is eligible for a free trial or intro pricing for this product.
   */
  INTRO_ELIGIBILITY_STATUS_ELIGIBLE,
}

export interface Transaction {
  /**
   * Unique identifier for the transaction.
   *
   * @since 1.0.0
   * @platform ios Numeric string (e.g., "2000001043762129")
   * @platform android Alphanumeric string (e.g., "GPA.1234-5678-9012-34567")
   */
  readonly transactionId: string;
  /**
   * Receipt data for validation (base64 encoded StoreKit receipt).
   *
   * **This is the full verified receipt payload from Apple StoreKit.**
   * Send this to your backend for server-side validation with Apple's receipt verification API.
   * The receipt remains available even after refund - server validation is required to detect refunded transactions.
   *
   * **For backend validation:**
   * - Use Apple's receipt verification API: https://buy.itunes.apple.com/verifyReceipt (production)
   * - Or sandbox: https://sandbox.itunes.apple.com/verifyReceipt
   * - This contains all transaction data needed for validation
   *
   * @since 1.0.0
   * @platform ios Always present
   * @platform android Not available (use purchaseToken instead)
   * @example
   * ```typescript
   * const transaction = await NativePurchases.purchaseProduct({ ... });
   * if (transaction.receipt) {
   *   // Send to your backend for validation
   *   await fetch('/api/validate-receipt', {
   *     method: 'POST',
   *     body: JSON.stringify({ receipt: transaction.receipt })
   *   });
   * }
   * ```
   */
  readonly receipt?: string;
  /**
   * StoreKit 2 JSON Web Signature (JWS) payload describing the verified transaction.
   *
   * **This is the full verified receipt in JWS format (StoreKit 2).**
   * Send this to your backend when using Apple's App Store Server API v2 instead of raw receipts.
   * Only available when the transaction originated from StoreKit 2 APIs (e.g. Transaction.updates).
   *
   * **For backend validation:**
   * - Use Apple's App Store Server API v2 to decode and verify the JWS
   * - This is the modern alternative to the legacy receipt format
   * - Contains signed transaction information from Apple
   *
   * @since 7.13.2
   * @platform ios Present for StoreKit 2 transactions (iOS 15+)
   * @platform android Not available
   * @example
   * ```typescript
   * const transaction = await NativePurchases.purchaseProduct({ ... });
   * if (transaction.jwsRepresentation) {
   *   // Send to your backend for validation with App Store Server API v2
   *   await fetch('/api/validate-jws', {
   *     method: 'POST',
   *     body: JSON.stringify({ jws: transaction.jwsRepresentation })
   *   });
   * }
   * ```
   */
  readonly jwsRepresentation?: string;
  /**
   * An optional obfuscated identifier that uniquely associates the transaction with a user account in your app.
   *
   * PURPOSE:
   * - Fraud detection: Helps platforms detect irregular activity (e.g., many devices purchasing on the same account)
   * - User linking: Links purchases to in-game characters, avatars, or in-app profiles
   *
   * PLATFORM DIFFERENCES:
   * - iOS: Must be a valid UUID format (e.g., "550e8400-e29b-41d4-a716-446655440000")
   *        Apple's StoreKit 2 requires UUID format for the appAccountToken parameter
   * - Android: Can be any obfuscated string (max 64 chars), maps to Google Play's ObfuscatedAccountId
   *           Google recommends using encryption or one-way hash
   *
   * SECURITY REQUIREMENTS (especially for Android):
   * - DO NOT store Personally Identifiable Information (PII) like emails in cleartext
   * - Use encryption or a one-way hash to generate an obfuscated identifier
   * - Maximum length: 64 characters (both platforms)
   * - Storing PII in cleartext will result in purchases being blocked by Google Play
   *
   * IMPLEMENTATION EXAMPLE:
   * ```typescript
   * // For iOS: Generate a deterministic UUID from user ID
   * import { v5 as uuidv5 } from 'uuid';
   * const NAMESPACE = '6ba7b810-9dad-11d1-80b4-00c04fd430c8'; // Your app's namespace UUID
   * const appAccountToken = uuidv5(userId, NAMESPACE);
   *
   * // For Android: Can also use UUID or any hashed value
   * // The same UUID approach works for both platforms
   * ```
   */
  readonly appAccountToken?: string | null;
  /**
   * Product identifier associated with the transaction.
   *
   * @since 1.0.0
   * @platform ios Always present
   * @platform android Always present
   */
  readonly productIdentifier: string;
  /**
   * Purchase date of the transaction in ISO 8601 format.
   *
   * @since 1.0.0
   * @example "2025-10-28T06:03:19Z"
   * @platform ios Always present
   * @platform android Always present
   */
  readonly purchaseDate: string;
  /**
   * Indicates whether this transaction is the result of a subscription upgrade.
   *
   * Useful for understanding when StoreKit generated the transaction because
   * the customer moved from a lower tier to a higher tier plan.
   *
   * @since 7.13.2
   * @platform ios Present for auto-renewable subscriptions (iOS 15+)
   * @platform android Not available
   */
  readonly isUpgraded?: boolean;
  /**
   * Original purchase date of the transaction in ISO 8601 format.
   *
   * For subscription renewals, this shows the date of the original subscription purchase,
   * while purchaseDate shows the date of the current renewal.
   *
   * @since 1.0.0
   * @platform ios Present for subscriptions only
   * @platform android Not available
   */
  readonly originalPurchaseDate?: string;
  /**
   * Expiration date of the transaction in ISO 8601 format.
   *
   * Check this date to determine if a subscription is still valid.
   * Compare with current date: if expirationDate > now, subscription is active.
   *
   * @since 1.0.0
   * @platform ios Present for subscriptions only
   * @platform android Not available (query Google Play Developer API instead)
   */
  readonly expirationDate?: string;
  /**
   * Whether the subscription is still active/valid.
   *
   * For iOS subscriptions, check if isActive === true to verify an active subscription.
   * For expired or refunded iOS subscriptions, this will be false.
   *
   * @since 1.0.0
   * @platform ios Present for subscriptions only (true if expiration date is in the future)
   * @platform android Not available (check purchaseState === "1" instead)
   */
  readonly isActive?: boolean;
  /**
   * Date the transaction was revoked/refunded, in ISO 8601 format.
   *
   * Present when Apple revokes access due to an issue (e.g., refund or developer issue).
   *
   * @since 7.13.2
   * @platform ios Present for revoked transactions (iOS 15+)
   * @platform android Not available
   */
  readonly revocationDate?: string;
  /**
   * Reason why Apple revoked the transaction.
   *
   * Possible values:
   * - `"developerIssue"`: Developer-initiated refund or issue
   * - `"other"`: Apple-initiated (customer refund, billing problem, etc.)
   * - `"unknown"`: StoreKit didn't report a specific reason
   *
   * @since 7.13.2
   * @platform ios Present for revoked transactions (iOS 15+)
   * @platform android Not available
   */
  readonly revocationReason?: 'developerIssue' | 'other' | 'unknown';
  /**
   * Whether the subscription will be cancelled at the end of the billing cycle.
   *
   * - `true`: User has cancelled but subscription remains active until expiration
   * - `false`: Subscription will auto-renew
   * - `null`: Status unknown or not available
   *
   * @since 1.0.0
   * @default null
   * @platform ios Present for subscriptions only (boolean or null)
   * @platform android Always null (use Google Play Developer API for cancellation status)
   */
  readonly willCancel: boolean | null;
  /**
   * Current subscription state reported by StoreKit.
   *
   * Possible values:
   * - `"subscribed"`: Auto-renewing and in good standing
   * - `"expired"`: Lapsed with no access
   * - `"revoked"`: Access removed due to refund or issue
   * - `"inGracePeriod"`: Payment issue but still in grace access window
   * - `"inBillingRetryPeriod"`: StoreKit retrying failed billing
   * - `"unknown"`: StoreKit did not report a state
   *
   * @since 7.13.2
   * @platform ios Present for auto-renewable subscriptions (iOS 15+)
   * @platform android Not available
   */
  readonly subscriptionState?:
    | 'subscribed'
    | 'expired'
    | 'revoked'
    | 'inGracePeriod'
    | 'inBillingRetryPeriod'
    | 'unknown';
  /**
   * Purchase state of the transaction (numeric string value).
   *
   * **Android Values:**
   * - `"1"`: Purchase completed and valid (PURCHASED state)
   * - `"0"`: Payment pending (PENDING state, e.g., cash payment processing)
   * - Other numeric values: Various other states
   *
   * Always check `purchaseState === "1"` on Android to verify a valid purchase.
   * Refunded purchases typically disappear from getPurchases() rather than showing a different state.
   *
   * @since 1.0.0
   * @platform ios Not available (use isActive for subscriptions or receipt validation for IAP)
   * @platform android Always present
   */
  readonly purchaseState?: string;
  /**
   * Order ID associated with the transaction.
   *
   * Use this for server-side verification on Android. This is the Google Play order ID.
   *
   * @since 1.0.0
   * @example "GPA.1234-5678-9012-34567"
   * @platform ios Not available
   * @platform android Always present
   */
  readonly orderId?: string;
  /**
   * Purchase token associated with the transaction.
   *
   * **This is the full verified purchase token from Google Play.**
   * Send this to your backend for server-side validation with Google Play Developer API.
   * This is the Android equivalent of iOS's receipt field.
   *
   * **For backend validation:**
   * - Use Google Play Developer API v3 to verify the purchase
   * - API endpoint: androidpublisher.purchases.products.get() or purchases.subscriptions.get()
   * - This token contains all data needed for validation with Google servers
   * - Can also be used for subscription status checks and cancellation detection
   *
   * @since 1.0.0
   * @platform ios Not available (use receipt instead)
   * @platform android Always present
   * @example
   * ```typescript
   * const transaction = await NativePurchases.purchaseProduct({ ... });
   * if (transaction.purchaseToken) {
   *   // Send to your backend for validation
   *   await fetch('/api/validate-purchase', {
   *     method: 'POST',
   *     body: JSON.stringify({ 
   *       purchaseToken: transaction.purchaseToken,
   *       productId: transaction.productIdentifier 
   *     })
   *   });
   * }
   * ```
   */
  readonly purchaseToken?: string;
  /**
   * Whether the purchase has been acknowledged.
   *
   * Purchases must be acknowledged within 3 days or they will be refunded.
   * By default, this plugin automatically acknowledges purchases unless you set
   * `autoAcknowledgePurchases: false` in purchaseProduct().
   *
   * @since 1.0.0
   * @platform ios Not available
   * @platform android Always present (should be true after successful purchase or manual acknowledgment)
   */
  readonly isAcknowledged?: boolean;
  /**
   * Quantity purchased.
   *
   * @since 1.0.0
   * @default 1
   * @platform ios 1 or higher (as specified in purchaseProduct call)
   * @platform android Always 1 (Google Play doesn't support quantity > 1)
   */
  readonly quantity?: number;
  /**
   * Product type.
   *
   * - `"inapp"`: One-time in-app purchase
   * - `"subs"`: Subscription
   *
   * @since 1.0.0
   * @platform ios Always present
   * @platform android Always present
   */
  readonly productType?: string;
  /**
   * Indicates how the user obtained access to the product.
   *
   * - `"purchased"`: The user purchased the product directly
   * - `"familyShared"`: The user has access through Family Sharing (another family member purchased it)
   *
   * This property is useful for:
   * - Detecting family sharing usage for analytics
   * - Implementing different features/limits for family-shared vs. directly purchased products
   * - Understanding your user acquisition channels
   *
   * @since 7.12.8
   * @platform ios Always present (iOS 15.0+, StoreKit 2)
   * @platform android Not available
   */
  readonly ownershipType?: 'purchased' | 'familyShared';
  /**
   * Indicates the server environment where the transaction was processed.
   *
   * - `"Sandbox"`: Transaction belongs to testing in the sandbox environment
   * - `"Production"`: Transaction belongs to a customer in the production environment
   * - `"Xcode"`: Transaction from StoreKit Testing in Xcode
   *
   * This property is useful for:
   * - Debugging and identifying test vs. production purchases
   * - Analytics and reporting (filtering out sandbox transactions)
   * - Server-side validation (knowing which Apple endpoint to use)
   * - Preventing test purchases from affecting production metrics
   *
   * @since 7.12.8
   * @platform ios Present on iOS 16.0+ only (not available on iOS 15)
   * @platform android Not available
   */
  readonly environment?: 'Sandbox' | 'Production' | 'Xcode';
  /**
   * Reason StoreKit generated the transaction.
   *
   * - `"purchase"`: Initial purchase that user made manually
   * - `"renewal"`: Automatically generated renewal for an auto-renewable subscription
   * - `"unknown"`: StoreKit did not return a reason
   *
   * @since 7.13.2
   * @platform ios Present on iOS 17.0+ (StoreKit 2 transactions)
   * @platform android Not available
   */
  readonly transactionReason?: 'purchase' | 'renewal' | 'unknown';
  /**
   * Whether the transaction is in a trial period.
   *
   * - `true`: Currently in free trial period
   * - `false`: Not in trial period
   *
   * @since 1.0.0
   * @platform ios Present for subscriptions with trial offers
   * @platform android Present for subscriptions with trial offers
   */
  readonly isTrialPeriod?: boolean;
  /**
   * Whether the transaction is in an introductory price period.
   *
   * Introductory pricing is a discounted rate, different from a free trial.
   *
   * - `true`: Currently using introductory pricing
   * - `false`: Not in intro period
   *
   * @since 1.0.0
   * @platform ios Present for subscriptions with intro pricing
   * @platform android Present for subscriptions with intro pricing
   */
  readonly isInIntroPricePeriod?: boolean;
  /**
   * Whether the transaction is in a grace period.
   *
   * Grace period allows users to fix payment issues while maintaining access.
   * You typically want to continue providing access during this time.
   *
   * - `true`: Subscription payment failed but user still has access
   * - `false`: Not in grace period
   *
   * @since 1.0.0
   * @platform ios Present for subscriptions in grace period
   * @platform android Present for subscriptions in grace period
   */
  readonly isInGracePeriod?: boolean;
}

export interface TransactionVerificationFailedEvent {
  /**
   * Identifier of the transaction that failed verification.
   *
   * @since 7.13.2
   * @platform ios Present when StoreKit reports an unverified transaction
   * @platform android Not available
   */
  readonly transactionId: string;
  /**
   * Localized error message describing why verification failed.
   *
   * @since 7.13.2
   * @platform ios Always present
   * @platform android Not available
   */
  readonly error: string;
}

/**
 * Represents the App Transaction information from StoreKit 2.
 * This provides details about when the user originally downloaded or purchased the app,
 * which is useful for determining if users are entitled to features from earlier business models.
 *
 * @see https://developer.apple.com/documentation/storekit/supporting-business-model-changes-by-using-the-app-transaction
 * @since 7.16.0
 */
export interface AppTransaction {
  /**
   * The app version that the user originally purchased or downloaded.
   *
   * Use this to determine if users who originally downloaded an earlier version
   * should be entitled to features that were previously free or included.
   *
   * For iOS: This is the `CFBundleShortVersionString` (e.g., "1.0.0")
   * For Android: This is the `versionName` from Google Play (e.g., "1.0.0")
   *
   * @example "1.0.0"
   * @since 7.16.0
   * @platform ios Always present (iOS 16+)
   * @platform android Always present
   */
  readonly originalAppVersion: string;

  /**
   * The date when the user originally purchased or downloaded the app.
   * ISO 8601 format.
   *
   * @example "2023-06-15T10:30:00Z"
   * @since 7.16.0
   * @platform ios Always present (iOS 16+)
   * @platform android Always present
   */
  readonly originalPurchaseDate: string;

  /**
   * The bundle identifier of the app.
   *
   * @example "com.example.myapp"
   * @since 7.16.0
   * @platform ios Always present (iOS 16+)
   * @platform android Always present (package name)
   */
  readonly bundleId: string;

  /**
   * The current app version installed on the device.
   *
   * @example "2.0.0"
   * @since 7.16.0
   * @platform ios Always present
   * @platform android Always present
   */
  readonly appVersion: string;

  /**
   * The server environment where the app was originally purchased.
   *
   * @since 7.16.0
   * @platform ios Present (iOS 16+)
   * @platform android Not available (always null)
   */
  readonly environment?: 'Sandbox' | 'Production' | 'Xcode' | null;

  /**
   * The JWS (JSON Web Signature) representation of the app transaction.
   * Can be sent to your backend for server-side verification.
   *
   * @since 7.16.0
   * @platform ios Present (iOS 16+)
   * @platform android Not available
   */
  readonly jwsRepresentation?: string;
}

export interface SubscriptionPeriod {
  /**
   * The Subscription Period number of unit.
   */
  readonly numberOfUnits: number;
  /**
   * The Subscription Period unit.
   */
  readonly unit: number;
}
export interface SKProductDiscount {
  /**
   * The Product discount identifier.
   */
  readonly identifier: string;
  /**
   * The Product discount type.
   */
  readonly type: number;
  /**
   * The Product discount price.
   */
  readonly price: number;
  /**
   * Formatted price of the item, including its currency sign, such as €3.99.
   */
  readonly priceString: string;
  /**
   * The Product discount currency symbol.
   */
  readonly currencySymbol: string;
  /**
   * The Product discount currency code.
   */
  readonly currencyCode: string;
  /**
   * The Product discount paymentMode.
   */
  readonly paymentMode: number;
  /**
   * The Product discount number Of Periods.
   */
  readonly numberOfPeriods: number;
  /**
   * The Product discount subscription period.
   */
  readonly subscriptionPeriod: SubscriptionPeriod;
}
export interface Product {
  /**
   * Product Id.
   */
  readonly identifier: string;
  /**
   * Description of the product.
   */
  readonly description: string;
  /**
   * Title of the product.
   */
  readonly title: string;
  /**
   * Price of the product in the local currency.
   */
  readonly price: number;
  /**
   * Formatted price of the item, including its currency sign, such as €3.99.
   */
  readonly priceString: string;
  /**
   * Currency code for price and original price.
   */
  readonly currencyCode: string;
  /**
   * Currency symbol for price and original price.
   */
  readonly currencySymbol: string;
  /**
   * Boolean indicating if the product is sharable with family
   */
  readonly isFamilyShareable: boolean;
  /**
   * Group identifier for the product.
   */
  readonly subscriptionGroupIdentifier: string;
  /**
   * The Product subscription group identifier.
   */
  readonly subscriptionPeriod: SubscriptionPeriod;
  /**
   * The Product introductory Price.
   */
  readonly introductoryPrice: SKProductDiscount | null;
  /**
   * The Product discounts list.
   */
  readonly discounts: SKProductDiscount[];
}

export interface NativePurchasesPlugin {
  /**
   * Restores a user's previous  and links their appUserIDs to any user's also using those .
   */
  restorePurchases(): Promise<void>;

  /**
   * Gets the App Transaction information, which provides details about when the user
   * originally downloaded or purchased the app.
   *
   * This is useful for implementing business model changes where you want to
   * grandfather users who originally downloaded an earlier version of the app.
   *
   * **Use Case Example:**
   * If your app was originally free but you're adding a subscription, you can use
   * `originalAppVersion` to check if users downloaded before the subscription was added
   * and give them free access.
   *
   * **Platform Notes:**
   * - **iOS**: Requires iOS 16.0+. Uses StoreKit 2's `AppTransaction.shared`.
   * - **Android**: Uses Google Play's install referrer data when available.
   *
   * @returns {Promise<{ appTransaction: AppTransaction }>} The app transaction info
   * @throws An error if the app transaction cannot be retrieved (iOS 15 or earlier)
   * @since 7.16.0
   *
   * @example
   * ```typescript
   * const { appTransaction } = await NativePurchases.getAppTransaction();
   *
   * // Check if user downloaded before version 2.0.0 (when subscription was added)
   * if (compareVersions(appTransaction.originalAppVersion, '2.0.0') < 0) {
   *   // User gets free access - they downloaded before subscriptions
   *   grantFreeAccess();
   * }
   * ```
   *
   * @see https://developer.apple.com/documentation/storekit/supporting-business-model-changes-by-using-the-app-transaction
   */
  getAppTransaction(): Promise<{ appTransaction: AppTransaction }>;

  /**
   * Compares the original app version from the App Transaction against a target version
   * to determine if the user is entitled to features from an earlier business model.
   *
   * This is a utility method that performs the version comparison natively, which can be
   * more reliable than JavaScript-based comparison for semantic versioning.
   *
   * **Use Case:**
   * Check if the user's original download version is older than a specific version
   * to determine if they should be grandfathered into free features.
   *
   * **Platform Differences:**
   * - iOS: Uses build number (CFBundleVersion) from AppTransaction. Requires iOS 16+.
   * - Android: Uses version name from PackageInfo (current installed version, not original).
   *
   * @param options - The comparison options
   * @param options.targetVersion - The Android version name to compare against (e.g., "2.0.0"). Used on Android only.
   * @param options.targetBuildNumber - The iOS build number to compare against (e.g., "42"). Used on iOS only.
   * @returns {Promise<{ isOlderVersion: boolean; originalAppVersion: string }>}
   *          - `isOlderVersion`: true if the user's original version is older than target
   *          - `originalAppVersion`: The user's original app version/build number for reference
   * @throws An error if the app transaction cannot be retrieved
   * @since 7.16.0
   *
   * @example
   * ```typescript
   * // Check if user downloaded before version 2.0.0/build 42 (when subscription was added)
   * const result = await NativePurchases.isEntitledToOldBusinessModel({
   *   targetVersion: '2.0.0',
   *   targetBuildNumber: '42'
   * });
   *
   * if (result.isOlderVersion) {
   *   console.log(`User downloaded v${result.originalAppVersion}, granting free access`);
   *   grantFreeAccess();
   * }
   * ```
   */
  isEntitledToOldBusinessModel(options: {
    targetVersion?: string;
    targetBuildNumber?: string;
  }): Promise<{ isOlderVersion: boolean; originalAppVersion: string }>;

  /**
   * Started purchase process for the given product.
   *
   * @param options - The product to purchase
   * @param options.productIdentifier - The product identifier of the product you want to purchase.
   * @param options.productType - Only Android, the type of product, can be inapp or subs. Will use inapp by default.
   * @param options.planIdentifier - Only Android, the identifier of the base plan you want to purchase from Google Play Console. REQUIRED for Android subscriptions, ignored on iOS.
   * @param options.quantity - Only iOS, the number of items you wish to purchase. Will use 1 by default.
   * @param options.appAccountToken - Optional identifier uniquely associated with the user's account in your app.
   *                                  PLATFORM REQUIREMENTS:
   *                                  - iOS: Must be a valid UUID format (StoreKit 2 requirement)
   *                                  - Android: Can be any obfuscated string (max 64 chars), maps to ObfuscatedAccountId
   *                                  SECURITY: DO NOT use PII like emails in cleartext - use UUID or hashed value.
   *                                  RECOMMENDED: Use UUID v5 with deterministic generation for cross-platform compatibility.
   * @param options.isConsumable - Only Android, when true the purchase token is consumed after granting entitlement (for consumable in-app items). Defaults to false.
   * @param options.autoAcknowledgePurchases - When false, the purchase/transaction will NOT be automatically acknowledged/finished. You must manually call acknowledgePurchase() or the purchase may be refunded. Defaults to true.
   *                                           - **Android**: Must acknowledge within 3 days or Google Play will refund
   *                                           - **iOS**: Unfinished transactions remain in the queue and may block future purchases
   */
  purchaseProduct(options: {
    productIdentifier: string;
    planIdentifier?: string;
    productType?: PURCHASE_TYPE;
    quantity?: number;
    appAccountToken?: string;
    isConsumable?: boolean;
    autoAcknowledgePurchases?: boolean;
  }): Promise<Transaction>;

  /**
   * Gets the product info associated with a list of product identifiers.
   *
   * @param options - The product identifiers you wish to retrieve information for
   * @param options.productIdentifiers - Array of product identifiers
   * @param options.productType - Only Android, the type of product, can be inapp or subs. Will use inapp by default.
   * @returns - The requested product info
   */
  getProducts(options: { productIdentifiers: string[]; productType?: PURCHASE_TYPE }): Promise<{ products: Product[] }>;

  /**
   * Gets the product info for a single product identifier.
   *
   * @param options - The product identifier you wish to retrieve information for
   * @param options.productIdentifier - The product identifier
   * @param options.productType - Only Android, the type of product, can be inapp or subs. Will use inapp by default.
   * @returns - The requested product info
   */
  getProduct(options: { productIdentifier: string; productType?: PURCHASE_TYPE }): Promise<{ product: Product }>;

  /**
   * Check if billing is supported for the current device.
   *
   *
   */
  isBillingSupported(): Promise<{ isBillingSupported: boolean }>;
  /**
   * Get the native Capacitor plugin version
   *
   * @returns {Promise<{ id: string }>} an Promise with version for this device
   * @throws An error if the something went wrong
   */
  getPluginVersion(): Promise<{ version: string }>;

  /**
   * Gets all the user's purchases (both in-app purchases and subscriptions).
   * This method queries the platform's purchase history for the current user.
   *
   * @param options - Optional parameters for filtering purchases
   * @param options.productType - Only Android, filter by product type (inapp or subs). If not specified, returns both types.
   * @param options.appAccountToken - Optional filter to restrict results to purchases that used the provided account token.
   *                                   Must be the same identifier used during purchase (UUID format for iOS, any obfuscated string for Android).
   *                                   iOS: UUID format required. Android: Maps to ObfuscatedAccountId.
   * @returns {Promise<{ purchases: Transaction[] }>} Promise that resolves with array of user's purchases
   * @throws An error if the purchase query fails
   * @since 7.2.0
   */
  getPurchases(options?: {
    productType?: PURCHASE_TYPE;
    appAccountToken?: string;
  }): Promise<{ purchases: Transaction[] }>;

  /**
   * Opens the platform's native subscription management page.
   * This allows users to view, modify, or cancel their subscriptions.
   *
   * - iOS: Opens the App Store subscription management page for the current app
   * - Android: Opens the Google Play subscription management page
   *
   * @returns {Promise<void>} Promise that resolves when the management page is opened
   * @throws An error if the subscription management page cannot be opened
   * @since 7.10.0
   */
  manageSubscriptions(): Promise<void>;

  /**
   * Manually acknowledge/finish a purchase transaction.
   *
   * This method is only needed when you set `autoAcknowledgePurchases: false` in purchaseProduct().
   *
   * **Platform Behavior:**
   * - **Android**: Acknowledges the purchase with Google Play. Must be called within 3 days or the purchase will be refunded.
   * - **iOS**: Finishes the transaction with StoreKit 2. Unfinished transactions remain in the queue and may block future purchases.
   *
   * **Acknowledgment Options:**
   *
   * **1. Client-side (this method)**: Call from your app after validation
   * ```typescript
   * await NativePurchases.acknowledgePurchase({
   *   purchaseToken: transaction.purchaseToken  // Android: purchaseToken, iOS: transactionId
   * });
   * ```
   *
   * **2. Server-side (Android only, recommended for security)**: Use Google Play Developer API v3
   * - Endpoint: `POST https://androidpublisher.googleapis.com/androidpublisher/v3/applications/{packageName}/purchases/products/{productId}/tokens/{token}:acknowledge`
   * - Requires OAuth 2.0 authentication with appropriate scopes
   * - See: https://developers.google.com/android-publisher/api-ref/rest/v3/purchases.products/acknowledge
   * - For subscriptions: Use `/purchases/subscriptions/{subscriptionId}/tokens/{token}:acknowledge` instead
   * - Note: iOS has no server-side finish API
   *
   * **When to use manual acknowledgment:**
   * - Server-side validation: Verify the purchase with your backend before acknowledging
   * - Entitlement delivery: Ensure user receives content/features before acknowledging
   * - Multi-step workflows: Complete all steps before final acknowledgment
   * - Security: Prevent client-side manipulation by handling acknowledgment server-side (Android only)
   *
   * @param options - The purchase to acknowledge
   * @param options.purchaseToken - The purchase token (Android) or transaction ID as string (iOS) from the Transaction object
   * @returns {Promise<void>} Promise that resolves when the purchase is acknowledged/finished
   * @throws An error if acknowledgment/finishing fails or transaction not found
   * @platform android Acknowledges the purchase with Google Play
   * @platform ios Finishes the transaction with StoreKit 2
   * @since 7.14.0
   *
   * @example
   * ```typescript
   * // Client-side acknowledgment
   * const transaction = await NativePurchases.purchaseProduct({
   *   productIdentifier: 'premium_feature',
   *   autoAcknowledgePurchases: false
   * });
   *
   * // Validate with your backend
   * const isValid = await fetch('/api/validate-purchase', {
   *   method: 'POST',
   *   body: JSON.stringify({ purchaseToken: transaction.purchaseToken })
   * });
   *
   * if (isValid) {
   *   // Option 1: Acknowledge from client
   *   await NativePurchases.acknowledgePurchase({
   *     purchaseToken: transaction.purchaseToken
   *   });
   *
   *   // Option 2: Or let your backend acknowledge via Google Play API
   *   // Your backend calls Google Play Developer API
   * }
   * ```
   */
  acknowledgePurchase(options: { purchaseToken: string }): Promise<void>;

  /**
   * Listen for StoreKit transaction updates delivered by Apple's Transaction.updates.
   * Fires on app launch if there are unfinished transactions, and for any updates afterward.
   * iOS only.
   */
  addListener(
    eventName: 'transactionUpdated',
    listenerFunc: (transaction: Transaction) => void,
  ): Promise<PluginListenerHandle>;
  /**
   * Listen for StoreKit transaction verification failures delivered by Apple's Transaction.updates.
   * Fires when the verification result is unverified.
   * iOS only.
   */
  addListener(
    eventName: 'transactionVerificationFailed',
    listenerFunc: (payload: TransactionVerificationFailedEvent) => void,
  ): Promise<PluginListenerHandle>;

  /** Remove all registered listeners */
  removeAllListeners(): Promise<void>;
}
