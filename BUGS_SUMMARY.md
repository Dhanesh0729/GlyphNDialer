# Bug Summary

This file tracks the current bug/risk picture for `feature_GlyphNDialer`.

## Fixed in the current branch

- Settings placeholders cleaned up: hard or fake entries such as Default SIM picker and fake Storage Export were removed; open-source licenses now route to an in-app screen.
- Glyph settings discoverability improved: Free users on Glyph-capable hardware now see a locked Basic/Pro Glyph section instead of the settings disappearing entirely.
- Contact profile crash risk reduced: contact lookup keys are URI-decoded in detail/edit ViewModels before querying ContactsContract.
- Contact edit/update made real: `ContactsRepositoryImpl.updateContact` now writes name and phone rows through ContactsContract batch operations.
- Default number made real: `setDefaultNumber` now marks the selected phone row primary/super-primary where the provider allows it.
- Contact delete made safer: the detail screen now asks for confirmation before deleting a contact.
- Incoming call notification improved: call notifications now include title/subtext/ticker/color and an explicit Nothing-style vibration pattern.
- Recorder tier improved: stock Android can use speakerphone-based two-way capture with an announcement; OEM/system call-audio and VoIP two-way tiers remain preferred where available.

## Known limitations / must-test areas

- Google Play Billing is modeled but not fully wired with BillingClient purchase/restore/acknowledge flow yet. `UserPreferences.appPlan` is currently only the cached entitlement target.
- Glyph hardware requires real Nothing GDK/Matrix AARs and a production API key; without them the Glyph controller remains fallback/no-op.
- Contact update uses a practical v1 strategy: it replaces StructuredName and Phone rows for the resolved raw contacts. Test carefully with merged contacts and multiple synced accounts.
- Contact delete/write requires runtime `WRITE_CONTACTS`; failure should surface as a snackbar, but device testing is required.
- Incoming call custom UI is constrained by Android notification rules. `CallStyle` remains the correct base for call notifications; the full custom Nothing-style UI should live in the full-screen in-call Activity.
- Haptics are present on dialpad keys and incoming notification vibration. More call-state haptics (answer/end/hold/record) still need explicit UX tuning.
- Gradle tests could not run on this machine because the wrapper download failed with a Java trust-store SSL error. Build and test on the Android laptop.

## Priority before Play Store

1. Run `./gradlew assembleDebug` and fix compiler errors.
2. Test contact open/edit/delete/update on real Google-synced contacts.
3. Test incoming call notification/full-screen behavior on Android 13/14/15 with notification and full-screen permissions.
4. Wire BillingClient and Play Console products: `glyph_dialer_basic_49`, `glyph_dialer_pro_99`.
5. Add production Glyph AAR/API key and test on Nothing hardware.
6. Create privacy policy, recording-law disclosure, billing/refund copy, and Play Data Safety answers.