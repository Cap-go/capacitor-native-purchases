# Example App for `@capgo/native-purchases`

This Vite project links directly to the local plugin source so you can exercise the native APIs while developing.

## Actions in this playground

- **Get plugin version** – Returns the native RevenueCat wrapper version.
- **Check billing support** – Determines whether billing is available on this device.
- **Fetch products** – Fetches metadata for product identifiers. Separate multiple IDs with commas.

## Getting started

```bash
npm install
npm start
```

Add native shells with `npx cap add ios` or `npx cap add android` from this folder to try behaviour on device or simulator.
