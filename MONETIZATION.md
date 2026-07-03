# Monetization Plan

Glyph Dialer sells digital app features, so the Play Store build should use Google Play Billing for Basic and Pro unlocks.

## Plans

| Plan | Product ID | Price target | Unlocks |
|---|---|---:|---|
| Free | none | INR 0 | Minimal dialer, contacts, recents, favorites, speed dial, T9 search, notes, spam marking, reminders, ringtone selection, no Glyph customization |
| Basic | `glyph_dialer_basic_49` | INR 49 one-time | Incoming-call Glyph flash, contact Glyph patterns, brightness/speed/duration controls, ringtone/Glyph sync, effect library, incoming preview |
| Pro | `glyph_dialer_pro_99` | INR 99 one-time | Premium themes, custom Glyph pattern creator, preset import/export, contact-group Glyph, profiles, charging mode, advanced personalization |

The product IDs are defined in `core/domain/src/main/kotlin/com/glyphdialer/core/domain/model/Monetization.kt`.

## How users buy

1. User installs the Free app from Google Play.
2. User opens Settings > Plan.
3. User taps Basic or Pro once the BillingClient purchase UI is wired.
4. Android shows the official Google Play purchase sheet with the user's available payment methods.
5. After purchase succeeds, the app acknowledges the purchase and stores the restored entitlement as `UserPreferences.appPlan`.
6. The app unlocks Basic/Pro UI immediately and restores the same plan on reinstall by querying Play purchase ownership.

## How you receive money

1. Create or use a Google Play Console developer account.
2. Set up the Play Console payments/merchant profile with bank, tax, and identity details.
3. Create the in-app products below in Play Console.
4. Google collects the user payment through Play Billing.
5. Google deducts applicable service fees, refunds, taxes, and adjustments.
6. Google pays the remaining balance to the bank account configured in the payments profile according to Play's payout schedule and minimums for your region.

## Play Console product setup

Create these as one-time in-app products:

| Product | Type | Product ID | Base price |
|---|---|---|---:|
| Glyph Dialer Basic | In-app product | `glyph_dialer_basic_49` | INR 49 |
| Glyph Dialer Pro | In-app product | `glyph_dialer_pro_99` | INR 99 |

Keep the IDs stable. Changing product IDs later breaks restore logic unless you maintain a migration map.

## App-side BillingClient work still needed

1. Add Google Play Billing Library to the app module.
2. Query products with `QueryProductDetailsParams`.
3. Launch `BillingClient.launchBillingFlow(...)` from the Settings Plan section.
4. On successful purchase, call `acknowledgePurchase(...)` for one-time purchases.
5. Restore entitlements on app startup by querying purchases.
6. Update `UserPreferences.appPlan` only from verified/restored Play ownership, not from a debug toggle in release builds.
7. Handle pending purchases, cancellations, refunds, and account changes.

For v1, client-side Play ownership restore is acceptable. Add server-side purchase verification later if piracy/fraud becomes important.

## Why not Razorpay / Stripe / UPI inside the Play Store app?

Basic and Pro are digital features consumed inside the app. For Google Play distribution, those purchases should go through Google Play Billing. External gateways are useful for a website, physical goods, support/donations that do not unlock app functionality, or direct APK distribution outside Play, but not for in-app digital unlocks in the Play Store build.

## External payment gateway option outside Play

If you distribute a direct APK from your own website, then Razorpay is the easiest India-first gateway:

| Gateway | Best for | Notes |
|---|---|---|
| Google Play Billing | Play Store app | Required/recommended for digital feature unlocks distributed through Play |
| Razorpay | India/direct APK/website | UPI, cards, netbanking; requires your own backend and license/receipt restore system |
| Stripe | International website/direct APK | Strong global cards/subscriptions; India availability and compliance must be checked |
| Cashfree/PayU | India website/direct APK | Similar to Razorpay; still needs backend entitlement system |

Do not mix Play Store digital unlocks with an in-app external checkout. If you want both channels, ship separate product flavors: `play` uses Google Play Billing; `direct` uses your backend + external gateway.

## Subscription alternative

If you decide to charge recurring money instead of one-time unlocks, keep the same plan model and create subscription products instead:

| Plan | Suggested monthly product ID |
|---|---|
| Basic | `glyph_dialer_basic_monthly` |
| Pro | `glyph_dialer_pro_monthly` |

For this app, one-time pricing is probably easier to sell to the Nothing community: INR 49 and INR 99 are simple, low-friction unlocks and avoid churn/refund pressure.