# SPDX-License-Identifier: Apache-2.0
#
# Consumer ProGuard/R8 rules for :telecom.
#
# The Android framework instantiates our services (InCallService,
# ConnectionService, CallScreeningService) reflectively via the manifest, and AGP's
# default Android rules already keep classes referenced from the merged manifest, so
# no extra keep rules are strictly required here.
#
# Hilt generates and keeps its own components; Room is not used by this module.
#
# If future R8 strips a manifest-referenced service in an aggressive configuration,
# add explicit keeps in :app (which owns the final manifest + R8 config), e.g.:
#   -keep class com.glyphdialer.telecom.service.** { *; }
