# Consumer ProGuard/R8 rules for :peripheral:glyph
#
# This module talks to the Nothing GDK / Glyph Matrix SDK ENTIRELY VIA REFLECTION
# (Class.forName / Method.invoke), so there are no compile-time references to
# com.nothing.ketchum.* for R8 to keep. When the real GDK AAR is dropped into
# peripheral/glyph/libs/, add keep rules so reflection targets survive shrinking:
#
#   -keep class com.nothing.ketchum.** { *; }
#   -keep class com.nothing.glyph.** { *; }
#
# Until the AAR is present these rules would reference non-existent classes, so
# they are intentionally left commented out. No other consumer rules are required.
