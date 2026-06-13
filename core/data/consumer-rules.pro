# SPDX-License-Identifier: Apache-2.0
# Consumer ProGuard/R8 rules for :core:data.
#
# Room and Hilt generated code is kept by their own bundled consumer rules
# (room-runtime / hilt-android ship -keep rules transitively), and the entities
# in this module are referenced reflectively only by generated Room DAOs which
# are themselves kept. DataStore Preferences uses no reflection on our types.
#
# No additional keep rules are required from consumers of this module.
