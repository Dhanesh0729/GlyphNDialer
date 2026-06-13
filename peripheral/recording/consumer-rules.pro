# SPDX-License-Identifier: Apache-2.0
# Consumer ProGuard/R8 rules for :peripheral:recording.
#
# This module has no reflection-based public surface that R8 would strip: Hilt
# generates its own keep rules, and the AudioRecord/MediaRecorder/foreground-service
# APIs are reached by direct calls. The AES/GCM keystore wrapper uses the standard
# javax.crypto + AndroidKeyStore providers, which the platform keeps.
#
# No additional keep rules are required. Left intentionally (almost) empty.
