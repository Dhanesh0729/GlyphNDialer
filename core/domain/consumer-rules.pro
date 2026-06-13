# SPDX-License-Identifier: Apache-2.0
# Consumer ProGuard/R8 rules for :core:domain.
#
# This module contains only pure-Kotlin domain contracts (entities, repository
# interfaces, use cases). It declares no reflection, no serialization, and no
# framework-bound types, so it needs no consumer keep rules of its own.
#
# Serialization / Room / Hilt keep rules belong to the modules that actually
# implement those concerns (:core:data, :app) and ship with their own consumer
# rules. Intentionally left without rules.
