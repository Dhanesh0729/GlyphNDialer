# Consumer ProGuard/R8 rules for :peripheral:transcription
#
# This module reflects on optional, may-be-absent runtimes (Whisper native lib,
# ML Kit STT, a cloud STT client) and on android.speech.* callback types. The
# reflection guards already fail soft when a class is missing, so no keep rules
# are strictly required for correctness. If you later bundle a Whisper TFLite
# interpreter or the whisper.cpp JNI bridge, add keeps for the JNI entry points,
# e.g.:
#
#   -keepclasseswithmembernames class com.glyphdialer.peripheral.transcription.engine.WhisperNative {
#       native <methods>;
#   }
#
# No rules needed at present.
