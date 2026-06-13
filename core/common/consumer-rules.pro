# SPDX-License-Identifier: Apache-2.0
# Consumer ProGuard/R8 rules for :core:common.
#
# This module exposes only plain Kotlin types (AppResult, DispatcherProvider,
# extension functions) and a Hilt @Module. Hilt's own generated code is kept by
# the Hilt Gradle plugin's bundled consumer rules, so no additional keep rules
# are required here. Intentionally left empty.
