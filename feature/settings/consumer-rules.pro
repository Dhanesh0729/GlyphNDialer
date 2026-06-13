# SPDX-License-Identifier: Apache-2.0
# Consumer ProGuard/R8 rules for :feature:settings.
#
# This module contributes only Compose UI, a @HiltViewModel, and plain domain
# data passthrough. Hilt/Compose keep rules are provided centrally by :app
# (CONVENTIONS.md §22). Enum-based settings models live in :core:domain and are
# kept there. No additional consumer rules are required here.
