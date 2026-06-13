# SPDX-License-Identifier: Apache-2.0
# Consumer ProGuard rules for :app.
#
# :app is the final application module, not a library consumed by anything else, so
# it ships no consumer rules. The active shrinker rules for the release build live in
# app/proguard-rules.pro (referenced from build.gradle.kts buildTypes.release).
