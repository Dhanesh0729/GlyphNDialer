// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.peripheral.recording.storage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.glyphdialer.core.common.AppResult
import com.glyphdialer.core.common.appResultOf
import com.glyphdialer.core.domain.model.RecordingTier
import com.glyphdialer.peripheral.recording.internal.AudioFormatSpec
import com.glyphdialer.peripheral.recording.internal.PcmSink
import com.glyphdialer.peripheral.recording.internal.PcmUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import java.io.RandomAccessFile
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encrypted audio body store (§12). Audio is written to app-private storage encrypted
 * with AES-256/GCM under a key held in the Android Keystore (hardware-backed where
 * available). We implement a small documented wrapper here because `security-crypto`
 * (EncryptedFile) is not in the version catalog; the on-disk format is intentionally
 * simple and self-describing.
 *
 * On-disk layout per recording file (`<dir>/<id>.glaud`):
 * ```
 *   [1 byte ] format version (= 1)
 *   [1 byte ] IV length (= 12)
 *   [12 bytes] GCM IV (random per file)
 *   [N bytes ] AES/GCM ciphertext of: WAV(44-byte header + PCM frames)
 * ```
 * The plaintext payload is a complete WAV file, so once decrypted it is directly
 * playable / transcribable. We keep a tiny unencrypted ".meta" side-file with the PCM
 * format so the (streaming) WAV header can be finalized on stop without re-reading the
 * whole ciphertext.
 *
 * NOTE: GCM is an AEAD cipher; we accumulate ciphertext via [CipherOutputStream] and rely
 * on the auth tag emitted at `doFinal`. Because GCM is not seekable for in-place header
 * patching, the streaming sink buffers the PCM payload to a temp plaintext file, then
 * encrypts the finalized WAV in one pass on [RecordingSession.finalizeAndEncrypt]. Audio
 * recordings are short-lived and bounded, so a single finalize pass is acceptable and
 * keeps the crypto simple and correct.
 */
@Singleton
class EncryptedRecordingStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val recordingsDir: File by lazy {
        File(context.filesDir, RECORDINGS_DIR_NAME).apply { mkdirs() }
    }

    /** Absolute path that [getRecording]/playback uses to reference an encrypted body. */
    fun encryptedFileFor(id: String): File = File(recordingsDir, "$id$ENC_EXTENSION")

    /**
     * Opens a streaming [RecordingSession] for [id] with the given PCM [format] and
     * [tier]. The session implements [PcmSink]; tier engines push frames to it. Call
     * [RecordingSession.finalizeAndEncrypt] (via [RecordingSession.close]) on stop.
     */
    fun openSession(id: String, format: AudioFormatSpec, tier: RecordingTier): RecordingSession {
        recordingsDir.mkdirs()
        val tempPcm = File(recordingsDir, "$id$TEMP_EXTENSION")
        return RecordingSession(
            id = id,
            format = format,
            tier = tier,
            tempPcmFile = tempPcm,
            encryptedFile = encryptedFileFor(id),
            secretKey = getOrCreateKey(),
        )
    }

    /** Deletes the encrypted body (and any temp) for [id]. */
    fun delete(id: String): AppResult<Unit> = appResultOf {
        encryptedFileFor(id).takeIf { it.exists() }?.delete()
        File(recordingsDir, "$id$TEMP_EXTENSION").takeIf { it.exists() }?.delete()
        Unit
    }

    /**
     * Decrypts the body for [id] into [destination] (a plaintext WAV). Used by the player
     * and by export. Returns the destination path on success.
     */
    fun decryptTo(id: String, destination: File): AppResult<String> = appResultOf {
        val src = encryptedFileFor(id)
        require(src.exists()) { "No recording body for id=$id" }
        java.io.DataInputStream(src.inputStream().buffered()).use { input ->
            val version = input.read()
            require(version == FORMAT_VERSION) { "Unsupported recording format v$version" }
            val ivLen = input.read()
            require(ivLen == GCM_IV_BYTES) { "Bad IV length $ivLen" }
            val iv = ByteArray(ivLen)
            input.readFully(iv) // blocks until the full IV is read or throws EOFException

            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            }
            destination.parentFile?.mkdirs()
            destination.outputStream().use { out ->
                javax.crypto.CipherInputStream(input, cipher).use { cipherIn ->
                    cipherIn.copyTo(out)
                }
            }
        }
        destination.absolutePath
    }

    /** Total bytes consumed by all encrypted bodies (for settings/diagnostics). */
    fun totalBytesOnDisk(): Long =
        recordingsDir.listFiles { f -> f.name.endsWith(ENC_EXTENSION) }
            ?.sumOf { it.length() } ?: 0L

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let {
            return it.secretKey
        }
        Timber.d("Generating new AES-256 recording key in AndroidKeyStore")
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(AES_KEY_BITS)
            // No user-authentication requirement: recordings must be writable while the
            // device is locked during a call. Key is still hardware-backed where present.
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    /**
     * A single streaming recording session. Tier engines write PCM frames via [write];
     * the temp PCM payload is encrypted into a final WAV on [close].
     */
    inner class RecordingSession internal constructor(
        val id: String,
        val format: AudioFormatSpec,
        val tier: RecordingTier,
        private val tempPcmFile: File,
        private val encryptedFile: File,
        private val secretKey: SecretKey,
    ) : PcmSink {

        private val pcmOut = RandomAccessFile(tempPcmFile, "rw").apply { setLength(0) }
        private var pcmBytesWritten: Long = 0L
        private var finalized = false

        override suspend fun write(data: ByteArray, offset: Int, length: Int) {
            synchronized(this) {
                if (finalized) return
                pcmOut.write(data, offset, length)
                pcmBytesWritten += length
            }
        }

        /** Bytes of raw PCM captured so far. */
        val bytesWritten: Long get() = pcmBytesWritten

        /**
         * Finalizes the recording: builds a WAV (header + PCM) and encrypts it to
         * [encryptedFile] with a fresh GCM IV. Returns the encrypted file's path. Safe to
         * call once; subsequent calls are no-ops returning the same path.
         */
        @Synchronized
        fun finalizeAndEncrypt(): AppResult<String> = appResultOf {
            if (!finalized) {
                runCatching { pcmOut.fd.sync() }
                pcmOut.close()
                encryptPcmToWav()
                tempPcmFile.delete()
                finalized = true
            }
            encryptedFile.absolutePath
        }

        /** Convenience close = [finalizeAndEncrypt] discarding the result on failure. */
        fun close(): AppResult<String> = finalizeAndEncrypt()

        private fun encryptPcmToWav() {
            val iv = ByteArray(GCM_IV_BYTES).also { java.security.SecureRandom().nextBytes(it) }
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION).apply {
                init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
            }
            encryptedFile.parentFile?.mkdirs()
            encryptedFile.outputStream().use { fileOut ->
                // header: version + iv (plaintext, needed for decrypt)
                fileOut.write(FORMAT_VERSION)
                fileOut.write(GCM_IV_BYTES)
                fileOut.write(iv)
                CipherOutputStream(fileOut, cipher).use { cipherOut ->
                    // WAV header (now that PCM size is known) then the PCM payload.
                    cipherOut.write(PcmUtils.buildWavHeader(format, pcmBytesWritten.toInt()))
                    tempPcmFile.inputStream().use { it.copyTo(cipherOut) }
                }
            }
            Timber.d(
                "Encrypted recording %s: %d PCM bytes -> %d enc bytes (tier=%s)",
                id, pcmBytesWritten, encryptedFile.length(), tier,
            )
        }
    }

    private companion object {
        const val RECORDINGS_DIR_NAME = "recordings"
        const val ENC_EXTENSION = ".glaud" // glyph-dialer encrypted audio
        const val TEMP_EXTENSION = ".pcmtmp"

        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "glyph_dialer_recording_key_v1"
        const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        const val AES_KEY_BITS = 256
        const val GCM_IV_BYTES = 12
        const val GCM_TAG_BITS = 128
        const val FORMAT_VERSION = 1
    }
}
