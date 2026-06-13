# Consumer ProGuard rules for :core:ui
#
# This module ships only stateless, themed Jetpack Compose components. It holds no
# reflection, no serialization, and no JNI, so no consumer keep rules are required.
# Compose's own consumer rules (shipped with the Compose artifacts) already cover
# composable lambda/keep semantics. Intentionally left empty.
