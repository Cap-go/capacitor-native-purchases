import { WebPlugin } from '@capacitor/core';

import type { AppTransaction, NativePurchasesPlugin, Product, PURCHASE_TYPE, Transaction } from './definitions';

export class NativePurchasesWeb extends WebPlugin implements NativePurchasesPlugin {
  async restorePurchases(): Promise<void> {
    console.error('restorePurchases only mocked in web');
  }

  async getProducts(options: {
    productIdentifiers: string[];
    productType?: PURCHASE_TYPE;
  }): Promise<{ products: Product[] }> {
    console.error('getProducts only mocked in web ' + options);
    return { products: [] };
  }

  async getProduct(options: { productIdentifier: string; productType?: PURCHASE_TYPE }): Promise<{ product: Product }> {
    console.error('getProduct only mocked in web ' + options);
    return { product: {} as any };
  }

  async purchaseProduct(options: {
    productIdentifier: string;
    planIdentifier?: string;
    productType?: PURCHASE_TYPE;
    quantity?: number;
    billingPlanType?: 'monthly' | 'upFront';
    appAccountToken?: string;
    isConsumable?: boolean;
    autoAcknowledgePurchases?: boolean;
  }): Promise<Transaction> {
    console.error('purchaseProduct only mocked in web' + options);
    return { transactionId: 'transactionId' } as any;
  }

  async isBillingSupported(): Promise<{ isBillingSupported: boolean }> {
    console.error('isBillingSupported only mocked in web');
    return { isBillingSupported: false };
  }
  async getPluginVersion(): Promise<{ version: string }> {
    console.warn('Cannot get plugin version in web');
    return { version: 'default' };
  }
  async getPurchases(options?: {
    productType?: PURCHASE_TYPE;
    appAccountToken?: string;
    onlyCurrentEntitlements?: boolean;
  }): Promise<{ purchases: Transaction[] }> {
    console.error('getPurchases only mocked in web ' + options);
    return { purchases: [] };
  }
  async manageSubscriptions(): Promise<void> {
    console.error('manageSubscriptions only mocked in web');
  }

  async acknowledgePurchase(options: { purchaseToken: string }): Promise<void> {
    void options;
    console.error('acknowledgePurchase only mocked in web');
  }

  async consumePurchase(options: { purchaseToken: string }): Promise<void> {
    void options;
    throw new Error('consumePurchase is only available on Android');
  }

  async getStorefront(): Promise<{ countryCode: string; storefrontId?: string }> {
    console.error('getStorefront only mocked in web');
    return { countryCode: '' };
  }

  async getAppTransaction(): Promise<{ appTransaction: AppTransaction }> {
    console.error('getAppTransaction only mocked in web');
    return {
      appTransaction: {
        originalAppVersion: '1.0.0',
        originalPurchaseDate: new Date().toISOString(),
        bundleId: 'com.example.app',
        appVersion: '1.0.0',
        environment: null,
      },
    };
  }

  async isEntitledToOldBusinessModel(options: {
    targetVersion?: string;
    targetBuildNumber?: string;
  }): Promise<{ isOlderVersion: boolean; originalAppVersion: string }> {
    void options;
    console.error('isEntitledToOldBusinessModel only mocked in web');
    return {
      isOlderVersion: false,
      originalAppVersion: '1.0.0',
    };
  }
}
