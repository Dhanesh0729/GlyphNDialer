// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.peripheral.webrtc.di

import android.content.Context
import android.content.pm.PackageManager
import com.glyphdialer.core.domain.repository.WebRtcClient
import com.glyphdialer.peripheral.webrtc.IceConfig
import com.glyphdialer.peripheral.webrtc.WebRtcClientImpl
import com.glyphdialer.peripheral.webrtc.signaling.SignalingConfig
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.PeerConnectionFactory
import org.webrtc.audio.JavaAudioDeviceModule
import timber.log.Timber
import javax.inject.Singleton

/**
 * Hilt wiring for the in-app VoIP/video layer (BUILD_SPEC §11).
 *
 * Provides the process-wide WebRTC primitives ([EglBase], [PeerConnectionFactory]),
 * the Ktor signaling [HttpClient], and the [SignalingConfig]; binds [WebRtcClient]
 * to its implementation.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class WebRtcModule {

    @Binds
    @Singleton
    abstract fun bindWebRtcClient(impl: WebRtcClientImpl): WebRtcClient

    companion object {

        /** Shared OpenGL context for video encode/decode/render. One per process. */
        @Provides
        @Singleton
        fun provideEglBase(): EglBase = EglBase.create()

        /**
         * The single [PeerConnectionFactory] for the process. Must be initialized
         * exactly once (via [PeerConnectionFactory.initialize]) before construction;
         * we do that here, guarded so repeated graph creation in tests is safe.
         */
        @Provides
        @Singleton
        fun providePeerConnectionFactory(
            @ApplicationContext context: Context,
            eglBase: EglBase,
        ): PeerConnectionFactory {
            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(context)
                    .setEnableInternalTracer(false)
                    .createInitializationOptions(),
            )

            val encoderFactory = DefaultVideoEncoderFactory(
                eglBase.eglBaseContext,
                /* enableIntelVp8Encoder = */ true,
                /* enableH264HighProfile = */ true,
            )
            val decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)

            val audioDeviceModule = JavaAudioDeviceModule.builder(context)
                .setUseHardwareAcousticEchoCanceler(true)
                .setUseHardwareNoiseSuppressor(true)
                .createAudioDeviceModule()

            return PeerConnectionFactory.builder()
                .setVideoEncoderFactory(encoderFactory)
                .setVideoDecoderFactory(decoderFactory)
                .setAudioDeviceModule(audioDeviceModule)
                .createPeerConnectionFactory()
        }

        /**
         * Optional TURN credentials for media relay (BUILD_SPEC §11).
         *
         * TODO(turn): provision a TURN server and return real
         *  [IceConfig.TurnCredentials] here — PREFERABLY short-lived credentials
         *  fetched per-session from your backend rather than baked into the APK.
         *  Returning null means STUN-only: calls work on permissive networks but
         *  fail behind symmetric NATs (§9: we do not pretend relay exists).
         */
        @Provides
        @Singleton
        fun provideTurnCredentials(): IceConfig.TurnCredentials? = null

        /**
         * Signaling backend configuration.
         *
         * TODO(backend): replace this default with your real deployment. Prefer
         *  loading host/token from a secure source (remote config, BuildConfig,
         *  encrypted prefs) rather than hard-coding here. The default is an
         *  intentionally non-functional placeholder (§9 honesty).
         */
        @Provides
        @Singleton
        fun provideSignalingConfig(
            @ApplicationContext context: Context,
        ): SignalingConfig {
            val config = context.readSignalingConfig()
            if (config.isPlaceholder) {
                Timber.w(
                    "Using placeholder SignalingConfig (%s). In-app VoIP will NOT " +
                        "connect until a real backend is configured — see TODO(backend).",
                    config.host,
                )
            }
            return config
        }

        /** Ktor WebSocket client used by the signaling layer. */
        @Provides
        @Singleton
        fun provideSignalingHttpClient(): HttpClient = HttpClient(OkHttp) {
            install(WebSockets)
            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                        classDiscriminator = "type"
                        encodeDefaults = true
                    },
                )
            }
        }

        private fun Context.readSignalingConfig(): SignalingConfig {
            val meta = runCatching {
                packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA).metaData
            }.getOrNull()
            val defaults = SignalingConfig()
            return SignalingConfig(
                scheme = meta?.getString(META_SIGNALING_SCHEME)?.takeIf { it.isNotBlank() } ?: defaults.scheme,
                host = meta?.getString(META_SIGNALING_HOST)?.takeIf { it.isNotBlank() } ?: defaults.host,
                port = meta?.getString(META_SIGNALING_PORT)?.toIntOrNull() ?: defaults.port,
                path = meta?.getString(META_SIGNALING_PATH)?.takeIf { it.isNotBlank() } ?: defaults.path,
                authToken = meta?.getString(META_SIGNALING_AUTH_TOKEN)?.takeIf { it.isNotBlank() },
            )
        }

        private const val META_SIGNALING_SCHEME = "com.glyphdialer.webrtc.SIGNALING_SCHEME"
        private const val META_SIGNALING_HOST = "com.glyphdialer.webrtc.SIGNALING_HOST"
        private const val META_SIGNALING_PORT = "com.glyphdialer.webrtc.SIGNALING_PORT"
        private const val META_SIGNALING_PATH = "com.glyphdialer.webrtc.SIGNALING_PATH"
        private const val META_SIGNALING_AUTH_TOKEN = "com.glyphdialer.webrtc.SIGNALING_AUTH_TOKEN"
    }
}
