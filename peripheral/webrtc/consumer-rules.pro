# SPDX-License-Identifier: Apache-2.0
# Consumer ProGuard/R8 rules for :peripheral:webrtc.
#
# org.webrtc native bridge: the WebRTC library calls back into a number of Java
# types from native code via JNI (PeerConnection.Observer, SdpObserver, the
# *.Builder classes, the org.webrtc.* model types). The stream-webrtc-android
# artifact ships its own transitive consumer rules that keep the org.webrtc.*
# surface, so we do NOT duplicate them here.
#
# Ktor + kotlinx.serialization: our @Serializable signaling models
# (com.glyphdialer.peripheral.webrtc.signaling.*) are kept by the
# kotlinx-serialization R8 rules (the generated *$$serializer companions are
# referenced from the generated serializer() functions, which R8 keeps). No
# extra reflection on our own types.
#
# Hilt generated code is kept by hilt-android's bundled consumer rules.
#
# No additional keep rules are required from consumers of this module.
