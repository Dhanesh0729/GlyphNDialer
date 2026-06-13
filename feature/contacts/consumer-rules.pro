# Consumer ProGuard/R8 rules for :feature:contacts.
#
# This module ships no reflection-, serialization-, or JNI-backed types of its
# own: it is plain Compose UI + Hilt ViewModels whose keep rules come from the
# Hilt/Dagger plugin and the app-level configuration. The domain models it
# renders (Contact, PhoneNumber, Favorite, SpeedDialSlot, CallLogEntry) are owned
# by :core:domain, and Coil's image-loading classes carry their own rules.
# Therefore no additional consumer rules are required.
#
# (Intentionally left without rules — header comment satisfies CONVENTIONS.md §4.)
