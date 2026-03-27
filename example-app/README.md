# Example App for `@capgo/native-purchases`

This Vite project links directly to the local plugin source so you can exercise the native APIs while developing.

## Actions in this playground

- **Get plugin version** – Returns the native RevenueCat wrapper version.
- **Check billing support** – Determines whether billing is available on this device.
- **Fetch products** – Fetches metadata for product identifiers. Separate multiple IDs with commas.

## Getting started

```bash
bun install
bun run start
```

Add native shells with `bunx cap add ios` or `bunx cap add android` from this folder to try behaviour on device or simulator.

## Native testing with Maestro

The `maestro/` folder contains three example flows that exercise the plugin on device:

- `plugin-version.yaml` – runs the default action to pull the plugin version.
- `billing-support.yaml` – switches the action dropdown and asserts billing support is returned.
- `fetch-products.yaml` – enters a product id and asserts the response includes it.

### Prerequisites

1. Install the Maestro CLI (one-time):

   ```bash
   curl -Ls https://get.maestro.mobile.dev | bash
   export PATH="$HOME/.maestro/bin:$PATH"
   ```

2. Build the native shells and install the app on a simulator/device (Android/iOS) using Capacitor.
3. Ensure the device is signed in with the right test account (Google Play / App Store) and that `PRODUCT_IDENTIFIER` matches a product configured for that account.

### Running the flows

From this `example-app` directory:

```bash
export PRODUCT_IDENTIFIER=premium_upgrade # or your own product id
maestro test maestro/test-plan.yaml
# or use the package script (Maestro CLI must be installed)
bun run maestro:test
```

You can also run individual flows with `maestro test maestro/flows/<flow>.yaml`. The `config.yaml` and `test-plan.yaml` files set the app id to `app.capgo.native.purchases` and provide a default product identifier for quick smoke tests.
