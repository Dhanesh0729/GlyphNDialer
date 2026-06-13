// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.peripheral.recording.internal

import java.io.OutputStream
import kotlin.math.abs
import kotlin.math.min

/** Shared helpers for 16-bit PCM processing (amplitude + WAV header). */
internal object PcmUtils {

    /** Maximum magnitude of a signed 16-bit sample. */
    private const val PCM16_MAX = 32_768f

    /**
     * Computes a normalized peak amplitude (0f..1f) over a 16-bit little-endian PCM
     * buffer [data] of [length] valid bytes starting at [offset]. Cheap peak detection —
     * good enough to drive the waveform/Glyph mirror without an FFT.
     */
    fun peakAmplitude16(data: ByteArray, length: Int, offset: Int = 0): Float {
        var peak = 0
        var i = offset
        val end = offset + length - 1
        while (i < end) {
            val lo = data[i].toInt() and 0xFF
            val hi = data[i + 1].toInt() // sign-extended
            val sample = (hi shl 8) or lo
            val mag = abs(sample)
            if (mag > peak) peak = mag
            i += 2
        }
        return (peak / PCM16_MAX).coerceIn(0f, 1f)
    }

    /**
     * Writes a canonical 44-byte RIFF/WAVE header for the given [format] and
     * [pcmDataBytes] payload size. Streaming writers that don't know the final size up
     * front can write a placeholder header (pcmDataBytes = 0) and patch the two size
     * fields afterward via [patchWavSizes].
     */
    fun writeWavHeader(out: OutputStream, format: AudioFormatSpec, pcmDataBytes: Int) {
        out.write(buildWavHeader(format, pcmDataBytes))
    }

    /** Builds the 44-byte WAV header as a byte array. */
    fun buildWavHeader(format: AudioFormatSpec, pcmDataBytes: Int): ByteArray {
        val header = ByteArray(WAV_HEADER_SIZE)
        val totalDataLen = pcmDataBytes + (WAV_HEADER_SIZE - 8)

        // RIFF chunk
        putAscii(header, 0, "RIFF")
        putIntLe(header, 4, totalDataLen)
        putAscii(header, 8, "WAVE")

        // fmt subchunk
        putAscii(header, 12, "fmt ")
        putIntLe(header, 16, 16) // PCM fmt chunk size
        putShortLe(header, 20, 1) // audio format = 1 (PCM)
        putShortLe(header, 22, format.channelCount)
        putIntLe(header, 24, format.sampleRateHz)
        putIntLe(header, 28, format.byteRate)
        putShortLe(header, 32, format.bytesPerFrame)
        putShortLe(header, 34, format.bitsPerSample)

        // data subchunk
        putAscii(header, 36, "data")
        putIntLe(header, 40, pcmDataBytes)
        return header
    }

    /**
     * Patches the RIFF chunk size (offset 4) and data chunk size (offset 40) of an
     * already-written 44-byte WAV header for streaming writers that didn't know the
     * payload length up front. Returns a 44-byte header to overwrite the file prefix.
     */
    fun patchedWavHeader(format: AudioFormatSpec, pcmDataBytes: Int): ByteArray =
        buildWavHeader(format, pcmDataBytes)

    const val WAV_HEADER_SIZE = 44

    private fun putAscii(dst: ByteArray, offset: Int, s: String) {
        val n = min(s.length, dst.size - offset)
        for (i in 0 until n) dst[offset + i] = s[i].code.toByte()
    }

    private fun putIntLe(dst: ByteArray, offset: Int, value: Int) {
        dst[offset] = (value and 0xFF).toByte()
        dst[offset + 1] = ((value shr 8) and 0xFF).toByte()
        dst[offset + 2] = ((value shr 16) and 0xFF).toByte()
        dst[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }

    private fun putShortLe(dst: ByteArray, offset: Int, value: Int) {
        dst[offset] = (value and 0xFF).toByte()
        dst[offset + 1] = ((value shr 8) and 0xFF).toByte()
    }
}
