# Changelog

## Unreleased

### Breaking (TypeScript typings)

`Product.subscriptionPeriod` is now optional. Android omits it on prepaid (non-recurring) offers, which have price only. The period is present for infinite recurring phases, or for finite recurring phases on installment offers. Consumers that read `product.subscriptionPeriod.unit` without a check now get a TypeScript error. Use optional chaining, for example `product.subscriptionPeriod?.unit`.

### Added

- Android-only `Product.subscriptionBillingCycles`, set from `InstallmentPlanDetails.getInstallmentPlanCommitmentPaymentsCount()` when Play exposes a positive commitment payment count on an installment offer.

All notable changes to this project are documented in the GitHub Releases page.

**[Click here to view all releases →](../../releases)**
