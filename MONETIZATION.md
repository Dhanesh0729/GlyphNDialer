# Monetization Plan

Glyph Dialer sells digital app features, so the Play Store build should use Google Play Billing for Basic and Pro unlocks.

## Plans

| Plan | Product ID | Price target | Unlocks |
|---|---|---:|---|
| Free | none | INR 0 | Minimal dialer, contacts, recents, favorites, speed dial, T9 search, notes, spam marking, reminders, ringtone selection, no Glyph customization |
| Basic | `glyph_dialer_basic_49` | INR 49 one-time | Incoming-call Glyph flash, contact Glyph patterns, brightness/speed/duration controls, ringtone/Glyph sync, effect library, incoming preview |
| Pro | `glyph_dialer_pro_99` | INR 99 one-time | Premium themes, custom Glyph pattern creator, preset import/export, contact-group Glyph, profiles, charging mode, advanced personalization |

The product IDs are defined in `core/domain/src/main/kotlin/com/glyphdialer/core/domain/model/Monetization.kt`.

## Recommended payment path

1. Create the products in Play Console as in-app products:
   - `glyph_dialer_basic_49`
   - `glyph_dialer_pro_99`
2. Use Google Play Billing Library 9.1.0 or newer in the app to query products, launch purchase flow, acknowledge purchases, and restore ownership.
3. Cache the restored entitlement into `UserPreferences.appPlan` through `SettingsRepository.update { it.copy(appPlan = purchasedPlan) }`.
4. Keep Free as the default. The Settings screen already hides the Glyph group unless the device supports Glyph and the cached plan includes Basic.
5. Add server-side purchase verification later if piracy/fraud becomes a real problem. For v1, client-side BillingClient restore + acknowledge is the simplest Play-compliant path.

## Why not Razorpay / Stripe / UPI inside the Play Store app?

Basic and Pro are digital features consumed inside the app. For Google Play distribution, those purchases should go through Google Play Billing. External gateways are useful for a website, physical goods, support/donations that do not unlock app functionality, or direct APK distribution outside Play, but not for in-app digital unlocks in the Play Store build.

## Subscription alternative

If you decide to charge recurring money instead of one-time unlocks, keep the same plan model and create subscription products instead:

| Plan | Suggested monthly product ID |
|---|---|
| Basic | `glyph_dialer_basic_monthly` |
| Pro | `glyph_dialer_pro_monthly` |

For this app, one-time pricing is probably easier to sell to the Nothing community: INR 49 and INR 99 are simple, low-friction unlocks and avoid churn/refund pressure.