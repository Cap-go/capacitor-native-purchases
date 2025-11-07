import { registerPlugin } from '@capacitor/core';

import type { NativePurchasesPlugin } from './definitions';

const NativePurchases = registerPlugin<NativePurchasesPlugin>('NativePurchases', {
  web: () => import('./web').then((m) => new m.NativePurchasesWeb()),
});

export * from './definitions';
export { NativePurchases };
