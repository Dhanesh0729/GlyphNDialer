# Bug Summary

This file tracks the current bug/risk picture for `feature_GlyphNDialer`.

## Fixed in the current branch

- Settings placeholders cleaned up: hard or fake entries such as Default SIM picker and fake Storage Export were removed; open-source licenses now route to an in-app screen.
- Glyph settings discoverability improved: Free users on Glyph-capable hardware now see a locked Basic/Pro Glyph section instead of the settings disappearing entirely.
- Contact profile crash risk reduced: contact detail/edit navigation now passes lookup keys as encoded query arguments instead of path segments, and detail/edit ViewModels decode before querying ContactsContract.
- Contact lookup stability improved: the contacts repository now uses Android's refreshed lookup key when folding phone rows into a detail result.
- Contact edit/update made real: `ContactsRepositoryImpl.updateContact` now writes name and phone rows through ContactsContract batch operations.
- Default number made real: `setDefaultNumber` now marks the selected phone row primary/super-primary where the provider allows it.
- Contact delete made safer: the detail screen now asks for confirmation before deleting a contact.
- Contact video call option added: contact detail number rows now expose an in-app WebRTC video action, gated by live camera + signaling capability.
- Incoming call notification improved: call notifications now include title/subtext/ticker/color and a settings-controlled Nothing-style vibration pattern.
- Call feedback controls added: Settings now includes incoming vibration, hang-up vibration, soft dial vibration, and experimental triple back-tap answer/end toggles.
- Recorder tier improved: stock Android can use speakerphone-based two-way capture with an announcement; OEM/system call-audio and VoIP two-way tiers remain preferred where available.
- Blocked numbers empty state fixed: the empty text now wraps as a compact two-line label and stays inside phone width.

## Known limitations / must-test areas

- Google Play Billing is modeled but not fully wired with BillingClient purchase/restore/acknowledge flow yet. `UserPreferences.appPlan` is currently only the cached entitlement target.
- Glyph hardware requires real Nothing GDK/Matrix AARs and a production API key; without them the Glyph controller remains fallback/no-op.
- Contact update uses a practical v1 strategy: it replaces StructuredName and Phone rows for the resolved raw contacts. Test carefully with merged contacts and multiple synced accounts.
- Contact delete/write requires runtime `WRITE_CONTACTS`; failure should surface as a snackbar, but device testing is required.
- In-app video calls require a real WebRTC signaling backend. The shipped manifest values intentionally point to `signaling.invalid.glyphdialer.example`, so Settings shows video as setup-required until production values are supplied.
- Incoming call custom UI is constrained by Android notification rules. `CallStyle` remains the correct base for call notifications; the full custom Nothing-style UI should live in the full-screen in-call Activity.
- Triple back-tap answer/end is accelerometer-based because Android exposes no public OEM back-tap gesture API. It must be tuned and tested on real Nothing hardware before marketing it as reliable.
- Gradle tests could not run on this machine because the wrapper download failed with a Java trust-store SSL error. Build and test on the Android laptop.

## Priority before Play Store

1. Run `./gradlew assembleDebug` and fix compiler errors.
2. Test contact open/edit/delete/update on real Google-synced contacts.
3. Test incoming call notification/full-screen behavior on Android 13/14/15 with notification and full-screen permissions.
4. Wire BillingClient and Play Console products: `glyph_dialer_basic_49`, `glyph_dialer_pro_99`.
5. Add production Glyph AAR/API key and test on Nothing hardware.
6. Create privacy policy, recording-law disclosure, billing/refund copy, and Play Data Safety answers.
