# SPDX-License-Identifier: Apache-2.0
# Consumer ProGuard/R8 rules for :feature:dialpad.
#
# This module ships no reflection-based public API of its own: Hilt-generated
# components, ViewModels, and Compose are kept by the respective library consumer
# rules pulled in transitively (Hilt, AndroidX). Navigation route constants and the
# graph extension are plain Kotlin and need no keep rules.
#
# Intentionally empty — add rules here only if this module later exposes types that
# are resolved reflectively by a downstream consumer.
