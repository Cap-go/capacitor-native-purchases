import type { VerifyResponse } from 'google-play-billing';

export async function validateGoogleReceipt(receipt: string, env: Env): Promise<VerifyResponse> {
  const { GOOGLE_SERVICE_ACCOUNT, GOOGLE_PACKAGE_NAME } = env;

  const url =
    'https://androidpublisher.googleapis.com/androidpublisher/v3/applications/' +
    GOOGLE_PACKAGE_NAME +
    '/purchases/products/' +
    receipt +
    '/tokens/' +
    receipt;

  const response = await fetch(url, {
    method: 'GET',
    headers: {
      Authorization: 'Bearer ' + (await getAccessToken(GOOGLE_SERVICE_ACCOUNT)),
    },
  });

  const data: VerifyResponse = await response.json();
  return data;
}

async function getAccessToken(serviceAccount: string): Promise<string> {
  const token = await fetch(
    'https://oauth2.googleapis.com/token?grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=' +
      serviceAccount,
    { method: 'POST' },
  );

  const { access_token } = await token.json<{ access_token: string }>();
  return access_token;
}
