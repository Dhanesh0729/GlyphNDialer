# Consumer ProGuard/R8 rules for :feature:calllog.
#
# This module ships no reflection-, serialization-, or JNI-backed types of its
# own: it is plain Compose UI + a Hilt ViewModel whose keep rules come from the
# Hilt/Dagger plugin and the app-level configuration. Domain models it renders
# are owned by :core:domain. Therefore no additional consumer rules are required.
#
# (Intentionally left without rules — header comment satisfies CONVENTIONS.md §4.)
