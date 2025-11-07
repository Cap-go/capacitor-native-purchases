import { Router } from 'itty-router';

import { validateAppleReceipt } from './apple';
import { validateGoogleReceipt } from './google';

export interface Env {
  APPLE_SECRET: string; // Your Apple shared secret key
  GOOGLE_SERVICE_ACCOUNT: string; // Your Google service account JSON key
  GOOGLE_PACKAGE_NAME: string; // Your Google Play package name
}

const router = Router();

router.post('/apple', async (request: Request, env: Env) => {
  const { receipt } = await request.json<{ receipt: string }>();

  try {
    const verifyResponse = await validateAppleReceipt(receipt, env);
    return new Response(JSON.stringify(verifyResponse), {
      headers: { 'Content-Type': 'application/json' },
    });
  } catch (error) {
    console.error('Error validating Apple receipt:', error);
    return new Response('Error validating Apple receipt', { status: 500 });
  }
});

router.post('/google', async (request: Request, env: Env) => {
  const { receipt } = await request.json<{ receipt: string }>();

  try {
    const verifyResponse = await validateGoogleReceipt(receipt, env);
    return new Response(JSON.stringify(verifyResponse), {
      headers: { 'Content-Type': 'application/json' },
    });
  } catch (error) {
    console.error('Error validating Google receipt:', error);
    return new Response('Error validating Google receipt', { status: 500 });
  }
});

router.all('*', () => new Response('Not Found', { status: 404 }));

export default {
  fetch: router.handle,
};
